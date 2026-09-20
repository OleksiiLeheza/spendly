package com.spendly.transactionservice.repository;

import com.spendly.common.money.Money;
import com.spendly.transactionservice.domain.FeedCursor;
import com.spendly.transactionservice.domain.FeedPage;
import com.spendly.transactionservice.domain.FeedQuery;
import com.spendly.transactionservice.domain.TransactionRow;
import com.spendly.transactionservice.domain.TransactionType;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionFeedRepository {

    private static final String SELECT = """
            SELECT t.id, t.account_id, t.transfer_account_id, t.type, t.amount_value, t.amount_currency,
                   t.booked_on, t.description, t.merchant, t.tags, t.category_id,
                   EXISTS (SELECT 1 FROM transaction_splits s WHERE s.transaction_id = t.id) AS split,
                   t.reverses_transaction_id, t.reversed_by_transaction_id, t.created_at
            FROM transactions t
            WHERE t.owner_id = :ownerId AND t.deleted_at IS NULL
            """;

    private final JdbcClient jdbc;

    public TransactionFeedRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public FeedPage feed(FeedQuery query) {
        StringBuilder sql = new StringBuilder(SELECT);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ownerId", query.ownerId());

        if (query.accountId() != null) {
            sql.append(" AND (t.account_id = :accountId OR t.transfer_account_id = :accountId)");
            params.put("accountId", query.accountId());
        }
        if (query.categoryId() != null) {
            sql.append(" AND (t.category_id = :categoryId OR EXISTS (SELECT 1 FROM transaction_splits s"
                    + " WHERE s.transaction_id = t.id AND s.category_id = :categoryId))");
            params.put("categoryId", query.categoryId());
        }
        if (query.type() != null) {
            sql.append(" AND t.type = :type");
            params.put("type", query.type().name());
        }
        if (query.from() != null) {
            sql.append(" AND t.booked_on >= :from");
            params.put("from", query.from());
        }
        if (query.to() != null) {
            sql.append(" AND t.booked_on <= :to");
            params.put("to", query.to());
        }
        if (query.search() != null && !query.search().isBlank()) {
            sql.append(" AND t.search_vector @@ websearch_to_tsquery('simple', :search)");
            params.put("search", query.search().strip());
        }
        if (query.after() != null) {
            sql.append(" AND (t.booked_on, t.id) < (:cursorDate, :cursorId)");
            params.put("cursorDate", query.after().bookedOn());
            params.put("cursorId", query.after().id());
        }
        sql.append(" ORDER BY t.booked_on DESC, t.id DESC LIMIT :limit");
        params.put("limit", query.limit() + 1);

        List<TransactionRow> rows = jdbc.sql(sql.toString())
                .params(params)
                .query(TransactionFeedRepository::mapRow)
                .list();

        if (rows.size() <= query.limit()) {
            return new FeedPage(rows, null);
        }
        List<TransactionRow> page = rows.subList(0, query.limit());
        TransactionRow last = page.getLast();
        return new FeedPage(page, new FeedCursor(last.bookedOn(), last.id()));
    }

    private static TransactionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("transfer_account_id", UUID.class),
                TransactionType.valueOf(rs.getString("type")),
                Money.of(rs.getBigDecimal("amount_value"), Currency.getInstance(rs.getString("amount_currency"))),
                rs.getObject("booked_on", java.time.LocalDate.class),
                rs.getString("description"),
                rs.getString("merchant"),
                tags(rs.getArray("tags")),
                rs.getObject("category_id", UUID.class),
                rs.getBoolean("split"),
                rs.getObject("reverses_transaction_id", UUID.class),
                rs.getObject("reversed_by_transaction_id", UUID.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
    }

    private static List<String> tags(@Nullable Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (Object tag : (Object[]) array.getArray()) {
            tags.add(String.valueOf(tag));
        }
        return List.copyOf(tags);
    }
}

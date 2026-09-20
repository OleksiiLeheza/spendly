package com.spendly.transactionservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendly.transactionservice.repository.TransactionPartitions;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TransactionServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionPartitions partitions;

    @Test
    void flywayAppliedTheSchemaTheEntitiesValidateAgainst() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).startsWith("1");
    }

    @Test
    void transactionsArePartitionedByMonthOnBookedOn() {
        String strategy = jdbc.queryForObject(
                "SELECT pg_get_partkeydef('transactions'::regclass)", String.class);
        assertThat(strategy).isEqualTo("RANGE (booked_on)");

        YearMonth thisMonth = YearMonth.now();
        List<String> partitions = jdbc.queryForList(
                "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid"
                        + " WHERE i.inhparent = 'transactions'::regclass",
                String.class);
        assertThat(partitions)
                .contains("transactions_default", "transactions_" + thisMonth.toString().replace('-', '_'))
                .contains("transactions_" + thisMonth.plusMonths(3).toString().replace('-', '_'));
    }

    @Test
    void theFeedIndexCoversEveryPartition() {
        Integer indexed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname LIKE 'transactions_%_owner_id_booked_on_id_idx'",
                Integer.class);
        Integer partitions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_inherits WHERE inhparent = 'transactions'::regclass", Integer.class);
        assertThat(indexed).isEqualTo(partitions);
    }

    @Test
    void partitionMaintenanceCreatesMissingMonthsIdempotently() {
        YearMonth farAhead = YearMonth.of(2031, 3);
        assertThat(partitions.existing()).doesNotContain("transactions_2031_03");

        assertThat(partitions.ensurePartition(farAhead)).isTrue();
        assertThat(partitions.ensurePartition(farAhead)).isFalse();
        assertThat(partitions.existing()).contains("transactions_2031_03");

        jdbc.update(
                "INSERT INTO transactions (id, owner_id, account_id, type, amount_value, amount_currency, booked_on,"
                        + " description, created_at, last_modified_at) VALUES (gen_random_uuid(), gen_random_uuid(),"
                        + " gen_random_uuid(), 'EXPENSE', 1.00, 'USD', '2031-03-15', 'future', now(), now())");
        Integer inPartition = jdbc.queryForObject("SELECT COUNT(*) FROM transactions_2031_03", Integer.class);
        assertThat(inPartition).isEqualTo(1);
    }

    @Test
    void monetaryColumnsCannotBeUpdatedEvenByRawSql() {
        assertThat(LocalDate.now()).isNotNull();
        jdbc.update("INSERT INTO categories (id, owner_id, name, kind, created_at, last_modified_at)"
                + " VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000aa', 'x', 'EXPENSE', now(), now())");
        jdbc.update(
                "INSERT INTO transactions (id, owner_id, account_id, type, amount_value, amount_currency, booked_on,"
                        + " description, created_at, last_modified_at) VALUES ('00000000-0000-0000-0000-000000000002',"
                        + " '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000bb', 'EXPENSE', 10.00, 'USD',"
                        + " '2026-09-19', 'coffee', now(), now())");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "UPDATE transactions SET amount_value = 99 WHERE id = '00000000-0000-0000-0000-000000000002'"))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM transactions WHERE id = '00000000-0000-0000-0000-000000000002'"))
                .hasMessageContaining("append-only");

        assertThat(jdbc.update(
                "UPDATE transactions SET description = 'espresso', category_id = '00000000-0000-0000-0000-000000000001'"
                        + " WHERE id = '00000000-0000-0000-0000-000000000002'"))
                .isEqualTo(1);
    }
}

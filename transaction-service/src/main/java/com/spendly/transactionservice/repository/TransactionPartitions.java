package com.spendly.transactionservice.repository;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionPartitions {

    static final int MONTHS_AHEAD = 6;

    private static final Logger log = LoggerFactory.getLogger(TransactionPartitions.class);

    private final JdbcClient jdbc;
    private final Clock clock;

    public TransactionPartitions(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ensureMonthsAhead();
    }

    @Scheduled(cron = "0 0 3 1 * *")
    public void monthly() {
        ensureMonthsAhead();
    }

    public void ensureMonthsAhead() {
        YearMonth month = YearMonth.now(clock);
        for (int ahead = 0; ahead <= MONTHS_AHEAD; ahead++) {
            ensurePartition(month.plusMonths(ahead));
        }
    }

    public boolean ensurePartition(YearMonth month) {
        if (exists(month)) {
            return false;
        }
        try {
            jdbc.sql("CREATE TABLE IF NOT EXISTS " + partitionName(month) + " PARTITION OF transactions"
                    + " FOR VALUES FROM ('" + month.atDay(1) + "') TO ('" + month.plusMonths(1).atDay(1) + "')")
                    .update();
            log.info("Created ledger partition {}", partitionName(month));
            return true;
        } catch (DataAccessException ex) {
            log.warn("Could not create ledger partition {}: {}", partitionName(month),
                    ex.getMostSpecificCause().getMessage());
            return false;
        }
    }

    public List<String> existing() {
        return jdbc.sql("SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid"
                + " WHERE i.inhparent = 'transactions'::regclass ORDER BY c.relname")
                .query(String.class)
                .list();
    }

    static String partitionName(YearMonth month) {
        return "transactions_%04d_%02d".formatted(month.getYear(), month.getMonthValue());
    }

    private boolean exists(YearMonth month) {
        return jdbc.sql("SELECT to_regclass(:name) IS NOT NULL")
                .param("name", partitionName(month))
                .query(Boolean.class)
                .single();
    }
}

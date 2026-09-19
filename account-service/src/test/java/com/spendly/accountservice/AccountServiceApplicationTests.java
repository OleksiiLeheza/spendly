package com.spendly.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliedTheSchemaTheEntitiesValidateAgainst() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("1");
            assertThat(rs.getBoolean("success")).isTrue();
        }
    }

    @Test
    void postingsAreUniquePerLedgerEntryAndAccount() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT conname FROM pg_constraint WHERE conrelid = 'postings'::regclass")) {

            List<String> constraints = new ArrayList<>();
            while (rs.next()) {
                constraints.add(rs.getString("conname"));
            }
            assertThat(constraints).contains("ux_postings_ledger_entry_account", "fk_postings_account");
        }
    }
}

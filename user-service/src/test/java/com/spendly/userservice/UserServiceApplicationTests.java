package com.spendly.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void flywayAppliedTheSchemaTheEntitiesValidateAgainst() throws Exception {
        // The context starting at all already proves ddl-auto=validate passed; this makes the
        // reason explicit by confirming the migration ran rather than the schema being guessed.
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
    void usersTableHasTheUniqueConstraintsProvisioningRelieson() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'users'")) {

            var indexes = new java.util.ArrayList<String>();
            while (rs.next()) {
                indexes.add(rs.getString("indexname"));
            }
            assertThat(indexes).contains("ux_users_keycloak_id", "ux_users_email");
        }
    }
}

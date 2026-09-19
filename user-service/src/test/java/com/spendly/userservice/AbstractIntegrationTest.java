package com.spendly.userservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests that need a real database (FI-26).
 *
 * <p>
 * Runs Flyway against a throwaway Postgres rather than an in-memory stand-in, so the migration and the
 * {@code ddl-auto=validate} check are exercised against the engine the application actually ships on.
 *
 * <p>
 * The container is static, so one instance is shared by every subclass for the whole test run instead of being started
 * per class.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
}

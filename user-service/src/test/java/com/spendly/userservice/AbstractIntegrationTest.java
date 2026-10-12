package com.spendly.userservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests that need a real database (FI-26).
 *
 * <p>
 * Runs Flyway against a throwaway Postgres rather than an in-memory stand-in, so the migration and the
 * {@code ddl-auto=validate} check are exercised against the engine the application actually ships on.
 *
 * <p>
 * The container is started once in a static initialiser and never stopped by a JUnit extension: Spring caches the
 * application context (and its connection pool) across test classes, so a container that {@code @Testcontainers}
 * restarted per class would leave every later class holding a pool bound to a port that no longer exists.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}

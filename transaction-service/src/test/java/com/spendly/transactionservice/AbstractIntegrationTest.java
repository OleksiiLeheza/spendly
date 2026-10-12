package com.spendly.transactionservice;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("postgres")
            .withCommand("postgres", "-c", "wal_level=logical");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka");

    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer("redis:8-alpine");

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }
}

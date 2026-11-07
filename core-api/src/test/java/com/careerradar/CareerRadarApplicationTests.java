package com.careerradar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a throwaway PostgreSQL instance, so Flyway
 * migrations and the datasource wiring are verified, not just the web slice.
 * Skipped automatically when Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CareerRadarApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywaySchemaHistoryExists() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isNotNull().isGreaterThanOrEqualTo(1);
    }
}

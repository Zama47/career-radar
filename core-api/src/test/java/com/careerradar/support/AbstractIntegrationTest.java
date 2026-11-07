package com.careerradar.support;

import com.careerradar.vacancy.upsert.VacancyUpsertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the real application against a shared throwaway PostgreSQL 16 with Flyway migrations
 * and {@code ddl-auto: validate}. Business tables are truncated before each test; dictionary
 * seeds stay in place.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    public static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected VacancyUpsertService vacancyUpsertService;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = PostgresTestContainer.get();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("internal.token", () -> INTERNAL_TOKEN);
    }

    @BeforeEach
    void truncateBusinessTables() {
        jdbcTemplate.execute("""
                truncate table vacancy_skills, vacancy_sources, vacancies, companies, source_runs
                restart identity cascade
                """);
    }
}

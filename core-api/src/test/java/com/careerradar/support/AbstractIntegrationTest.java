package com.careerradar.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.vacancy.upsert.VacancyUpsertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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

    /** BCrypt hash of {@link #APP_USER_PASSWORD}; test-only, never used outside tests. */
    public static final String APP_USER_EMAIL = "test@example.com";
    public static final String APP_USER_PASSWORD = "test-password";
    public static final String APP_USER_PASSWORD_HASH =
            "$2a$10$lxE89F.XZEUoBzWB7OF0A.qTo.18SMOv7fzbpL0AHXeuOV4te8dJW";

    private static final String JWT_SECRET = "test-secret-at-least-32-bytes-long!!";

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
        registry.add("security.jwt.secret", () -> JWT_SECRET);
        registry.add("app.user.email", () -> APP_USER_EMAIL);
        registry.add("app.user.password-hash", () -> APP_USER_PASSWORD_HASH);
        registry.add(
                "career-radar.resume-storage.dir",
                () -> java.nio.file.Path.of(
                                System.getProperty("java.io.tmpdir"), "career-radar-test-resumes")
                        .toString());
        registry.add("career-radar.llm.base-url", () -> "");
        registry.add("career-radar.llm.api-key", () -> "");
        registry.add("career-radar.llm.default-model", () -> "deepseek-v4.1-flash");
        registry.add("career-radar.llm.writing-model", () -> "deepseek-v4-pro");
    }

    @BeforeEach
    void truncateBusinessTables() {
        jdbcTemplate.execute("""
                truncate table llm_cache, llm_runs, resume_variants, resumes,
                    vacancy_skills, vacancy_sources, vacancies, companies, source_runs
                restart identity cascade
                """);
    }

    /**
     * Logs in via the real endpoint so tests exercise the same auth path as the web app.
     */
    protected String loginToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", APP_USER_EMAIL, "password", APP_USER_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("accessToken").asText();
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}

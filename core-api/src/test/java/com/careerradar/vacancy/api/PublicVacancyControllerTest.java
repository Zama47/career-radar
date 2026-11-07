package com.careerradar.vacancy.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.support.AbstractIntegrationTest;
import com.careerradar.vacancy.upsert.VacancyDraftRequest;
import com.careerradar.vacancy.upsert.VacancyUpsertRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PublicVacancyControllerTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void seedVacancies() throws Exception {
        token = loginToken();

        vacancyUpsertService.upsert(new VacancyUpsertRequest("HH", List.of(
                draft("hh-1", "Android Developer", "Romashka", true, "Android platform role", 90000, 130000),
                draft("hh-2", "Java Developer", "Acme", false, "Backend role in Java", 150000, 220000),
                draft("hh-3", "QA Engineer", "Ozon", true, "Testing role", null, null))));

        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'ANDROID'),
                       seniority = 'JUNIOR'
                 where title = 'Android Developer'
                """);
        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'JAVA_BACKEND'),
                       seniority = 'INTERN'
                 where title = 'Java Developer'
                """);
    }

    @Test
    void listsVacancies() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[0].url").isString());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paginates() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void filtersByProfessionIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("profession", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Android Developer"))
                .andExpect(jsonPath("$.items[0].profession").value("ANDROID"));
    }

    @Test
    void filtersBySeniority() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("seniority", "INTERN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));
    }

    @Test
    void filtersByRemoteType() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("remote", "REMOTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void filtersBySalaryMin() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("salaryMin", "200000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));
    }

    @Test
    void rejectsNegativeSalaryMin() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("salaryMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void searchesTitleDescriptionAndCompany() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)).param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));

        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)).param("q", "ozon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("QA Engineer"));
    }

    @Test
    void sortsBySalaryFrom() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("sort", "salaryFrom,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"))
                .andExpect(jsonPath("$.items[1].title").value("Android Developer"));
    }

    @Test
    void rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("sort", "title,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void clampsPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsInvalidRemoteFilter() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)).param("remote", "MOON"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsDetailCard() throws Exception {
        String listBody = mockMvc.perform(get("/api/v1/vacancies").with(bearer(token))
                        .param("q", "android"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String publicId = objectMapper.readTree(listBody).path("items").get(0).path("id").asText();

        mockMvc.perform(get("/api/v1/vacancies/{publicId}", publicId).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId))
                .andExpect(jsonPath("$.title").value("Android Developer"))
                .andExpect(jsonPath("$.company").value("Romashka"))
                .andExpect(jsonPath("$.profession").value("ANDROID"))
                .andExpect(jsonPath("$.source").value("HH"))
                .andExpect(jsonPath("$.sourceUrl").value("https://hh.ru/vacancy/hh-1"))
                .andExpect(jsonPath("$.description").value("Android platform role"))
                .andExpect(jsonPath("$.skills", hasSize(0)));
    }

    @Test
    void rejectsUnknownDetailId() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies/{publicId}", "00000000-0000-0000-0000-000000000000")
                        .with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void omitsMissingSalaryFields() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").with(bearer(token)).param("q", "qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("QA Engineer"))
                .andExpect(jsonPath("$.items[0].salaryFrom").doesNotExist())
                .andExpect(jsonPath("$.items[0].salaryTo").doesNotExist());
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Career Radar API"))
                .andExpect(jsonPath("$.paths['/internal/v1/vacancies:upsert']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }

    @Test
    void listsProfessionDictionary() throws Exception {
        mockMvc.perform(get("/api/v1/professions").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[?(@.code == 'ANDROID')].name").value("Android Developer"));
    }

    @Test
    void seedsDictionaries() {
        Integer professions = jdbcTemplate.queryForObject("select count(*) from professions", Integer.class);
        Integer skills = jdbcTemplate.queryForObject("select count(*) from skills", Integer.class);
        Integer sources = jdbcTemplate.queryForObject("select count(*) from sources", Integer.class);

        org.assertj.core.api.Assertions.assertThat(professions).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(skills).isEqualTo(25);
        // 7 rows from V3 (HH, OTHER and five pilot sites) + 36 new company sites in V4.
        org.assertj.core.api.Assertions.assertThat(sources).isEqualTo(43);
    }

    private static VacancyDraftRequest draft(
            String externalId,
            String title,
            String company,
            boolean remote,
            String description,
            Integer salaryFrom,
            Integer salaryTo) {
        return new VacancyDraftRequest(
                externalId,
                title,
                "https://hh.ru/vacancy/" + externalId,
                company,
                description,
                salaryFrom == null ? null : java.math.BigDecimal.valueOf(salaryFrom),
                salaryTo == null ? null : java.math.BigDecimal.valueOf(salaryTo),
                "RUB",
                remote,
                "Москва",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of(),
                null,
                null);
    }
}

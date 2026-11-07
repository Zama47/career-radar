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

    @BeforeEach
    void seedVacancies() {
        vacancyUpsertService.upsert(new VacancyUpsertRequest("HH", List.of(
                draft("hh-1", "Android Developer", "Romashka", true, "Android platform role"),
                draft("hh-2", "Java Developer", "Acme", false, "Backend role in Java"),
                draft("hh-3", "QA Engineer", "Ozon", true, "Testing role"))));

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
        mockMvc.perform(get("/api/v1/vacancies"))
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
    void paginates() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void filtersByProfessionIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("profession", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Android Developer"))
                .andExpect(jsonPath("$.items[0].profession").value("ANDROID"));
    }

    @Test
    void filtersBySeniority() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("seniority", "INTERN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));
    }

    @Test
    void filtersByRemoteType() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("remote", "REMOTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void searchesTitleAndDescription() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"));
    }

    @Test
    void clampsPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsInvalidRemoteFilter() throws Exception {
        mockMvc.perform(get("/api/v1/vacancies").param("remote", "MOON"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Career Radar API"))
                .andExpect(jsonPath("$.paths['/internal/v1/vacancies:upsert']").exists());
    }

    @Test
    void seedsDictionaries() {
        Integer professions = jdbcTemplate.queryForObject("select count(*) from professions", Integer.class);
        Integer skills = jdbcTemplate.queryForObject("select count(*) from skills", Integer.class);
        Integer sources = jdbcTemplate.queryForObject("select count(*) from sources", Integer.class);

        org.assertj.core.api.Assertions.assertThat(professions).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(skills).isEqualTo(25);
        org.assertj.core.api.Assertions.assertThat(sources).isEqualTo(7);
    }

    private static VacancyDraftRequest draft(
            String externalId, String title, String company, boolean remote, String description) {
        return new VacancyDraftRequest(
                externalId,
                title,
                "https://hh.ru/vacancy/" + externalId,
                company,
                description,
                null,
                null,
                "RUB",
                remote,
                "Москва",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of());
    }
}

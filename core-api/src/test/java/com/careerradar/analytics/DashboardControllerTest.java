package com.careerradar.analytics;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DashboardControllerTest extends AbstractIntegrationTest {

    @Test
    void countsOnlyActiveVacancies() throws Exception {
        String token = loginToken();
        vacancyUpsertService.upsert(new VacancyUpsertRequest("MTS", List.of(
                draft("mts-1", "Android Intern"),
                draft("mts-2", "Android Developer"),
                draft("mts-3", "Java Developer"),
                draft("mts-4", "QA Engineer"),
                draft("mts-5", "Archived Android"))));

        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'ANDROID'),
                       seniority = 'INTERN',
                       remote_type = 'REMOTE'
                 where title = 'Android Intern'
                """);
        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'ANDROID'),
                       seniority = 'JUNIOR',
                       remote_type = 'REMOTE'
                 where title = 'Android Developer'
                """);
        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'JAVA_BACKEND'),
                       seniority = 'JUNIOR',
                       remote_type = 'OFFICE'
                 where title = 'Java Developer'
                """);
        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'QA'),
                       seniority = 'MIDDLE',
                       remote_type = 'HYBRID'
                 where title = 'QA Engineer'
                """);
        jdbcTemplate.update("""
                update vacancies
                   set profession_id = (select id from professions where code = 'ANDROID'),
                       seniority = 'SENIOR',
                       remote_type = 'REMOTE',
                       status = 'ARCHIVED'
                 where title = 'Archived Android'
                """);

        linkSkill("Android Intern", "KOTLIN");
        linkSkill("Android Developer", "KOTLIN");
        linkSkill("Android Intern", "COROUTINES");

        mockMvc.perform(get("/api/v1/dashboard").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalVacancies").value(4))
                .andExpect(jsonPath("$.newToday").value(4))
                .andExpect(jsonPath("$.internships").value(1))
                .andExpect(jsonPath("$.remoteShare").value(0.5))
                .andExpect(jsonPath("$.topProfessions[0].code").value("ANDROID"))
                .andExpect(jsonPath("$.topProfessions[0].count").value(2))
                .andExpect(jsonPath("$.topSkills[0].name").value("Kotlin"))
                .andExpect(jsonPath("$.topSkills[0].count").value(2));
    }

    @Test
    void returnsZeroesOnEmptyDatabase() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/v1/dashboard").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(0))
                .andExpect(jsonPath("$.remoteShare").value(0.0))
                .andExpect(jsonPath("$.topProfessions").isEmpty())
                .andExpect(jsonPath("$.topSkills").isEmpty());
    }

    private void linkSkill(String title, String skillCode) {
        jdbcTemplate.update("""
                insert into vacancy_skills (vacancy_id, skill_id)
                select v.id, s.id
                  from vacancies v, skills s
                 where v.title = ? and s.code = ?
                """, title, skillCode);
    }

    private static VacancyDraftRequest draft(String externalId, String title) {
        return new VacancyDraftRequest(
                externalId,
                title,
                "https://job.mts.ru/vacancies/" + externalId,
                "МТС",
                "Test vacancy",
                null,
                null,
                "RUB",
                null,
                "Москва",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of(),
                null,
                null);
    }
}

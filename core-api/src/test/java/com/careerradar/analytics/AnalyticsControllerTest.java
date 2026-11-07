package com.careerradar.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.support.AbstractIntegrationTest;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.upsert.VacancyDraftRequest;
import com.careerradar.vacancy.upsert.VacancyUpsertRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AnalyticsControllerTest extends AbstractIntegrationTest {

    private static final String OCTOBER = "from=2026-10-01&to=2026-10-31";

    @Test
    void overviewAggregatesOnlyActiveVacanciesInPeriod() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/overview?" + OCTOBER).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalVacancies").value(5))
                .andExpect(jsonPath("$.newVacancies").value(5))
                .andExpect(jsonPath("$.medianSalary").value(100000))
                .andExpect(jsonPath("$.p25Salary").value(80000))
                .andExpect(jsonPath("$.p75Salary").value(120000))
                .andExpect(jsonPath("$.remoteShare").value(0.4))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.topProfessions[0].code").value("ANDROID"))
                .andExpect(jsonPath("$.topProfessions[0].name").value("Android Developer"))
                .andExpect(jsonPath("$.topProfessions[0].count").value(3))
                .andExpect(jsonPath("$.topSkills[0].name").value("Kotlin"))
                .andExpect(jsonPath("$.topSkills[0].count").value(3));
    }

    @Test
    void overviewAppliesProfessionSeniorityAndRemoteFilters() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/overview?" + OCTOBER + "&profession=android&seniority=JUNIOR")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(1))
                .andExpect(jsonPath("$.medianSalary").value(80000))
                .andExpect(jsonPath("$.remoteShare").value(1.0));

        mockMvc.perform(get("/api/v1/analytics/overview?" + OCTOBER + "&remote=REMOTE").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(2))
                .andExpect(jsonPath("$.remoteShare").value(1.0));
    }

    @Test
    void overviewCountsNewVacanciesInsidePeriod() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/overview?from=2026-10-03&to=2026-10-31").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(3))
                .andExpect(jsonPath("$.newVacancies").value(3));
    }

    @Test
    void timelineGroupsByDayWeekAndMonth() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/timeline?" + OCTOBER + "&profession=ANDROID&bucket=day")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.points[0].count").value(2))
                .andExpect(jsonPath("$.points[1].date").value("2026-10-08"))
                .andExpect(jsonPath("$.points[1].count").value(1));

        mockMvc.perform(get("/api/v1/analytics/timeline?" + OCTOBER + "&profession=ANDROID&bucket=week")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].date").value("2026-09-28"))
                .andExpect(jsonPath("$.points[0].count").value(2))
                .andExpect(jsonPath("$.points[1].date").value("2026-10-05"))
                .andExpect(jsonPath("$.points[1].count").value(1));

        mockMvc.perform(get("/api/v1/analytics/timeline?profession=ANDROID&bucket=month")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.points[0].count").value(1))
                .andExpect(jsonPath("$.points[1].date").value("2026-10-01"))
                .andExpect(jsonPath("$.points[1].count").value(3));
    }

    @Test
    void skillsReturnsCountsShareAndRespectsLimit() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/skills?profession=ANDROID&limit=10").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].skill").value("Kotlin"))
                .andExpect(jsonPath("$.items[0].count").value(3))
                .andExpect(jsonPath("$.items[0].share").value(0.75))
                .andExpect(jsonPath("$.items[1].skill").value("Coroutines"))
                .andExpect(jsonPath("$.items[1].count").value(1));

        mockMvc.perform(get("/api/v1/analytics/skills?profession=ANDROID&limit=1").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void salariesReturnsPercentilesForTheSample() throws Exception {
        String token = loginToken();
        seed();

        mockMvc.perform(get("/api/v1/analytics/salaries?profession=JAVA_BACKEND").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.p25").value(140000))
                .andExpect(jsonPath("$.median").value(160000))
                .andExpect(jsonPath("$.p75").value(180000))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.sampleSize").value(2));

        mockMvc.perform(get("/api/v1/analytics/salaries?profession=ANDROID&seniority=MIDDLE")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.median").value(100000))
                .andExpect(jsonPath("$.sampleSize").value(1));
    }

    @Test
    void emptyDatabaseReturnsEmptyAnalytics() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/v1/analytics/overview?" + OCTOBER).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(0))
                .andExpect(jsonPath("$.newVacancies").value(0))
                .andExpect(jsonPath("$.remoteShare").value(0.0))
                .andExpect(jsonPath("$.medianSalary").doesNotExist())
                .andExpect(jsonPath("$.topProfessions").isEmpty())
                .andExpect(jsonPath("$.topSkills").isEmpty());

        mockMvc.perform(get("/api/v1/analytics/timeline?" + OCTOBER + "&bucket=week").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isEmpty());

        mockMvc.perform(get("/api/v1/analytics/skills").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/api/v1/analytics/salaries").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleSize").value(0))
                .andExpect(jsonPath("$.median").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/v1/analytics/timeline?bucket=year").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/overview?from=2026-10-31&to=2026-10-01").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/overview?top=0").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/overview?top=21").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/skills?limit=101").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/overview?seniority=SUPER").with(bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private void seed() {
        vacancyUpsertService.upsert(new VacancyUpsertRequest("MTS", List.of(
                draft("a-1", "Kotlin Intern", "ANDROID", Seniority.INTERN, RemoteType.REMOTE,
                        "60000", "2026-10-01T09:00:00Z"),
                draft("a-2", "Kotlin Junior", "ANDROID", Seniority.JUNIOR, RemoteType.REMOTE,
                        "80000", "2026-10-01T12:00:00Z"),
                draft("a-3", "Kotlin Middle", "ANDROID", Seniority.MIDDLE, RemoteType.OFFICE,
                        "100000", "2026-10-08T09:00:00Z"),
                draft("j-1", "Java Junior", "JAVA_BACKEND", Seniority.JUNIOR, RemoteType.HYBRID,
                        "120000", "2026-10-08T09:00:00Z"),
                draft("j-2", "Java Senior", "JAVA_BACKEND", Seniority.SENIOR, RemoteType.OFFICE,
                        "200000", "2026-10-15T09:00:00Z"),
                draft("old-1", "Old Android", "ANDROID", Seniority.JUNIOR, RemoteType.REMOTE,
                        "90000", "2026-09-01T09:00:00Z"),
                draft("arch-1", "Archived Android", "ANDROID", Seniority.INTERN, RemoteType.REMOTE,
                        "70000", "2026-10-08T09:00:00Z"))));

        setFirstSeen("Kotlin Intern", "2026-10-01T10:00:00Z");
        setFirstSeen("Kotlin Junior", "2026-10-02T10:00:00Z");
        setFirstSeen("Kotlin Middle", "2026-10-03T10:00:00Z");
        setFirstSeen("Java Junior", "2026-10-04T10:00:00Z");
        setFirstSeen("Java Senior", "2026-10-05T10:00:00Z");
        setFirstSeen("Old Android", "2026-09-01T10:00:00Z");
        setFirstSeen("Archived Android", "2026-10-03T10:00:00Z");
        jdbcTemplate.update("update vacancies set status = 'ARCHIVED' where title = 'Archived Android'");

        linkSkill("Kotlin Intern", "KOTLIN");
        linkSkill("Kotlin Junior", "KOTLIN");
        linkSkill("Kotlin Middle", "KOTLIN");
        linkSkill("Kotlin Intern", "COROUTINES");
        linkSkill("Java Junior", "JAVA");
        linkSkill("Java Senior", "JAVA");
    }

    private void setFirstSeen(String title, String instant) {
        jdbcTemplate.update("""
                update vacancies
                   set first_seen_at = cast(? as timestamptz)
                 where title = ?
                """, instant, title);
    }

    private void linkSkill(String title, String skillCode) {
        jdbcTemplate.update("""
                insert into vacancy_skills (vacancy_id, skill_id)
                select v.id, s.id
                  from vacancies v, skills s
                 where v.title = ? and s.code = ?
                """, title, skillCode);
    }

    private static VacancyDraftRequest draft(
            String externalId,
            String title,
            String profession,
            Seniority seniority,
            RemoteType remoteType,
            String salaryFrom,
            String publishedAt) {
        return new VacancyDraftRequest(
                externalId,
                title,
                "https://job.mts.ru/vacancies/" + externalId,
                "МТС",
                "Test vacancy",
                new BigDecimal(salaryFrom),
                null,
                "RUB",
                null,
                "Москва",
                seniority,
                Instant.parse(publishedAt),
                Map.of(),
                remoteType,
                profession);
    }
}

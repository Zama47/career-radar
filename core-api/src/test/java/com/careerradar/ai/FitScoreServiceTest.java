package com.careerradar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.llm.LlmClient;
import com.careerradar.llm.LlmResponse;
import com.careerradar.resume.api.ResumeRequest;
import com.careerradar.resume.api.ResumeService;
import com.careerradar.support.AbstractIntegrationTest;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.upsert.VacancyDraftRequest;
import com.careerradar.vacancy.upsert.VacancyUpsertRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FitScoreServiceTest extends AbstractIntegrationTest {

    private static final String VALID_JSON = """
            {"score": 78, "verdict": "good_match",
             "strengths": ["Kotlin", "Compose"],
             "missing": ["Coroutines Flow", "CI"],
             "reasons": ["Есть релевантный Android-опыт.", "Не хватает CI."]}
            """;

    @MockitoBean
    private LlmClient llmClient;

    @Autowired
    private ResumeService resumeService;

    @Test
    void returnsStructuredFitScoreAndRecordsRun() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android, Kotlin, Compose, CI");
        String resumeId = createResume("Kotlin, Compose, Room");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "```json\n" + VALID_JSON + "\n```", "deepseek-v4.1-flash", 3200, 450, 900));

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(78))
                .andExpect(jsonPath("$.verdict").value("good_match"))
                .andExpect(jsonPath("$.strengths[0]").value("Kotlin"))
                .andExpect(jsonPath("$.missing[1]").value("CI"))
                .andExpect(jsonPath("$.tokens.input").value(3200))
                .andExpect(jsonPath("$.tokens.output").value(450))
                .andExpect(jsonPath("$.cached").value(false));

        Map<String, Object> run = jdbcTemplate.queryForMap("select * from llm_runs");
        assertThat(run.get("task")).isEqualTo("FIT_SCORE");
        assertThat(run.get("status")).isEqualTo("SUCCESS");
        assertThat(run.get("input_tokens")).isEqualTo(3200);
        assertThat(run.get("output_tokens")).isEqualTo(450);
        assertThat(run.get("prompt_version")).isEqualTo("fit-score/v1");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from resume_variants where fit_score = 78", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void invalidJsonBecomesProblemDetailAndFailedRun() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        String resumeId = createResume("Kotlin");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "Извините, не могу вернуть JSON", "deepseek-v4.1-flash", 10, 2, 50));

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").isString());

        Map<String, Object> run = jdbcTemplate.queryForMap("select * from llm_runs");
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("input_tokens")).isEqualTo(10);
        assertThat(run.get("output_tokens")).isEqualTo(2);
        assertThat(run.get("error_message")).asString().contains("JSON");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from resume_variants", Integer.class))
                .isZero();
    }

    @Test
    void secondCallUsesSevenDayCache() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        String resumeId = createResume("Kotlin");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                VALID_JSON, "deepseek-v4.1-flash", 100, 20, 30));
        String body = "{\"resumeId\":\"" + resumeId + "\"}";

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.tokens.input").value(100));

        verify(llmClient, times(1)).complete(any());
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from llm_cache", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void forceRefreshBypassesCache() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        String resumeId = createResume("Kotlin");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                VALID_JSON, "deepseek-v4.1-flash", 100, 20, 30));
        String body = "{\"resumeId\":\"" + resumeId + "\", \"forceRefresh\": true}";

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(llmClient, times(2)).complete(any());
    }

    @Test
    void emptyResumeTextIsRejectedBeforeCallingLlm() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        UUID resumeId = jdbcTemplate.queryForObject("""
                insert into resumes (title, content_json, content_text)
                values ('Blank', '{}'::jsonb, '   ')
                returning public_id
                """, UUID.class);

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/fit")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("contentText")));

        verify(llmClient, never()).complete(any());
        assertThat(jdbcTemplate.queryForObject("select count(*) from llm_runs", Integer.class))
                .isZero();
    }

    private String createResume(String text) {
        return resumeService.create(new ResumeRequest(
                        "Master", Map.of("summary", text), text, true, null))
                .id()
                .toString();
    }

    private String createVacancy(String description) {
        vacancyUpsertService.upsert(new VacancyUpsertRequest("HH", List.of(new VacancyDraftRequest(
                "fit-" + UUID.randomUUID(),
                "Android Developer",
                "https://hh.ru/vacancy/fit-" + UUID.randomUUID(),
                "Romashka",
                description,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(150_000),
                "RUB",
                true,
                "Москва",
                Seniority.JUNIOR,
                Instant.now(),
                null,
                RemoteType.REMOTE,
                "ANDROID"))));
        return jdbcTemplate.queryForObject(
                "select public_id::text from vacancies limit 1", String.class);
    }
}

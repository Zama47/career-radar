package com.careerradar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

class TailorServiceTest extends AbstractIntegrationTest {

    private static final String VALID_JSON = """
            {"contentJson": {"summary": "Android: Kotlin, Compose", "skills": ["Kotlin"]},
             "diff": ["Акцент на Compose"],
             "warnings": ["Проверьте даты проектов"]}
            """;

    @MockitoBean
    private LlmClient llmClient;

    @Autowired
    private ResumeService resumeService;

    @Test
    void returnsDraftAndSavesVariantWithoutTouchingMaster() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Kotlin, Compose, CI");
        String resumeId = createResume("Kotlin, Room");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                VALID_JSON, "deepseek-v4-pro", 900, 200, 700));

        mockMvc.perform(post("/api/v1/resumes/" + resumeId + "/tailor")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vacancyId\":\"" + vacancyId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentJson.summary").value("Android: Kotlin, Compose"))
                .andExpect(jsonPath("$.diff[0]").value("Акцент на Compose"))
                .andExpect(jsonPath("$.warnings[0]").value("Проверьте даты проектов"))
                .andExpect(jsonPath("$.warnings[1]").value(
                        org.hamcrest.Matchers.containsString("Черновик")))
                .andExpect(jsonPath("$.model").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.cached").value(false));

        assertThat(jdbcTemplate.queryForObject(
                        "select content_text from resumes", String.class))
                .isEqualTo("Kotlin, Room");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from resume_variants", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        select count(*) from llm_runs
                        where task = 'RESUME_TAILOR' and status = 'SUCCESS'
                        """,
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void emptyTailoredContentIsRejectedAsBadGateway() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Kotlin");
        String resumeId = createResume("Kotlin");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"contentJson\":{}, \"diff\":[], \"warnings\":[]}",
                "deepseek-v4-pro", 10, 5, 10));

        mockMvc.perform(post("/api/v1/resumes/" + resumeId + "/tailor")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vacancyId\":\"" + vacancyId + "\"}"))
                .andExpect(status().isBadGateway());

        assertThat(jdbcTemplate.queryForObject(
                        "select status from llm_runs", String.class))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from resume_variants", Integer.class))
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
                "tailor-" + UUID.randomUUID(),
                "Android Developer",
                "https://hh.ru/vacancy/tailor-" + UUID.randomUUID(),
                "Romashka",
                description,
                BigDecimal.valueOf(100_000),
                null,
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

package com.careerradar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.llm.LlmClient;
import com.careerradar.llm.LlmRequest;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CoverLetterServiceTest extends AbstractIntegrationTest {

    private static final String VALID_JSON = """
            {"subject": "Отклик на Android Developer",
             "text": "Здравствуйте! Я делал Android-приложение на Kotlin и Compose."}
            """;

    @MockitoBean
    private LlmClient llmClient;

    @Autowired
    private ResumeService resumeService;

    @Test
    void generatesDraftAndSavesVariant() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Требуется Kotlin, Compose, CI");
        String resumeId = createResume("Kotlin, Compose, Room, проект CoffeeApp");

        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                VALID_JSON, "deepseek-v4-pro", 1800, 300, 1500));

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/cover-letter")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\", \"tone\":\"professional\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Отклик на Android Developer"))
                .andExpect(jsonPath("$.text").value(
                        org.hamcrest.Matchers.containsString("Kotlin")))
                .andExpect(jsonPath("$.model").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.tokens.input").value(1800))
                .andExpect(jsonPath("$.cached").value(false));

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.model()).isEqualTo("deepseek-v4-pro");
        assertThat(request.maxOutputTokens()).isEqualTo(1200);
        assertThat(request.userPrompt())
                .contains("Kotlin, Compose, Room, проект CoffeeApp")
                .contains("Требуется Kotlin, Compose, CI")
                .contains("профессиональный");

        assertThat(jdbcTemplate.queryForObject(
                        "select cover_letter from resume_variants", String.class))
                .contains("Здравствуйте");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from llm_runs where task = 'COVER_LETTER' and status = 'SUCCESS'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsUnknownToneWithoutCallingLlm() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        String resumeId = createResume("Kotlin");

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/cover-letter")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\", \"tone\":\"poetic\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("tone")));

        verify(llmClient, never()).complete(any());
    }

    @Test
    void rejectsEmptyResumeText() throws Exception {
        String token = loginToken();
        String vacancyId = createVacancy("Android");
        UUID resumeId = jdbcTemplate.queryForObject("""
                insert into resumes (title, content_json, content_text)
                values ('Blank', '{}'::jsonb, '')
                returning public_id
                """, UUID.class);

        mockMvc.perform(post("/api/v1/vacancies/" + vacancyId + "/cover-letter")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\"}"))
                .andExpect(status().isBadRequest());

        verify(llmClient, never()).complete(any());
    }

    private String createResume(String text) {
        return resumeService.create(new ResumeRequest(
                        "Master", Map.of("summary", text), text, true, null))
                .id()
                .toString();
    }

    private String createVacancy(String description) {
        vacancyUpsertService.upsert(new VacancyUpsertRequest("HH", List.of(new VacancyDraftRequest(
                "cover-" + UUID.randomUUID(),
                "Android Developer",
                "https://hh.ru/vacancy/cover-" + UUID.randomUUID(),
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

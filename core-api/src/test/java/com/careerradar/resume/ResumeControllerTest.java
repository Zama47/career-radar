package com.careerradar.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ResumeControllerTest extends AbstractIntegrationTest {

    private static final String RESUMES = "/api/v1/resumes";

    @Test
    void createsListsDownloadsAndDeletesResume() throws Exception {
        String token = loginToken();
        String id = createResume(token, "Android master", "Kotlin, Compose, Room", true, pdf());

        mockMvc.perform(get(RESUMES).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].isMaster").value(true))
                .andExpect(jsonPath("$[0].hasPdf").value(true));

        mockMvc.perform(get(RESUMES + "/" + id).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentText").value("Kotlin, Compose, Room"))
                .andExpect(jsonPath("$.contentJson.summary").value("Kotlin, Compose, Room"));

        MvcResult file = mockMvc.perform(get(RESUMES + "/" + id + "/file").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(file.getResponse().getContentAsByteArray())
                .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(delete(RESUMES + "/" + id).with(bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(RESUMES + "/" + id).with(bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsOnlyOneMasterAndUpdatesContent() throws Exception {
        String token = loginToken();
        String first = createResume(token, "First", "Java, Spring", true, null);
        String second = createResume(token, "Second", "Kotlin, Android", true, null);

        String list = mockMvc.perform(get(RESUMES).with(bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode items = objectMapper.readTree(list);
        assertThat(StreamSupport.stream(items.spliterator(), false)
                        .filter(item -> item.path("isMaster").asBoolean())
                        .count())
                .isEqualTo(1);
        JsonNode secondNode = StreamSupport.stream(items.spliterator(), false)
                .filter(item -> second.equals(item.path("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(secondNode.path("isMaster").asBoolean()).isTrue();

        mockMvc.perform(put(RESUMES + "/" + first)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("First updated", "Java, Spring, PostgreSQL", true, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First updated"))
                .andExpect(jsonPath("$.isMaster").value(true));
    }

    @Test
    void updatingCurrentMasterKeepsItMaster() throws Exception {
        String token = loginToken();
        String id = createResume(token, "Master", "Kotlin", true, null);

        mockMvc.perform(put(RESUMES + "/" + id)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Master renamed", "Kotlin, Compose", true, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMaster").value(true));

        mockMvc.perform(get(RESUMES + "/" + id).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMaster").value(true))
                .andExpect(jsonPath("$.title").value("Master renamed"));
    }

    @Test
    void deletingMasterLeavesOtherResumesIntact() throws Exception {
        String token = loginToken();
        String first = createResume(token, "First", "Java", true, null);
        String second = createResume(token, "Second", "Kotlin", false, null);

        mockMvc.perform(delete(RESUMES + "/" + first).with(bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(RESUMES + "/" + second).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Second"));
    }

    @Test
    void rejectsNonPdfAndMalformedBase64() throws Exception {
        String token = loginToken();
        String notPdf = Base64.getEncoder()
                .encodeToString("hello".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(post(RESUMES)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Broken", "Text", false, notPdf)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(RESUMES)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Broken", "Text", false, "not-base64!!")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankContentAndMissingToken() throws Exception {
        mockMvc.perform(post(RESUMES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("No auth", "Text", false, null)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(RESUMES)
                        .with(bearer(loginToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Blank", "   ", false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void unknownResumeReturns404() throws Exception {
        mockMvc.perform(get(RESUMES + "/" + java.util.UUID.randomUUID())
                        .with(bearer(loginToken())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private String createResume(
            String token, String title, String text, boolean master, String pdfBase64)
            throws Exception {
        String response = mockMvc.perform(post(RESUMES)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, text, master, pdfBase64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("id").asText();
    }

    private String body(String title, String text, boolean master, String pdfBase64)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("contentJson", Map.of("summary", text));
        body.put("contentText", text);
        body.put("isMaster", master);
        if (pdfBase64 != null) {
            body.put("pdfBase64", pdfBase64);
        }
        return objectMapper.writeValueAsString(body);
    }

    private static String pdf() {
        return Base64.getEncoder()
                .encodeToString("%PDF-1.4\n% test resume\n".getBytes(StandardCharsets.US_ASCII));
    }
}

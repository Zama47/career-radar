package com.careerradar.vacancy.upsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careerradar.common.security.InternalTokenFilter;
import com.careerradar.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class InternalVacancyControllerTest extends AbstractIntegrationTest {

    private static final String UPSERT_PATH = "/internal/v1/vacancies:upsert";

    private static final String VALID_BODY = """
            {
              "source": "HH",
              "items": [
                {"externalId": "hh-1", "title": "Android Developer", "url": "https://hh.ru/vacancy/1",
                 "companyName": "Romashka", "salaryFrom": 100000, "salaryTo": 150000,
                 "currency": "RUB", "remote": true},
                {"externalId": "hh-2", "title": "Java Developer", "url": "https://hh.ru/vacancy/2",
                 "companyName": "Acme", "currency": "RUB"},
                {"externalId": "hh-3", "title": "QA Engineer", "url": "https://hh.ru/vacancy/3",
                 "companyName": "Ozon", "currency": "RUB"}
              ]
            }
            """;

    @Test
    void acceptsCollectorFieldAliasesAndClassification() throws Exception {
        String body = """
                {"source": "HH", "items": [
                  {"externalId": "hh-42", "title": "Android-разработчик (стажер)",
                   "url": "https://hh.ru/vacancy/42", "company": "Romashka",
                   "remoteType": "HYBRID", "profession": "ANDROID", "seniority": "INTERN"}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        var row = jdbcTemplate.queryForMap("""
                select c.name as company, v.remote_type, v.seniority, p.code as profession
                from vacancies v
                left join companies c on c.id = v.company_id
                left join professions p on p.id = v.profession_id
                """);
        assertThat(row.get("company")).isEqualTo("Romashka");
        assertThat(row.get("remote_type")).isEqualTo("HYBRID");
        assertThat(row.get("seniority")).isEqualTo("INTERN");
        assertThat(row.get("profession")).isEqualTo("ANDROID");
    }

    @Test
    void acceptsRawJsonAliasAndScrubsCredentials() throws Exception {
        String body = """
                {"source": "HH", "items": [
                  {"externalId": "hh-raw-1", "title": "Android Developer",
                   "url": "https://hh.ru/vacancy/raw-1",
                   "rawJson": {"token": "super-secret", "public": "kept"}}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        String raw = jdbcTemplate.queryForObject(
                "select raw_json::text from vacancy_sources", String.class);
        assertThat(raw).contains("kept").doesNotContain("super-secret").doesNotContain("\"token\"");
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post(UPSERT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString(InternalTokenFilter.HEADER)));

        assertThatTableIsEmpty();
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        assertThatTableIsEmpty();
    }

    @Test
    void acceptsBatchAndIsIdempotent() throws Exception {
        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.received").value(3))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0));

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(3));

        Integer vacancies = jdbcTemplate.queryForObject("select count(*) from vacancies", Integer.class);
        org.assertj.core.api.Assertions.assertThat(vacancies).isEqualTo(3);
    }

    @Test
    void rejectsItemWithoutTitle() throws Exception {
        String body = """
                {"source": "HH", "items": [
                  {"externalId": "hh-1", "url": "https://hh.ru/vacancy/1"}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(containsString("title"))));

        assertThatTableIsEmpty();
    }

    @Test
    void rejectsInvertedSalaryRange() throws Exception {
        String body = """
                {"source": "HH", "items": [
                  {"externalId": "hh-1", "title": "Android Developer", "url": "https://hh.ru/vacancy/1",
                   "salaryFrom": 200000, "salaryTo": 100000}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(containsString("salaryRangeValid"))));

        assertThatTableIsEmpty();
    }

    @Test
    void rejectsRelativeUrl() throws Exception {
        String body = """
                {"source": "HH", "items": [
                  {"externalId": "hh-1", "title": "Android Developer", "url": "/vacancy/1"}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyItems() throws Exception {
        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"HH\", \"items\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownSource() throws Exception {
        String body = """
                {"source": "UNKNOWN", "items": [
                  {"externalId": "x-1", "title": "Android Developer", "url": "https://example.com/1"}
                ]}
                """;

        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post(UPSERT_PATH)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void assertThatTableIsEmpty() {
        Integer vacancies = jdbcTemplate.queryForObject("select count(*) from vacancies", Integer.class);
        org.assertj.core.api.Assertions.assertThat(vacancies).isZero();
    }
}

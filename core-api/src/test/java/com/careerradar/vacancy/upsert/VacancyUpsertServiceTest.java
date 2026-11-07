package com.careerradar.vacancy.upsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careerradar.common.error.NotFoundException;
import com.careerradar.company.CompanyRepository;
import com.careerradar.support.AbstractIntegrationTest;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Vacancy;
import com.careerradar.vacancy.VacancyRepository;
import com.careerradar.vacancy.VacancySourceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VacancyUpsertServiceTest extends AbstractIntegrationTest {

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private VacancySourceRepository vacancySourceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void createsVacanciesAndReusesCompanies() {
        VacancyUpsertResponse response = vacancyUpsertService.upsert(request("HH",
                draft("hh-1", "Android Developer", "ООО «Ромашка»"),
                draft("hh-2", "Java Developer", "Ромашка"),
                draft("hh-3", "QA Engineer", "Acme LLC")));

        assertThat(response.received()).isEqualTo(3);
        assertThat(response.created()).isEqualTo(3);
        assertThat(response.updated()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(vacancyRepository.count()).isEqualTo(3);
        assertThat(vacancySourceRepository.count()).isEqualTo(3);
        assertThat(companyRepository.count()).isEqualTo(2);
    }

    @Test
    void updatesVacancyBySourceAndExternalId() {
        vacancyUpsertService.upsert(request("HH",
                draft("hh-1", "Android Developer", "Ромашка",
                        new BigDecimal("100000"), new BigDecimal("150000"), false)));
        Long vacancyId = vacancyRepository.findAll().getFirst().getId();
        String originalHash = vacancyRepository.findById(vacancyId).orElseThrow().getContentHash();

        VacancyUpsertResponse response = vacancyUpsertService.upsert(request("HH",
                draft("hh-1", "Android Developer", "Ромашка",
                        new BigDecimal("200000"), new BigDecimal("250000"), true)));

        assertThat(response.received()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.created()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(vacancyRepository.count()).isEqualTo(1);

        Vacancy refreshed = vacancyRepository.findById(vacancyId).orElseThrow();
        assertThat(refreshed.getSalaryFrom()).isEqualByComparingTo("200000");
        assertThat(refreshed.getSalaryTo()).isEqualByComparingTo("250000");
        assertThat(refreshed.getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(refreshed.getContentHash()).isNotEqualTo(originalHash);
        assertThat(refreshed.getLastSeenAt()).isAfterOrEqualTo(refreshed.getFirstSeenAt());
    }

    @Test
    void linksContentHashDuplicateInsteadOfCreatingIt() {
        vacancyUpsertService.upsert(request("HH", draft("hh-1", "Android Developer", "Ромашка")));

        VacancyUpsertResponse response = vacancyUpsertService.upsert(request("HH",
                draft("hh-99", "  android   developer ", "ООО Ромашка")));

        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.created()).isZero();
        assertThat(response.updated()).isZero();
        assertThat(vacancyRepository.count()).isEqualTo(1);
        assertThat(vacancySourceRepository.count()).isEqualTo(2);
    }

    @Test
    void deduplicatesAcrossSources() {
        vacancyUpsertService.upsert(request("HH", draft("hh-1", "Android Developer", "Ромашка")));

        VacancyUpsertResponse response = vacancyUpsertService.upsert(request("OTHER",
                draft("other-7", "Android Developer", "Ромашка")));

        assertThat(response.skipped()).isEqualTo(1);
        assertThat(vacancyRepository.count()).isEqualTo(1);
        assertThat(vacancySourceRepository.count()).isEqualTo(2);
    }

    @Test
    void reportsBatchCounts() {
        vacancyUpsertService.upsert(request("HH", draft("hh-1", "Android Developer", "Ромашка")));

        VacancyUpsertResponse response = vacancyUpsertService.upsert(request("HH",
                draft("hh-1", "Android Developer", "Ромашка"),
                draft("hh-2", "QA Engineer", "Acme"),
                draft("hh-3", "Android Developer", "Ромашка")));

        assertThat(response.received()).isEqualTo(3);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(vacancyRepository.count()).isEqualTo(2);
    }

    @Test
    void stripsCredentialLikeKeysFromRawPayload() {
        VacancyDraftRequest draft = new VacancyDraftRequest(
                "hh-7",
                "Android Developer",
                "https://hh.ru/vacancy/7",
                "Ромашка",
                "Description",
                null,
                null,
                "RUB",
                false,
                null,
                null,
                null,
                Map.of(
                        "token", "super-secret",
                        "nested", Map.of("password", "hunter2", "public", "kept"),
                        "public", "kept"),
                null,
                null);

        vacancyUpsertService.upsert(request("HH", draft));

        String raw = jdbcTemplate.queryForObject(
                "select raw_json::text from vacancy_sources", String.class);
        assertThat(raw).contains("public").contains("kept");
        assertThat(raw).doesNotContain("super-secret").doesNotContain("hunter2")
                .doesNotContain("\"token\"").doesNotContain("\"password\"");
    }

    @Test
    void rejectsUnknownSource() {
        assertThatThrownBy(() -> vacancyUpsertService.upsert(
                request("UNKNOWN", draft("x-1", "Android Developer", "Ромашка"))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        assertThat(vacancyRepository.count()).isZero();
    }

    private static VacancyUpsertRequest request(String source, VacancyDraftRequest... items) {
        return new VacancyUpsertRequest(source, List.of(items));
    }

    private static VacancyDraftRequest draft(String externalId, String title, String company) {
        return draft(externalId, title, company, null, null, false);
    }

    private static VacancyDraftRequest draft(
            String externalId,
            String title,
            String company,
            BigDecimal salaryFrom,
            BigDecimal salaryTo,
            boolean remote) {
        return new VacancyDraftRequest(
                externalId,
                title,
                "https://hh.ru/vacancy/" + externalId,
                company,
                "Android vacancy description",
                salaryFrom,
                salaryTo,
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

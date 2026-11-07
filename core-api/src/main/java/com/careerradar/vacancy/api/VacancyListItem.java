package com.careerradar.vacancy.api;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.SalaryPeriod;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.VacancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vacancy as exposed by the public read API.
 */
@Schema(name = "VacancyListItem")
public record VacancyListItem(
        UUID id,
        String title,
        String company,
        @Schema(description = "Profession code", example = "JAVA_BACKEND") String profession,
        Seniority seniority,
        RemoteType remoteType,
        String location,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String currency,
        Boolean salaryGross,
        SalaryPeriod salaryPeriod,
        Instant publishedAt,
        Instant firstSeenAt,
        Instant lastSeenAt,
        VacancyStatus status,
        @Schema(description = "First known source URL for this vacancy") String url) {
}

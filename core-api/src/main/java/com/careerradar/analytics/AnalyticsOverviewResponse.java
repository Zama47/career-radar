package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Market snapshot for the selected filters. Salary percentiles are in {@link #currency()} and
 * are {@code null} when no vacancy in the filter has a salary.
 */
@Schema(name = "AnalyticsOverview")
public record AnalyticsOverviewResponse(
        @Schema(example = "1234") long totalVacancies,
        @Schema(description = "First seen inside the requested period (or today)", example = "120")
        long newVacancies,
        @Schema(description = "Median salary, RUB by default", example = "80000") Long medianSalary,
        @Schema(example = "60000") Long p25Salary,
        @Schema(example = "120000") Long p75Salary,
        @Schema(description = "Share of REMOTE vacancies, 0..1", example = "0.31") double remoteShare,
        @Schema(description = "Currency of the salary percentiles", example = "RUB") String currency,
        List<TopProfession> topProfessions,
        List<TopSkill> topSkills) {

    @Schema(name = "AnalyticsTopProfession")
    public record TopProfession(String code, String name, long count) {
    }

    @Schema(name = "AnalyticsTopSkill")
    public record TopSkill(String name, long count) {
    }
}

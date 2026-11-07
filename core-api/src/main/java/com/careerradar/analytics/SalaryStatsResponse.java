package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salary percentiles for the selected filters. Values are {@code null} when the sample is
 * empty.
 */
@Schema(name = "AnalyticsSalaries")
public record SalaryStatsResponse(
        @Schema(example = "60000") Long p25,
        @Schema(example = "80000") Long median,
        @Schema(example = "120000") Long p75,
        @Schema(example = "RUB") String currency,
        @Schema(example = "120") long sampleSize) {
}

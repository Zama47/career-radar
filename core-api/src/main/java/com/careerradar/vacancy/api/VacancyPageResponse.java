package com.careerradar.vacancy.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Page envelope used by public list endpoints.
 */
@Schema(name = "VacancyPage")
public record VacancyPageResponse(
        List<VacancyListItem> items,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Effective page size", example = "20") int size,
        @Schema(description = "Total matching vacancies", example = "123") long total) {
}

package com.careerradar.vacancy.upsert;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of a batch upsert. {@code received} always equals {@code created + updated + skipped}.
 */
@Schema(name = "VacancyUpsertResponse")
public record VacancyUpsertResponse(
        @Schema(description = "Items accepted for processing", example = "3") int received,
        @Schema(description = "New vacancies created", example = "2") int created,
        @Schema(description = "Existing vacancies refreshed by source/external id", example = "1") int updated,
        @Schema(description = "Items matched an existing vacancy by content hash and were linked, not duplicated", example = "0")
        int skipped) {
}

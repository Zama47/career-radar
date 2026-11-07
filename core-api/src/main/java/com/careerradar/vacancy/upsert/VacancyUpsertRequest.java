package com.careerradar.vacancy.upsert;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Batch upsert payload: {@code {"source": "HH", "items": [...]}}.
 */
@Schema(name = "VacancyUpsertRequest")
public record VacancyUpsertRequest(

        @Schema(description = "Source code, case-insensitive", example = "HH")
        @NotBlank @Size(max = 32)
        String source,

        @Schema(description = "Normalized vacancies from that source")
        @NotEmpty @Size(max = 500)
        List<@Valid VacancyDraftRequest> items) {
}

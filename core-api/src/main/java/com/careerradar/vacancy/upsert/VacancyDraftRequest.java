package com.careerradar.vacancy.upsert;

import com.careerradar.vacancy.Seniority;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One normalized vacancy produced by an adapter. Unknown fields are ignored so collector and
 * core-api can evolve independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "VacancyDraft", description = "Normalized vacancy sent by the collector")
public record VacancyDraftRequest(

        @Schema(description = "Identifier of the vacancy on the source site", example = "12345678")
        @NotBlank @Size(max = 255)
        String externalId,

        @Schema(example = "Android Developer")
        @NotBlank @Size(max = 500)
        String title,

        @Schema(example = "https://hh.ru/vacancy/12345678")
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "must be an absolute http(s) URL")
        String url,

        @Schema(example = "ООО «Ромашка»")
        @Size(max = 255)
        String companyName,

        String description,

        @Schema(description = "Lower salary bound in the source currency")
        @PositiveOrZero
        BigDecimal salaryFrom,

        @Schema(description = "Upper salary bound in the source currency")
        @PositiveOrZero
        BigDecimal salaryTo,

        @Schema(example = "RUB")
        @Size(max = 8)
        String currency,

        @Schema(description = "True when the source marks the vacancy as remote")
        Boolean remote,

        @Size(max = 255)
        String location,

        Seniority seniority,

        Instant publishedAt,

        @Schema(description = "Source payload; credential-like keys are stripped before storage")
        Map<String, Object> raw) {

    @AssertTrue(message = "salaryTo must be greater than or equal to salaryFrom")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isSalaryRangeValid() {
        return salaryFrom == null || salaryTo == null || salaryTo.compareTo(salaryFrom) >= 0;
    }
}

package com.careerradar.vacancy.upsert;

import com.careerradar.common.config.OpenApiConfig;
import com.careerradar.common.security.InternalTokenFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion boundary. Only the collector calls this endpoint, authenticated with a shared token.
 */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "internal-vacancies", description = "Collector ingestion API")
public class InternalVacancyController {

    private final VacancyUpsertService upsertService;

    public InternalVacancyController(VacancyUpsertService upsertService) {
        this.upsertService = upsertService;
    }

    @PostMapping(
            path = "/vacancies:upsert",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Upsert a batch of normalized vacancies",
            description = "Idempotent by (source, externalId); content-hash duplicates are linked, not re-created.",
            security = @SecurityRequirement(name = OpenApiConfig.INTERNAL_TOKEN_SCHEME))
    @Parameter(name = InternalTokenFilter.HEADER, in = ParameterIn.HEADER, required = true,
            description = "Shared secret configured via INTERNAL_TOKEN")
    public VacancyUpsertResponse upsert(@Valid @RequestBody VacancyUpsertRequest request) {
        return upsertService.upsert(request);
    }
}

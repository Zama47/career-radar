package com.careerradar.vacancy.api;

import com.careerradar.common.error.BadRequestException;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated, read-only vacancy search consumed by the web app.
 */
@RestController
@RequestMapping("/api/v1/vacancies")
@Tag(name = "vacancies", description = "Vacancy search and detail cards")
@SecurityRequirement(name = "bearerAuth")
public class PublicVacancyController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final VacancyQueryService queryService;

    public PublicVacancyController(VacancyQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Search vacancies with pagination and optional filters")
    public VacancyPageResponse list(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, 1..100 (values outside the range are clamped)", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Case-insensitive substring of title, company or description")
            @RequestParam(required = false) String q,

            @Parameter(description = "Profession code, e.g. JAVA_BACKEND", example = "JAVA_BACKEND")
            @RequestParam(required = false) String profession,

            @Parameter(description = "Seniority filter", example = "JUNIOR")
            @RequestParam(required = false) Seniority seniority,

            @Parameter(description = "Workplace filter", example = "REMOTE")
            @RequestParam(required = false) RemoteType remote,

            @Parameter(description = "Only vacancies whose salary reaches this value")
            @RequestParam(required = false) BigDecimal salaryMin,

            @Parameter(description = "Sort order", example = "publishedAt,desc")
            @RequestParam(defaultValue = "publishedAt,desc") String sort) {

        if (salaryMin != null && salaryMin.signum() < 0) {
            throw new BadRequestException("salaryMin must not be negative");
        }

        VacancySearchQuery query = new VacancySearchQuery(
                Math.max(page, 0),
                clampSize(size),
                blankToNull(q),
                blankToNull(profession),
                seniority,
                remote,
                salaryMin,
                VacancySort.fromApiValue(sort));
        return queryService.search(query);
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Get a vacancy card by its public UUID")
    public VacancyDetailResponse get(
            @Parameter(description = "Public vacancy id", example = "3f1d9c62-5a0f-4f2c-9d5b-1c7a2b8e4f60")
            @PathVariable UUID publicId) {
        return queryService.get(publicId);
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

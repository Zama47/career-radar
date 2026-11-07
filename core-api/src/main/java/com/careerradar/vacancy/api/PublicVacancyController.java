package com.careerradar.vacancy.api;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only vacancy search consumed by the web app.
 */
@RestController
@RequestMapping("/api/v1/vacancies")
@Tag(name = "vacancies", description = "Public vacancy search")
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

            @Parameter(description = "Case-insensitive substring of title or description")
            @RequestParam(required = false) String q,

            @Parameter(description = "Profession code, e.g. JAVA_BACKEND", example = "JAVA_BACKEND")
            @RequestParam(required = false) String profession,

            @Parameter(description = "Seniority filter", example = "JUNIOR")
            @RequestParam(required = false) Seniority seniority,

            @Parameter(description = "Workplace filter", example = "REMOTE")
            @RequestParam(required = false) RemoteType remote) {

        VacancySearchQuery query = new VacancySearchQuery(
                Math.max(page, 0),
                clampSize(size),
                blankToNull(q),
                blankToNull(profession),
                seniority,
                remote);
        return queryService.search(query);
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

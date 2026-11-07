package com.careerradar.analytics;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market analytics consumed by the dashboard and the {@code /analytics} page.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics", description = "Vacancy market analytics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "KPI snapshot: totals, salary percentiles, remote share, top lists")
    public AnalyticsOverviewResponse overview(
            @Parameter(description = "Inclusive start date, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive end date, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Profession dictionary code") @RequestParam(required = false) String profession,
            @RequestParam(required = false) Seniority seniority,
            @RequestParam(required = false) RemoteType remote,
            @Parameter(description = "Size of the top professions and top skills lists, 1..20")
            @RequestParam(defaultValue = "5") int top) {
        return analyticsService.overview(filter(from, to, profession, seniority, remote), top);
    }

    @GetMapping("/timeline")
    @Operation(summary = "Vacancy counts grouped into day, week or month buckets")
    public TimelineResponse timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false) Seniority seniority,
            @RequestParam(required = false) RemoteType remote,
            @Parameter(description = "day, week or month") @RequestParam(defaultValue = "day") String bucket) {
        return analyticsService.timeline(
                filter(from, to, profession, seniority, remote), TimelineBucket.from(bucket));
    }

    @GetMapping("/skills")
    @Operation(summary = "Most requested skills with their share of matching vacancies")
    public SkillsResponse skills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false) Seniority seniority,
            @RequestParam(required = false) RemoteType remote,
            @RequestParam(defaultValue = "20") int limit) {
        return analyticsService.skills(
                filter(from, to, profession, seniority, remote), limit);
    }

    @GetMapping("/salaries")
    @Operation(summary = "Salary percentiles p25, median and p75")
    public SalaryStatsResponse salaries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false) Seniority seniority,
            @RequestParam(required = false) RemoteType remote) {
        return analyticsService.salaries(filter(from, to, profession, seniority, remote));
    }

    private static AnalyticsFilter filter(
            LocalDate from, LocalDate to, String profession, Seniority seniority, RemoteType remote) {
        return AnalyticsFilter.of(from, to, profession, seniority, remote);
    }
}

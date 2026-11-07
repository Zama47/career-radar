package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI endpoint feeding the dashboard page.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "dashboard", description = "Vacancy market KPI")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get the current KPI snapshot")
    public DashboardResponse dashboard() {
        return dashboardService.snapshot();
    }
}

package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * KPI snapshot of the current vacancy market.
 */
@Schema(name = "Dashboard")
public record DashboardResponse(
        @Schema(description = "Active vacancies in total", example = "1234") long totalVacancies,
        @Schema(description = "Vacancies first seen today (UTC)", example = "42") long newToday,
        @Schema(description = "Active INTERN vacancies", example = "87") long internships,
        @Schema(description = "Share of REMOTE vacancies, 0..1", example = "0.31") double remoteShare,
        List<TopProfession> topProfessions,
        List<TopSkill> topSkills) {

    @Schema(name = "TopProfession")
    public record TopProfession(String code, long count) {
    }

    @Schema(name = "TopSkill")
    public record TopSkill(String name, long count) {
    }
}

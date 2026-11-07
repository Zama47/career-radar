package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Most requested skills for the selected profession.
 */
@Schema(name = "AnalyticsSkills")
public record SkillsResponse(List<Item> items) {

    @Schema(name = "AnalyticsSkillItem")
    public record Item(
            @Schema(example = "Kotlin") String skill,
            @Schema(example = "80") long count,
            @Schema(description = "Share of matching vacancies that mention the skill, 0..1",
                    example = "0.66") double share) {
    }
}

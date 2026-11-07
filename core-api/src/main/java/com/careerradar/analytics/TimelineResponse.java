package com.careerradar.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Vacancy counts grouped into day, week or month buckets.
 */
@Schema(name = "AnalyticsTimeline")
public record TimelineResponse(List<Point> points) {

    @Schema(name = "AnalyticsTimelinePoint")
    public record Point(
            @Schema(description = "Bucket start date, ISO-8601", example = "2026-10-01") String date,
            @Schema(example = "12") long count) {
    }
}

package com.careerradar.resume.api;

import java.time.Instant;
import java.util.UUID;

public record ResumeSummaryResponse(
        UUID id,
        String title,
        boolean isMaster,
        boolean hasPdf,
        Instant createdAt,
        Instant updatedAt) {
}

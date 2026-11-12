package com.careerradar.resume.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ResumeDetailResponse(
        UUID id,
        String title,
        Map<String, Object> contentJson,
        String contentText,
        boolean isMaster,
        boolean hasPdf,
        Instant createdAt,
        Instant updatedAt) {
}

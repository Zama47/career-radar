package com.careerradar.ai;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CoverLetterResponse")
public record CoverLetterResponse(
        String subject,
        String text,
        String model,
        TokenUsage tokens,
        boolean cached) {
}

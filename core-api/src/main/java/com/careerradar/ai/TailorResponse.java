package com.careerradar.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(name = "TailorResumeResponse")
public record TailorResponse(
        Map<String, Object> contentJson,
        List<String> diff,
        List<String> warnings,
        String model,
        TokenUsage tokens,
        boolean cached) {
}

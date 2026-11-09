package com.careerradar.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "FitScoreResponse")
public record FitResponse(
        int score,
        String verdict,
        List<String> strengths,
        List<String> missing,
        List<String> reasons,
        String model,
        TokenUsage tokens,
        boolean cached) {
}

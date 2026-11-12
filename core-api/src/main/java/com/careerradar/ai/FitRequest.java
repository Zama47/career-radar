package com.careerradar.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "FitScoreRequest")
public record FitRequest(
        @Schema(description = "Public resume id") @NotNull UUID resumeId,
        @Schema(description = "Bypass the 7-day cache and call the model again")
        Boolean forceRefresh) {

    public boolean isForceRefresh() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}

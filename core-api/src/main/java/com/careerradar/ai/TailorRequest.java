package com.careerradar.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "TailorResumeRequest")
public record TailorRequest(
        @Schema(description = "Public vacancy id") @NotNull UUID vacancyId,
        @Schema(description = "Bypass the 7-day cache and call the model again")
        Boolean forceRefresh) {

    public boolean isForceRefresh() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}

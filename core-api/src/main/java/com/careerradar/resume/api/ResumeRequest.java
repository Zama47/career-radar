package com.careerradar.resume.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Create/update payload. {@code contentText} is mandatory: it is the plain-text projection
 * sent to the LLM and must not silently fall back to an empty prompt.
 */
@Schema(name = "ResumeRequest")
public record ResumeRequest(

        @Schema(example = "Android Developer — master")
        @NotBlank @Size(max = 255)
        String title,

        @Schema(description = "Structured resume; any JSON object is accepted")
        @NotNull
        Map<String, Object> contentJson,

        @Schema(description = "Plain-text resume used by the LLM")
        @NotBlank @Size(max = 200_000)
        String contentText,

        @Schema(description = "Marks this resume as the master one")
        Boolean isMaster,

        @Schema(description = "Optional PDF as base64 (data: prefix allowed)")
        @Size(max = 20_000_000)
        String pdfBase64) {
}

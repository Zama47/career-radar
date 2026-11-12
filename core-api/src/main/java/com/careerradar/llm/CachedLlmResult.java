package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A cache hit carries the structured payload plus the token counts of the original call.
 */
public record CachedLlmResult(
        JsonNode result,
        String model,
        int inputTokens,
        int outputTokens) {
}

package com.careerradar.llm;

/**
 * Normalized provider response. {@code model} is the model reported by the provider when
 * present, otherwise the configured one.
 */
public record LlmResponse(
        String text,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs) {
}

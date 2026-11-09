package com.careerradar.llm;

/**
 * One chat completion request. The prompt is assembled by the feature service, not here.
 */
public record LlmRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens) {
}

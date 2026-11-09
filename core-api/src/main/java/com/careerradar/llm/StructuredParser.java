package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converts a parsed JSON object into a typed payload. Throwing here marks the run as FAILED,
 * so schema violations are visible in {@code llm_runs} instead of being silently dropped.
 */
@FunctionalInterface
public interface StructuredParser<T> {

    T parse(JsonNode node);
}

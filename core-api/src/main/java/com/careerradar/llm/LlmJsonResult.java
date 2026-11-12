package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmJsonResult<T>(
        T value,
        LlmResponse response,
        JsonNode raw) {
}

package com.careerradar.llm;

/**
 * Thin abstraction over the provider so AI services can be tested with a mock and swapped
 * without touching prompt assembly.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);
}

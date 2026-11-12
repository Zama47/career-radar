package com.careerradar.llm;

import org.springframework.http.HttpStatus;

/**
 * Provider failure translated into an HTTP status for the Problem Details handler. Messages
 * are user-facing and never contain prompts, API keys or response bodies.
 */
public class LlmException extends RuntimeException {

    private final HttpStatus status;
    private final boolean retryable;
    private final long latencyMs;

    public LlmException(HttpStatus status, String message, boolean retryable, long latencyMs) {
        super(message);
        this.status = status;
        this.retryable = retryable;
        this.latencyMs = latencyMs;
    }

    public LlmException(HttpStatus status, String message) {
        this(status, message, false, 0);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}

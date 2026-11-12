package com.careerradar.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cached structured response, keyed by a hash of task + model + prompt version + inputs.
 */
@Entity
@Table(name = "llm_cache")
public class LlmCacheEntry {

    @Id
    @Column(name = "cache_key", length = 64)
    private String cacheKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private LlmTask task;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> responseJson;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LlmCacheEntry() {
    }

    public LlmCacheEntry(
            String cacheKey,
            LlmTask task,
            String model,
            String promptVersion,
            Map<String, Object> responseJson,
            int inputTokens,
            int outputTokens,
            Instant expiresAt) {
        this.cacheKey = cacheKey;
        this.task = task;
        this.model = model;
        this.promptVersion = promptVersion;
        this.responseJson = responseJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.expiresAt = expiresAt;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public LlmTask getTask() {
        return task;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Map<String, Object> getResponseJson() {
        return responseJson;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

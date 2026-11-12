package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short-lived cache for structured LLM answers. A caller can bypass it with
 * {@code forceRefresh} (the "regenerate" button in the UI).
 */
@Service
public class LlmCacheService {

    private final LlmCacheRepository repository;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public LlmCacheService(
            LlmCacheRepository repository, LlmProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<CachedLlmResult> get(String cacheKey) {
        LlmCacheEntry entry = repository.findById(cacheKey).orElse(null);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(entry);
            return Optional.empty();
        }
        JsonNode result = objectMapper.valueToTree(entry.getResponseJson());
        return Optional.of(new CachedLlmResult(
                result, entry.getModel(), entry.getInputTokens(), entry.getOutputTokens()));
    }

    @Transactional
    public void put(
            String cacheKey,
            LlmTask task,
            String model,
            String promptVersion,
            CachedLlmResult result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(result.result(), Map.class);
        Instant expiresAt = Instant.now().plus(
                Math.max(1, properties.getCacheTtlHours()), ChronoUnit.HOURS);
        repository.save(new LlmCacheEntry(
                cacheKey,
                task,
                model,
                promptVersion,
                payload,
                result.inputTokens(),
                result.outputTokens(),
                expiresAt));
    }

    public static String cacheKey(
            LlmTask task,
            String model,
            String promptVersion,
            String resumeHash,
            String vacancyHash) {
        String material = String.join(
                "|",
                task.name(),
                nullToEmpty(model),
                nullToEmpty(promptVersion),
                nullToEmpty(resumeHash),
                nullToEmpty(vacancyHash));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

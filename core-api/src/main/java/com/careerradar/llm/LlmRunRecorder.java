package com.careerradar.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists token usage in its own transaction, so a failed AI request still leaves an audit
 * row even when the surrounding business transaction rolls back.
 */
@Service
public class LlmRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmRunRecorder.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final LlmRunRepository repository;
    private final LlmProperties properties;

    public LlmRunRecorder(LlmRunRepository repository, LlmProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            LlmTask task, String model, String promptVersion, LlmResponse response) {
        LlmRun run = new LlmRun(task, response.model(), promptVersion);
        run.setInputTokens(response.inputTokens());
        run.setOutputTokens(response.outputTokens());
        run.setLatencyMs((int) response.latencyMs());
        run.setCostUsd(costUsd(response.model(), response.inputTokens(), response.outputTokens()));
        run.setStatus(LlmRunStatus.SUCCESS);
        repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            LlmTask task,
            String model,
            String promptVersion,
            LlmResponse response,
            String errorMessage) {
        LlmRun run = new LlmRun(task, response == null ? model : response.model(), promptVersion);
        if (response != null) {
            run.setInputTokens(response.inputTokens());
            run.setOutputTokens(response.outputTokens());
            run.setLatencyMs((int) response.latencyMs());
            run.setCostUsd(costUsd(response.model(), response.inputTokens(), response.outputTokens()));
        } else {
            run.setLatencyMs(0);
            run.setCostUsd(BigDecimal.ZERO);
        }
        run.setStatus(LlmRunStatus.FAILED);
        run.setErrorMessage(truncate(errorMessage));
        repository.save(run);
        log.warn("LLM task {} failed: {}", task, truncate(errorMessage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            LlmTask task, String model, String promptVersion, Throwable error, long latencyMs) {
        LlmRun run = new LlmRun(task, model, promptVersion);
        run.setLatencyMs((int) Math.max(0, latencyMs));
        run.setCostUsd(BigDecimal.ZERO);
        run.setStatus(LlmRunStatus.FAILED);
        run.setErrorMessage(truncate(error.getMessage()));
        repository.save(run);
        log.warn("LLM task {} failed: {}", task, truncate(error.getMessage()));
    }

    private BigDecimal costUsd(String model, int inputTokens, int outputTokens) {
        LlmProperties.ModelPrice price = properties.priceFor(model);
        BigDecimal inputCost = price.getInputPerMillion()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = price.getOutputPerMillion()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}

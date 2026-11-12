package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Executes one structured LLM call and records its usage. Parsing happens before the run is
 * marked successful, so invalid JSON produces a FAILED {@code llm_runs} row with real tokens.
 */
@Service
public class LlmInvoker {

    private final LlmClient client;
    private final LlmRunRecorder runRecorder;
    private final ObjectMapper objectMapper;

    public LlmInvoker(LlmClient client, LlmRunRecorder runRecorder, ObjectMapper objectMapper) {
        this.client = client;
        this.runRecorder = runRecorder;
        this.objectMapper = objectMapper;
    }

    public <T> LlmJsonResult<T> executeJson(
            LlmTask task,
            String model,
            String promptVersion,
            String systemPrompt,
            String userPrompt,
            int maxOutputTokens,
            StructuredParser<T> parser) {
        LlmResponse response;
        try {
            response = client.complete(
                    new LlmRequest(model, systemPrompt, userPrompt, maxOutputTokens));
        } catch (LlmException ex) {
            runRecorder.recordFailure(
                    task, model, promptVersion, ex, Math.max(0, ex.getLatencyMs()));
            throw ex;
        } catch (RuntimeException ex) {
            runRecorder.recordFailure(task, model, promptVersion, ex, 0);
            throw new LlmException(
                    HttpStatus.BAD_GATEWAY, "AI-сервис недоступен. Попробуйте позже.");
        }

        try {
            JsonNode node = LlmJson.parseObject(objectMapper, response.text());
            T value = parser.parse(node);
            runRecorder.recordSuccess(task, model, promptVersion, response);
            return new LlmJsonResult<>(value, response, node);
        } catch (LlmException ex) {
            runRecorder.recordFailure(task, model, promptVersion, response, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            runRecorder.recordFailure(
                    task, model, promptVersion, response, "Invalid structured response: " + ex);
            throw new LlmException(
                    HttpStatus.BAD_GATEWAY,
                    "LLM вернул некорректный ответ. Попробуйте перегенерировать.");
        }
    }
}

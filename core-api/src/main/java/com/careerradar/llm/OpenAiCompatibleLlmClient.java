package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI-compatible chat completions client ({@code POST {baseUrl}/chat/completions}).
 * Retries only 429, 5xx and timeouts; 4xx client errors are reported immediately.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String sessionId;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionId = properties.getSessionId() == null || properties.getSessionId().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : properties.getSessionId();
        if (properties.isConfigured()) {
            Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(timeout)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(timeout);
            this.restClient = RestClient.builder()
                    .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                    .requestFactory(factory)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (restClient == null) {
            throw new LlmException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LLM не настроен: задайте OPENCODE_GO_BASE_URL и OPENCODE_GO_API_KEY");
        }

        long started = System.nanoTime();
        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        LlmException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return call(request, started);
            } catch (LlmException ex) {
                last = ex;
                if (!ex.isRetryable() || attempt == attempts) {
                    throw ex;
                }
                log.warn("LLM call failed (attempt {}/{}), retrying: {}", attempt, attempts, ex.getMessage());
                sleep(400L * attempt);
            }
        }
        throw last == null
                ? new LlmException(HttpStatus.BAD_GATEWAY, "AI-сервис недоступен", true, elapsedMs(started))
                : last;
    }

    private LlmResponse call(LlmRequest request, long started) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        body.put("temperature", 0.2);
        body.put("max_tokens", request.maxOutputTokens());

        String raw;
        try {
            raw = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .header(HttpHeaders.USER_AGENT, "career-radar/0.1")
                    .header("x-opencode-session", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status == 429 || ex.getStatusCode().is5xxServerError();
            log.warn("LLM provider returned {}", status);
            throw new LlmException(
                    HttpStatus.BAD_GATEWAY,
                    "AI-сервис ответил ошибкой " + status,
                    retryable,
                    elapsedMs(started));
        } catch (ResourceAccessException ex) {
            log.warn("LLM provider is unreachable: {}", ex.getClass().getSimpleName());
            throw new LlmException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI-сервис не ответил за отведённое время",
                    true,
                    elapsedMs(started));
        }

        if (raw == null || raw.isBlank()) {
            throw new LlmException(
                    HttpStatus.BAD_GATEWAY, "AI-сервис вернул пустой ответ", false, elapsedMs(started));
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new LlmException(
                        HttpStatus.BAD_GATEWAY,
                        "AI-сервис вернул пустой ответ",
                        false,
                        elapsedMs(started));
            }
            JsonNode usage = root.path("usage");
            return new LlmResponse(
                    content.asText(),
                    root.path("model").asText(request.model()),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    elapsedMs(started));
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException(
                    HttpStatus.BAD_GATEWAY,
                    "AI-сервис вернул некорректный ответ",
                    false,
                    elapsedMs(started));
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

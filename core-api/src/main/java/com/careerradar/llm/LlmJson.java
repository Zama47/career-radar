package com.careerradar.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/**
 * Parses structured LLM output. Providers often wrap JSON in markdown fences despite the
 * prompt, so the surrounding text is stripped before parsing.
 */
public final class LlmJson {

    private LlmJson() {
    }

    public static JsonNode parseObject(ObjectMapper objectMapper, String text) {
        String candidate = stripFences(text);
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException(HttpStatus.BAD_GATEWAY, "LLM вернул ответ без JSON");
        }
        try {
            JsonNode node = objectMapper.readTree(candidate.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new LlmException(HttpStatus.BAD_GATEWAY, "LLM вернул не JSON-объект");
            }
            return node;
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException(HttpStatus.BAD_GATEWAY, "LLM вернул некорректный JSON");
        }
    }

    private static String stripFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String withoutOpening = trimmed.substring(firstNewline + 1);
        int closing = withoutOpening.lastIndexOf("```");
        return closing < 0 ? withoutOpening : withoutOpening.substring(0, closing);
    }
}

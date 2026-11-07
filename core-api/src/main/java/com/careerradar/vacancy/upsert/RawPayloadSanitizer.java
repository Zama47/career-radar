package com.careerradar.vacancy.upsert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Removes likely credential fields from collector payloads before storing {@code raw_json}.
 * The collector is trusted, but raw payloads are written to the database and must never
 * capture tokens or session data.
 */
@Component
public class RawPayloadSanitizer {

    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i).*(token|password|passwd|secret|authorization|cookie|api[-_]?key|session).*");

    public Map<String, Object> scrub(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> scrubbed = scrubMap(raw);
        return scrubbed.isEmpty() ? null : scrubbed;
    }

    private Map<String, Object> scrubMap(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!SECRET_KEY.matcher(String.valueOf(key)).matches()) {
                result.put(key, scrubValue(value));
            }
        });
        return result;
    }

    private Object scrubValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> result = new LinkedHashMap<>();
            nested.forEach((key, nestedValue) -> {
                if (!SECRET_KEY.matcher(String.valueOf(key)).matches()) {
                    result.put(String.valueOf(key), scrubValue(nestedValue));
                }
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::scrubValue).toList();
        }
        return value;
    }
}

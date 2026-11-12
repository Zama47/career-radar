package com.careerradar.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record TailorPayload(
        Map<String, Object> contentJson,
        List<String> diff,
        List<String> warnings) {

    TailorPayload {
        contentJson = contentJson == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(contentJson));
        diff = diff == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(diff));
        warnings = warnings == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(warnings));
        if (contentJson.isEmpty()) {
            throw new IllegalArgumentException("tailored contentJson is empty");
        }
    }
}

package com.careerradar.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parsed and validated fit-score payload. Validation failures are recorded as FAILED runs.
 */
record FitPayload(
        int score,
        String verdict,
        List<String> strengths,
        List<String> missing,
        List<String> reasons) {

    private static final Set<String> VERDICTS =
            Set.of("strong_match", "good_match", "partial_match", "weak_match");

    FitPayload {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (verdict == null || !VERDICTS.contains(verdict)) {
            throw new IllegalArgumentException("unknown verdict: " + verdict);
        }
        strengths = strengths == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(strengths));
        missing = missing == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(missing));
        reasons = reasons == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(reasons));
    }
}

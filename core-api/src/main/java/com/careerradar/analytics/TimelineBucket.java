package com.careerradar.analytics;

import com.careerradar.common.error.BadRequestException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Supported {@code date_trunc} buckets for the vacancy timeline.
 */
public enum TimelineBucket {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlValue;

    TimelineBucket(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String sqlValue() {
        return sqlValue;
    }

    public static TimelineBucket from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(bucket -> bucket.sqlValue.equals(normalized) || bucket.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "'bucket' must be one of: " + Arrays.stream(values())
                                .map(TimelineBucket::sqlValue)
                                .collect(Collectors.joining(", "))));
    }
}

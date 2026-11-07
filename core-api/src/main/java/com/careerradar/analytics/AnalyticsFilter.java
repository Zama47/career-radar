package com.careerradar.analytics;

import com.careerradar.common.error.BadRequestException;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Shared filter for market analytics. All fields are optional; empty values mean "no filter".
 * Dates are inclusive and interpreted as calendar days in UTC.
 */
public record AnalyticsFilter(
        LocalDate from,
        LocalDate to,
        String profession,
        Seniority seniority,
        RemoteType remote) {

    public AnalyticsFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        profession = profession == null || profession.isBlank()
                ? null
                : profession.trim().toUpperCase(Locale.ROOT);
    }

    public static AnalyticsFilter of(
            LocalDate from, LocalDate to, String profession, Seniority seniority, RemoteType remote) {
        return new AnalyticsFilter(from, to, profession, seniority, remote);
    }
}

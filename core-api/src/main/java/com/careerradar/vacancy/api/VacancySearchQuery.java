package com.careerradar.vacancy.api;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;

/**
 * Normalized public search criteria, independent of HTTP parameter names.
 */
public record VacancySearchQuery(
        int page,
        int size,
        String q,
        String professionCode,
        Seniority seniority,
        RemoteType remoteType) {
}

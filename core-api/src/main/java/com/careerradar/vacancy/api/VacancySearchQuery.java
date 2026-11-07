package com.careerradar.vacancy.api;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import java.math.BigDecimal;

/**
 * Normalized public search criteria, independent of HTTP parameter names.
 */
public record VacancySearchQuery(
        int page,
        int size,
        String q,
        String professionCode,
        Seniority seniority,
        RemoteType remoteType,
        BigDecimal salaryMin,
        VacancySort sort) {
}

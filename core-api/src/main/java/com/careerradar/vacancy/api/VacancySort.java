package com.careerradar.vacancy.api;

import com.careerradar.common.error.BadRequestException;
import java.util.Arrays;
import org.springframework.data.domain.Sort;

/**
 * Supported sort values of {@code GET /api/v1/vacancies}. Unknown values are rejected with
 * a 400 rather than silently falling back, so shared URLs behave predictably.
 */
public enum VacancySort {

    PUBLISHED_AT_DESC("publishedAt,desc", Sort.by(
            Sort.Order.desc("publishedAt").nullsLast(),
            Sort.Order.desc("lastSeenAt"),
            Sort.Order.desc("id"))),

    SALARY_FROM_DESC("salaryFrom,desc", Sort.by(
            Sort.Order.desc("salaryFrom").nullsLast(),
            Sort.Order.desc("publishedAt").nullsLast(),
            Sort.Order.desc("id"))),

    FIRST_SEEN_AT_DESC("firstSeenAt,desc", Sort.by(
            Sort.Order.desc("firstSeenAt"),
            Sort.Order.desc("id")));

    private final String apiValue;
    private final Sort sort;

    VacancySort(String apiValue, Sort sort) {
        this.apiValue = apiValue;
        this.sort = sort;
    }

    public String apiValue() {
        return apiValue;
    }

    public Sort sort() {
        return sort;
    }

    public static VacancySort fromApiValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return Arrays.stream(values())
                .filter(option -> option.apiValue.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unsupported sort '" + normalized + "'. Allowed values: "
                                + String.join(", ", Arrays.stream(values()).map(VacancySort::apiValue).toList())));
    }
}

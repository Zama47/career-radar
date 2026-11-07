package com.careerradar.analytics;

import com.careerradar.common.error.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for market analytics. Every method shares the same filter object and never
 * touches JPA entities, so aggregates stay a single SQL statement each.
 */
@Service
public class AnalyticsService {

    private static final int MAX_TOP = 20;
    private static final int MAX_SKILL_LIMIT = 100;

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(AnalyticsFilter filter, int top) {
        int limit = clamp(top, MAX_TOP, "'top'");
        AnalyticsRepository.OverviewRow row = analyticsRepository.overview(filter);
        List<AnalyticsOverviewResponse.TopProfession> professions =
                analyticsRepository.topProfessions(filter, limit);
        List<AnalyticsOverviewResponse.TopSkill> skills = analyticsRepository.topSkills(filter, limit);

        return new AnalyticsOverviewResponse(
                row.total(),
                row.newCount(),
                rubles(row.p50()),
                rubles(row.p25()),
                rubles(row.p75()),
                roundShare(row.remoteCount(), row.total()),
                row.currency(),
                professions,
                skills);
    }

    @Transactional(readOnly = true)
    public TimelineResponse timeline(AnalyticsFilter filter, TimelineBucket bucket) {
        return new TimelineResponse(analyticsRepository.timeline(filter, bucket));
    }

    @Transactional(readOnly = true)
    public SkillsResponse skills(AnalyticsFilter filter, int limit) {
        int effectiveLimit = clamp(limit, MAX_SKILL_LIMIT, "'limit'");
        long total = analyticsRepository.count(filter);
        List<SkillsResponse.Item> items = analyticsRepository.topSkills(filter, effectiveLimit).stream()
                .map(skill -> new SkillsResponse.Item(
                        skill.name(), skill.count(), roundShare(skill.count(), total)))
                .toList();
        return new SkillsResponse(items);
    }

    @Transactional(readOnly = true)
    public SalaryStatsResponse salaries(AnalyticsFilter filter) {
        AnalyticsRepository.SalaryRow row = analyticsRepository.salaries(filter);
        return new SalaryStatsResponse(
                rubles(row.p25()),
                rubles(row.p50()),
                rubles(row.p75()),
                row.currency(),
                row.sampleSize());
    }

    private static int clamp(int value, int max, String parameter) {
        if (value < 1 || value > max) {
            throw new BadRequestException(parameter + " must be between 1 and " + max);
        }
        return value;
    }

    private static Long rubles(Double value) {
        return value == null ? null : Math.round(value);
    }

    private static double roundShare(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) part / total * 10000d) / 10000d;
    }
}

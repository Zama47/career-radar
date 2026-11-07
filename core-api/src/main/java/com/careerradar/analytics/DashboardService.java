package com.careerradar.analytics;

import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.VacancyRepository;
import com.careerradar.vacancy.VacancyStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the dashboard KPI. All counts are limited to {@code ACTIVE} vacancies so
 * archived duplicates never inflate the market picture.
 */
@Service
public class DashboardService {

    private final VacancyRepository vacancyRepository;

    public DashboardService(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse snapshot() {
        long total = vacancyRepository.countByStatus(VacancyStatus.ACTIVE);
        long newToday = vacancyRepository.countByStatusAndFirstSeenAtGreaterThanEqual(
                VacancyStatus.ACTIVE, startOfTodayUtc());
        long internships = vacancyRepository.countByStatusAndSeniority(
                VacancyStatus.ACTIVE, Seniority.INTERN);
        long remote = vacancyRepository.countByStatusAndRemoteType(
                VacancyStatus.ACTIVE, RemoteType.REMOTE);

        List<DashboardResponse.TopProfession> topProfessions =
                vacancyRepository.findTopProfessions().stream()
                        .map(row -> new DashboardResponse.TopProfession(
                                row.getCode(), row.getVacancyCount()))
                        .toList();

        List<DashboardResponse.TopSkill> topSkills =
                vacancyRepository.findTopSkills().stream()
                        .map(row -> new DashboardResponse.TopSkill(
                                row.getSkillName(), row.getVacancyCount()))
                        .toList();

        return new DashboardResponse(
                total, newToday, internships, roundShare(remote, total), topProfessions, topSkills);
    }

    private static Instant startOfTodayUtc() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static double roundShare(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) part / total * 10000d) / 10000d;
    }
}

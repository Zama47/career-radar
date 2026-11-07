package com.careerradar.vacancy.api;

import com.careerradar.profession.Profession;
import com.careerradar.vacancy.Vacancy;
import com.careerradar.vacancy.VacancyRepository;
import com.careerradar.vacancy.VacancySource;
import com.careerradar.vacancy.VacancySourceRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for the public vacancy list. Keeps HTTP concerns out of the repository.
 */
@Service
public class VacancyQueryService {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("publishedAt").nullsLast(),
            Sort.Order.desc("lastSeenAt"),
            Sort.Order.desc("id"));

    private final VacancyRepository vacancyRepository;
    private final VacancySourceRepository vacancySourceRepository;

    public VacancyQueryService(
            VacancyRepository vacancyRepository,
            VacancySourceRepository vacancySourceRepository) {
        this.vacancyRepository = vacancyRepository;
        this.vacancySourceRepository = vacancySourceRepository;
    }

    @Transactional(readOnly = true)
    public VacancyPageResponse search(VacancySearchQuery query) {
        String pattern = query.q() == null ? null : "%" + query.q().toLowerCase(Locale.ROOT) + "%";
        String professionCode = query.professionCode() == null
                ? null
                : query.professionCode().toUpperCase(Locale.ROOT);

        Pageable pageable = PageRequest.of(query.page(), query.size(), DEFAULT_SORT);
        Page<Vacancy> page = vacancyRepository.search(
                pattern, professionCode, query.seniority(), query.remoteType(), pageable);

        Map<Long, String> urlByVacancyId = vacancySourceRepository
                .findByVacancyIdIn(page.getContent().stream().map(Vacancy::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        link -> link.getVacancy().getId(),
                        VacancySource::getUrl,
                        (first, second) -> first));

        List<VacancyListItem> items = page.getContent().stream()
                .map(vacancy -> toItem(vacancy, urlByVacancyId.get(vacancy.getId())))
                .toList();

        return new VacancyPageResponse(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static VacancyListItem toItem(Vacancy vacancy, String url) {
        Profession profession = vacancy.getProfession();
        return new VacancyListItem(
                vacancy.getPublicId(),
                vacancy.getTitle(),
                vacancy.getCompany() == null ? null : vacancy.getCompany().getName(),
                profession == null ? null : profession.getCode(),
                vacancy.getSeniority(),
                vacancy.getRemoteType(),
                vacancy.getLocation(),
                vacancy.getSalaryFrom(),
                vacancy.getSalaryTo(),
                vacancy.getCurrency(),
                vacancy.getSalaryGross(),
                vacancy.getSalaryPeriod(),
                vacancy.getPublishedAt(),
                vacancy.getFirstSeenAt(),
                vacancy.getLastSeenAt(),
                vacancy.getStatus(),
                url);
    }
}

package com.careerradar.vacancy.api;

import com.careerradar.common.error.NotFoundException;
import com.careerradar.profession.Profession;
import com.careerradar.skill.VacancySkill;
import com.careerradar.skill.VacancySkillRepository;
import com.careerradar.vacancy.Vacancy;
import com.careerradar.vacancy.VacancyRepository;
import com.careerradar.vacancy.VacancySource;
import com.careerradar.vacancy.VacancySourceRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for the public vacancy list and detail card. Keeps HTTP concerns out of the
 * repositories.
 */
@Service
public class VacancyQueryService {

    private final VacancyRepository vacancyRepository;
    private final VacancySourceRepository vacancySourceRepository;
    private final VacancySkillRepository vacancySkillRepository;

    public VacancyQueryService(
            VacancyRepository vacancyRepository,
            VacancySourceRepository vacancySourceRepository,
            VacancySkillRepository vacancySkillRepository) {
        this.vacancyRepository = vacancyRepository;
        this.vacancySourceRepository = vacancySourceRepository;
        this.vacancySkillRepository = vacancySkillRepository;
    }

    @Transactional(readOnly = true)
    public VacancyPageResponse search(VacancySearchQuery query) {
        String pattern = query.q() == null ? null : "%" + query.q().toLowerCase(Locale.ROOT) + "%";
        String professionCode = query.professionCode() == null
                ? null
                : query.professionCode().toUpperCase(Locale.ROOT);

        Pageable pageable = PageRequest.of(query.page(), query.size(), query.sort().sort());
        Page<Vacancy> page = vacancyRepository.search(
                pattern,
                professionCode,
                query.seniority(),
                query.remoteType(),
                query.salaryMin(),
                pageable);

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

    @Transactional(readOnly = true)
    public VacancyDetailResponse get(UUID publicId) {
        Vacancy vacancy = vacancyRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new NotFoundException("Vacancy " + publicId + " not found"));

        VacancySource source = vacancySourceRepository
                .findByVacancyIdOrderByIdAsc(vacancy.getId())
                .stream()
                .findFirst()
                .orElse(null);

        List<VacancyDetailResponse.SkillItem> skills = vacancySkillRepository
                .findByVacancyIdWithSkill(vacancy.getId())
                .stream()
                .map(VacancyQueryService::toSkillItem)
                .toList();

        Profession profession = vacancy.getProfession();
        return new VacancyDetailResponse(
                vacancy.getPublicId(),
                vacancy.getTitle(),
                vacancy.getDescription(),
                vacancy.getCompany() == null ? null : vacancy.getCompany().getName(),
                vacancy.getCompany() == null ? null : vacancy.getCompany().getWebsite(),
                profession == null ? null : profession.getCode(),
                profession == null ? null : profession.getName(),
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
                source == null ? null : source.getUrl(),
                source == null ? null : source.getSource().getCode(),
                source == null ? null : source.getSource().getName(),
                skills);
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

    private static VacancyDetailResponse.SkillItem toSkillItem(VacancySkill vacancySkill) {
        return new VacancyDetailResponse.SkillItem(
                vacancySkill.getSkill().getCode(),
                vacancySkill.getSkill().getName(),
                vacancySkill.getConfidence(),
                vacancySkill.getSource());
    }
}

package com.careerradar.vacancy.upsert;

import com.careerradar.common.error.NotFoundException;
import com.careerradar.company.Company;
import com.careerradar.company.CompanyNameNormalizer;
import com.careerradar.company.CompanyRepository;
import com.careerradar.source.Source;
import com.careerradar.source.SourceRepository;
import com.careerradar.vacancy.ContentHash;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.Vacancy;
import com.careerradar.vacancy.VacancyRepository;
import com.careerradar.vacancy.VacancySource;
import com.careerradar.vacancy.VacancySourceRepository;
import com.careerradar.vacancy.VacancyStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deduplication and persistence policy for collector batches.
 *
 * <ul>
 *   <li>Known {@code (source, externalId)} -> refresh the vacancy, count as {@code updated}.</li>
 *   <li>New item whose content hash matches an existing vacancy -> attach a
 *       {@code vacancy_sources} row to that vacancy, count as {@code skipped}.</li>
 *   <li>Otherwise -> create company, vacancy and source link, count as {@code created}.</li>
 * </ul>
 *
 * The whole batch shares one transaction, so a partially applied batch is never persisted.
 */
@Service
public class VacancyUpsertService {

    private static final Logger log = LoggerFactory.getLogger(VacancyUpsertService.class);

    private final SourceRepository sourceRepository;
    private final CompanyRepository companyRepository;
    private final VacancyRepository vacancyRepository;
    private final VacancySourceRepository vacancySourceRepository;
    private final CompanyNameNormalizer companyNameNormalizer;
    private final ContentHash contentHash;
    private final RawPayloadSanitizer rawPayloadSanitizer;

    public VacancyUpsertService(
            SourceRepository sourceRepository,
            CompanyRepository companyRepository,
            VacancyRepository vacancyRepository,
            VacancySourceRepository vacancySourceRepository,
            CompanyNameNormalizer companyNameNormalizer,
            ContentHash contentHash,
            RawPayloadSanitizer rawPayloadSanitizer) {
        this.sourceRepository = sourceRepository;
        this.companyRepository = companyRepository;
        this.vacancyRepository = vacancyRepository;
        this.vacancySourceRepository = vacancySourceRepository;
        this.companyNameNormalizer = companyNameNormalizer;
        this.contentHash = contentHash;
        this.rawPayloadSanitizer = rawPayloadSanitizer;
    }

    @Transactional
    public VacancyUpsertResponse upsert(VacancyUpsertRequest request) {
        Source source = sourceRepository.findByCodeIgnoreCase(request.source())
                .orElseThrow(() -> new NotFoundException("Unknown vacancy source: " + request.source()));

        int created = 0;
        int updated = 0;
        int skipped = 0;
        Map<String, Company> companyCache = new HashMap<>();

        for (VacancyDraftRequest item : request.items()) {
            UpsertOutcome outcome = upsertItem(source, item, companyCache);
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
            }
        }

        log.info("Vacancy batch processed: source={} received={} created={} updated={} skipped={}",
                source.getCode(), request.items().size(), created, updated, skipped);
        return new VacancyUpsertResponse(request.items().size(), created, updated, skipped);
    }

    private UpsertOutcome upsertItem(
            Source source, VacancyDraftRequest item, Map<String, Company> companyCache) {
        Instant now = Instant.now();
        String externalId = item.externalId().trim();

        Optional<VacancySource> existingLink =
                vacancySourceRepository.findBySourceIdAndExternalId(source.getId(), externalId);
        if (existingLink.isPresent()) {
            VacancySource link = existingLink.get();
            applyDraft(link.getVacancy(), item, now);
            link.setUrl(item.url().trim());
            link.setRawJson(rawPayloadSanitizer.scrub(item.raw()));
            return UpsertOutcome.UPDATED;
        }

        String hash = contentHash.compute(
                item.title(),
                companyNameNormalizer.normalize(item.companyName()),
                item.location(),
                item.salaryFrom(),
                item.salaryTo(),
                item.currency());

        Optional<Vacancy> duplicate = vacancyRepository.findFirstByContentHash(hash);
        if (duplicate.isPresent()) {
            Vacancy vacancy = duplicate.get();
            vacancy.setLastSeenAt(now);
            vacancy.setStatus(VacancyStatus.ACTIVE);
            saveLink(vacancy, source, item);
            return UpsertOutcome.SKIPPED;
        }

        Vacancy vacancy = new Vacancy(item.title().trim());
        vacancy.setCompany(resolveCompany(item.companyName(), companyCache));
        vacancy.setSeniority(item.seniority() == null ? Seniority.UNKNOWN : item.seniority());
        vacancy.setFirstSeenAt(now);
        vacancy.setPublishedAt(item.publishedAt() == null ? now : item.publishedAt());
        vacancy.setRawJson(rawPayloadSanitizer.scrub(item.raw()));
        applyDraft(vacancy, item, now);

        vacancyRepository.save(vacancy);
        saveLink(vacancy, source, item);
        return UpsertOutcome.CREATED;
    }

    private void applyDraft(Vacancy vacancy, VacancyDraftRequest item, Instant now) {
        if (item.description() != null) {
            vacancy.setDescription(item.description().trim());
        }
        if (item.location() != null) {
            vacancy.setLocation(item.location().trim());
        }
        if (item.salaryFrom() != null) {
            vacancy.setSalaryFrom(item.salaryFrom());
        }
        if (item.salaryTo() != null) {
            vacancy.setSalaryTo(item.salaryTo());
        }
        if (item.currency() != null) {
            vacancy.setCurrency(item.currency().trim().toUpperCase(Locale.ROOT));
        }
        if (Boolean.TRUE.equals(item.remote())) {
            vacancy.setRemoteType(RemoteType.REMOTE);
        }
        if (item.seniority() != null) {
            vacancy.setSeniority(item.seniority());
        }
        if (item.publishedAt() != null) {
            vacancy.setPublishedAt(item.publishedAt());
        }
        vacancy.setLastSeenAt(now);
        vacancy.setStatus(VacancyStatus.ACTIVE);
        vacancy.setContentHash(computeHash(vacancy));
    }

    private String computeHash(Vacancy vacancy) {
        Company company = vacancy.getCompany();
        return contentHash.compute(
                vacancy.getTitle(),
                company == null ? null : company.getNormalizedName(),
                vacancy.getLocation(),
                vacancy.getSalaryFrom(),
                vacancy.getSalaryTo(),
                vacancy.getCurrency());
    }

    private Company resolveCompany(String rawName, Map<String, Company> cache) {
        String normalized = companyNameNormalizer.normalize(rawName);
        if (normalized.isEmpty()) {
            return null;
        }
        return cache.computeIfAbsent(normalized, key -> companyRepository.findByNormalizedName(key)
                .orElseGet(() -> companyRepository.save(new Company(rawName.trim(), key))));
    }

    private void saveLink(Vacancy vacancy, Source source, VacancyDraftRequest item) {
        VacancySource link = new VacancySource(
                vacancy, source, item.externalId().trim(), item.url().trim());
        link.setRawJson(rawPayloadSanitizer.scrub(item.raw()));
        vacancySourceRepository.save(link);
    }

    private enum UpsertOutcome {
        CREATED,
        UPDATED,
        SKIPPED
    }
}

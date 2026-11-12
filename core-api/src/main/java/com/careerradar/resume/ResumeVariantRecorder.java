package com.careerradar.resume;

import com.careerradar.vacancy.VacancyRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists AI results as resume variants. Kept in a short transaction, separate from the
 * (potentially 60-second) LLM call.
 */
@Service
public class ResumeVariantRecorder {

    private final ResumeVariantRepository variants;
    private final ResumeRepository resumes;
    private final VacancyRepository vacancies;

    public ResumeVariantRecorder(
            ResumeVariantRepository variants,
            ResumeRepository resumes,
            VacancyRepository vacancies) {
        this.variants = variants;
        this.resumes = resumes;
        this.vacancies = vacancies;
    }

    @Transactional
    public void recordFit(
            Long resumeId, Long vacancyId, int score, Map<String, Object> contentJson) {
        ResumeVariant variant = newVariant(resumeId, vacancyId);
        variant.setFitScore(score);
        variant.setContentJson(contentJson);
        variants.save(variant);
    }

    @Transactional
    public void recordCoverLetter(
            Long resumeId,
            Long vacancyId,
            String coverLetter,
            Map<String, Object> contentJson) {
        ResumeVariant variant = newVariant(resumeId, vacancyId);
        variant.setCoverLetter(coverLetter);
        variant.setContentJson(contentJson);
        variants.save(variant);
    }

    @Transactional
    public void recordTailor(
            Long resumeId, Long vacancyId, Map<String, Object> contentJson) {
        ResumeVariant variant = newVariant(resumeId, vacancyId);
        variant.setContentJson(contentJson);
        variants.save(variant);
    }

    private ResumeVariant newVariant(Long resumeId, Long vacancyId) {
        Resume resume = resumes.getReferenceById(resumeId);
        var vacancy = vacancyId == null ? null : vacancies.getReferenceById(vacancyId);
        return new ResumeVariant(resume, vacancy);
    }
}

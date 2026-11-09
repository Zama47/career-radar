package com.careerradar.resume;

import com.careerradar.vacancy.Vacancy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Result of one AI scenario for a resume: fit score, cover letter or tailored content.
 */
@Entity
@Table(name = "resume_variants")
public class ResumeVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacancy_id")
    private Vacancy vacancy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb")
    private Map<String, Object> contentJson;

    @Column(name = "cover_letter", columnDefinition = "text")
    private String coverLetter;

    @Column(name = "fit_score")
    private Integer fitScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ResumeVariant() {
    }

    public ResumeVariant(Resume resume, Vacancy vacancy) {
        this.resume = resume;
        this.vacancy = vacancy;
    }

    public Long getId() {
        return id;
    }

    public Resume getResume() {
        return resume;
    }

    public Vacancy getVacancy() {
        return vacancy;
    }

    public Map<String, Object> getContentJson() {
        return contentJson;
    }

    public void setContentJson(Map<String, Object> contentJson) {
        this.contentJson = contentJson;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public Integer getFitScore() {
        return fitScore;
    }

    public void setFitScore(Integer fitScore) {
        this.fitScore = fitScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

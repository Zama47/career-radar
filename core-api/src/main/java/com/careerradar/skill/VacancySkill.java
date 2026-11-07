package com.careerradar.skill;

import com.careerradar.vacancy.Vacancy;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Link between a vacancy and a detected skill.
 */
@Entity
@Table(name = "vacancy_skills")
public class VacancySkill {

    @EmbeddedId
    private VacancySkillId id = new VacancySkillId();

    @MapsId("vacancyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacancy_id", nullable = false)
    private Vacancy vacancy;

    @MapsId("skillId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SkillSource source = SkillSource.RULE;

    protected VacancySkill() {
    }

    public VacancySkill(Vacancy vacancy, Skill skill) {
        this.vacancy = vacancy;
        this.skill = skill;
    }

    public VacancySkillId getId() {
        return id;
    }

    public Vacancy getVacancy() {
        return vacancy;
    }

    public void setVacancy(Vacancy vacancy) {
        this.vacancy = vacancy;
    }

    public Skill getSkill() {
        return skill;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public SkillSource getSource() {
        return source;
    }

    public void setSource(SkillSource source) {
        this.source = source;
    }
}

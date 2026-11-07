package com.careerradar.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@code vacancy_skills}.
 */
@Embeddable
public class VacancySkillId implements Serializable {

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    public VacancySkillId() {
    }

    public VacancySkillId(Long vacancyId, Long skillId) {
        this.vacancyId = vacancyId;
        this.skillId = skillId;
    }

    public Long getVacancyId() {
        return vacancyId;
    }

    public Long getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VacancySkillId that)) {
            return false;
        }
        return Objects.equals(vacancyId, that.vacancyId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vacancyId, skillId);
    }
}

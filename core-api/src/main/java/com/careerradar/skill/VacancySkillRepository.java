package com.careerradar.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancySkillRepository extends JpaRepository<VacancySkill, VacancySkillId> {

    @Query("""
            select vs from VacancySkill vs
            join fetch vs.skill
            where vs.vacancy.id = :vacancyId
            order by vs.skill.name
            """)
    List<VacancySkill> findByVacancyIdWithSkill(@Param("vacancyId") Long vacancyId);
}

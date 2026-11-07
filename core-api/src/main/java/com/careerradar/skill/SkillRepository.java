package com.careerradar.skill;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByCodeIgnoreCase(String code);

    List<Skill> findAllByOrderByNameAsc();
}

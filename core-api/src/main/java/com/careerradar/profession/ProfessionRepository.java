package com.careerradar.profession;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionRepository extends JpaRepository<Profession, Long> {

    Optional<Profession> findByCodeIgnoreCase(String code);

    List<Profession> findAllByOrderByNameAsc();
}

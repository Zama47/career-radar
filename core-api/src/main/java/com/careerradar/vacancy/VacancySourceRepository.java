package com.careerradar.vacancy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancySourceRepository extends JpaRepository<VacancySource, Long> {

    Optional<VacancySource> findBySourceIdAndExternalId(Long sourceId, String externalId);

    List<VacancySource> findByVacancyIdIn(Collection<Long> vacancyIds);

    @EntityGraph(attributePaths = "source")
    List<VacancySource> findByVacancyIdOrderByIdAsc(Long vacancyId);
}

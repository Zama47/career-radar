package com.careerradar.source;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRunRepository extends JpaRepository<SourceRun, Long> {

    Optional<SourceRun> findFirstBySourceIdOrderByStartedAtDesc(Long sourceId);
}

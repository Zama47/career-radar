package com.careerradar.resume;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVariantRepository extends JpaRepository<ResumeVariant, Long> {
}

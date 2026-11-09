package com.careerradar.llm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCacheRepository extends JpaRepository<LlmCacheEntry, String> {
}

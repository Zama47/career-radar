package com.careerradar.vacancy;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    Optional<Vacancy> findFirstByContentHash(String contentHash);

    /**
     * Public search with optional filters. The profession join is explicit so vacancies without
     * a profession still match when no profession filter is supplied.
     */
    @Query(value = """
            select v from Vacancy v
            left join fetch v.company
            left join fetch v.profession p
            where (:q is null or lower(v.title) like :q or lower(coalesce(v.description, '')) like :q)
              and (:profession is null or p.code = :profession)
              and (:seniority is null or v.seniority = :seniority)
              and (:remoteType is null or v.remoteType = :remoteType)
            """,
            countQuery = """
            select count(v) from Vacancy v
            left join v.profession p
            where (:q is null or lower(v.title) like :q or lower(coalesce(v.description, '')) like :q)
              and (:profession is null or p.code = :profession)
              and (:seniority is null or v.seniority = :seniority)
              and (:remoteType is null or v.remoteType = :remoteType)
            """)
    Page<Vacancy> search(
            @Param("q") String q,
            @Param("profession") String profession,
            @Param("seniority") Seniority seniority,
            @Param("remoteType") RemoteType remoteType,
            Pageable pageable);
}

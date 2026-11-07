package com.careerradar.vacancy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            left join fetch v.company c
            left join fetch v.profession p
            where (:q is null
                   or lower(v.title) like :q
                   or lower(coalesce(v.description, '')) like :q
                   or lower(coalesce(c.name, '')) like :q)
              and (:profession is null or p.code = :profession)
              and (:seniority is null or v.seniority = :seniority)
              and (:remoteType is null or v.remoteType = :remoteType)
              and (:salaryMin is null
                   or coalesce(v.salaryTo, v.salaryFrom) >= :salaryMin)
            """,
            countQuery = """
            select count(v) from Vacancy v
            left join v.company c
            left join v.profession p
            where (:q is null
                   or lower(v.title) like :q
                   or lower(coalesce(v.description, '')) like :q
                   or lower(coalesce(c.name, '')) like :q)
              and (:profession is null or p.code = :profession)
              and (:seniority is null or v.seniority = :seniority)
              and (:remoteType is null or v.remoteType = :remoteType)
              and (:salaryMin is null
                   or coalesce(v.salaryTo, v.salaryFrom) >= :salaryMin)
            """)
    Page<Vacancy> search(
            @Param("q") String q,
            @Param("profession") String profession,
            @Param("seniority") Seniority seniority,
            @Param("remoteType") RemoteType remoteType,
            @Param("salaryMin") BigDecimal salaryMin,
            Pageable pageable);

    @Query("""
            select v from Vacancy v
            left join fetch v.company
            left join fetch v.profession
            where v.publicId = :publicId
            """)
    Optional<Vacancy> findByPublicIdWithDetails(@Param("publicId") UUID publicId);

    long countByStatus(VacancyStatus status);

    long countByStatusAndSeniority(VacancyStatus status, Seniority seniority);

    long countByStatusAndRemoteType(VacancyStatus status, RemoteType remoteType);

    long countByStatusAndFirstSeenAtGreaterThanEqual(
            VacancyStatus status, Instant firstSeenAt);

    @Query(value = """
            select p.code       as code,
                   count(*)     as vacancy_count
              from vacancies v
              join professions p on p.id = v.profession_id
             where v.status = 'ACTIVE'
             group by p.code
             order by count(*) desc, p.code
             limit 5
            """, nativeQuery = true)
    List<ProfessionCount> findTopProfessions();

    @Query(value = """
            select s.name    as skill_name,
                   count(*)  as vacancy_count
              from vacancy_skills vs
              join skills s on s.id = vs.skill_id
              join vacancies v on v.id = vs.vacancy_id
             where v.status = 'ACTIVE'
             group by s.name
             order by count(*) desc, s.name
             limit 5
            """, nativeQuery = true)
    List<SkillCount> findTopSkills();

    interface ProfessionCount {
        String getCode();

        long getVacancyCount();
    }

    interface SkillCount {
        String getSkillName();

        long getVacancyCount();
    }
}

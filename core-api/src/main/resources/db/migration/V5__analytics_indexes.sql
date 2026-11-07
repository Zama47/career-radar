-- Stage 4: supporting indexes for market analytics.
-- Aggregates always filter status = 'ACTIVE', so partial indexes stay small.

-- "Market date" used by the timeline and period filters.
create index idx_vacancies_active_market_date
    on vacancies ((coalesce(published_at, first_seen_at)))
    where status = 'ACTIVE';

-- Percentile queries order by coalesce(salary_from, salary_to).
create index idx_vacancies_active_salary
    on vacancies ((coalesce(salary_from, salary_to)))
    where status = 'ACTIVE' and coalesce(salary_from, salary_to) is not null;

-- Top-skills aggregation joins vacancy_skills by skill first.
create index idx_vacancy_skills_skill_vacancy on vacancy_skills (skill_id, vacancy_id);

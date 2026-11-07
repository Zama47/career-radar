-- Stage 1: core domain.
-- V1__baseline.sql only establishes the Flyway history table, so business tables start here.

create table sources
(
    id         bigserial primary key,
    code       varchar(32)  not null unique,
    name       varchar(255) not null,
    base_url   text,
    enabled    boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table source_runs
(
    id            bigserial primary key,
    source_id     bigint      not null references sources (id),
    started_at    timestamptz not null default now(),
    finished_at   timestamptz,
    status        varchar(16) not null default 'RUNNING',
    items_found   integer     not null default 0,
    items_created integer     not null default 0,
    items_updated integer     not null default 0,
    items_skipped integer     not null default 0,
    error_message text,
    constraint chk_source_runs_status check (status in ('RUNNING', 'SUCCESS', 'FAILED'))
);

create index idx_source_runs_source_started on source_runs (source_id, started_at desc);

create table companies
(
    id              bigserial primary key,
    name            varchar(255) not null,
    normalized_name varchar(255) not null unique,
    website         text,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create table professions
(
    id        bigserial primary key,
    code      varchar(64)  not null unique,
    name      varchar(255) not null,
    parent_id bigint references professions (id)
);

create table vacancies
(
    id             bigserial primary key,
    public_id      uuid         not null unique default gen_random_uuid(),
    title          varchar(500) not null,
    description    text,
    company_id     bigint references companies (id),
    profession_id  bigint references professions (id),
    seniority      varchar(16)  not null default 'UNKNOWN',
    remote_type    varchar(16)  not null default 'UNKNOWN',
    location       varchar(255),
    salary_from    numeric(12, 2),
    salary_to      numeric(12, 2),
    currency       varchar(8),
    salary_gross   boolean,
    salary_period  varchar(16)  not null default 'UNKNOWN',
    published_at   timestamptz,
    first_seen_at  timestamptz  not null default now(),
    last_seen_at   timestamptz  not null default now(),
    status         varchar(16)  not null default 'ACTIVE',
    content_hash   varchar(64)  not null,
    raw_json       jsonb,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint chk_vacancies_seniority check (seniority in ('INTERN', 'JUNIOR', 'MIDDLE', 'SENIOR', 'LEAD', 'UNKNOWN')),
    constraint chk_vacancies_remote_type check (remote_type in ('OFFICE', 'HYBRID', 'REMOTE', 'UNKNOWN')),
    constraint chk_vacancies_salary_period check (salary_period in ('MONTH', 'YEAR', 'DAY', 'HOUR', 'UNKNOWN')),
    constraint chk_vacancies_status check (status in ('ACTIVE', 'ARCHIVED', 'DUPLICATE'))
);

create index idx_vacancies_content_hash on vacancies (content_hash);
create index idx_vacancies_profession_published on vacancies (profession_id, published_at desc);
create index idx_vacancies_seniority_remote on vacancies (seniority, remote_type);

create table vacancy_sources
(
    id          bigserial primary key,
    vacancy_id  bigint      not null references vacancies (id) on delete cascade,
    source_id   bigint      not null references sources (id),
    external_id varchar(255) not null,
    url         text         not null,
    raw_json    jsonb,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uq_vacancy_sources_source_external unique (source_id, external_id)
);

create index idx_vacancy_sources_vacancy on vacancy_sources (vacancy_id);

create table skills
(
    id       bigserial primary key,
    code     varchar(64)  not null unique,
    name     varchar(255) not null,
    aliases  text[]       not null default '{}',
    category varchar(64)
);

create table vacancy_skills
(
    vacancy_id bigint not null references vacancies (id) on delete cascade,
    skill_id   bigint not null references skills (id) on delete cascade,
    confidence numeric(3, 2) not null default 1.0,
    source     varchar(16)   not null default 'RULE',
    primary key (vacancy_id, skill_id),
    constraint chk_vacancy_skills_source check (source in ('RULE', 'LLM', 'MANUAL')),
    constraint chk_vacancy_skills_confidence check (confidence >= 0 and confidence <= 1)
);

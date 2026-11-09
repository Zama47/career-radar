-- Stage 5: resumes, resume variants, LLM runs and a short-lived response cache.

create table resumes
(
    id           bigserial primary key,
    public_id    uuid         not null unique default gen_random_uuid(),
    title        varchar(255) not null,
    content_json jsonb        not null,
    content_text text         not null,
    pdf_path     text,
    is_master    boolean      not null default false,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

-- At most one master resume. The service demotes the previous one before saving.
create unique index uq_resumes_single_master on resumes (is_master) where is_master;
create index idx_resumes_updated on resumes (updated_at desc);

create table llm_runs
(
    id             bigserial primary key,
    task           varchar(64)   not null,
    model          varchar(128)  not null,
    prompt_version varchar(64),
    input_tokens   integer,
    output_tokens  integer,
    cost_usd       numeric(12, 6),
    latency_ms     integer,
    status         varchar(16)   not null,
    error_message  text,
    created_at     timestamptz   not null default now(),
    constraint chk_llm_runs_status check (status in ('SUCCESS', 'FAILED'))
);

create index idx_llm_runs_created on llm_runs (created_at desc);
create index idx_llm_runs_task_created on llm_runs (task, created_at desc);

create table resume_variants
(
    id           bigserial primary key,
    resume_id    bigint      not null references resumes (id) on delete cascade,
    vacancy_id   bigint references vacancies (id) on delete set null,
    content_json jsonb,
    cover_letter text,
    fit_score    integer,
    created_at   timestamptz not null default now(),
    constraint chk_resume_variants_fit_score
        check (fit_score is null or (fit_score between 0 and 100))
);

create index idx_resume_variants_resume on resume_variants (resume_id, created_at desc);
create index idx_resume_variants_vacancy on resume_variants (vacancy_id);

-- Cached structured LLM responses. Key = sha256(task + model + prompt_version + hashes).
create table llm_cache
(
    cache_key      varchar(64) primary key,
    task           varchar(64)  not null,
    model          varchar(128) not null,
    prompt_version varchar(64)  not null,
    response_json  jsonb        not null,
    input_tokens   integer      not null default 0,
    output_tokens  integer      not null default 0,
    created_at     timestamptz  not null default now(),
    expires_at     timestamptz  not null
);

create index idx_llm_cache_expires on llm_cache (expires_at);

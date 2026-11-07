# Architecture

Career Radar is a single-user tool. The design keeps one owner of the database and one
place where LLM and email credentials live.

```text
web (Next.js) ──public API──▶ core-api (Spring Boot) ──▶ PostgreSQL 16
                                   ▲
                                   │ POST /internal/v1/vacancies:upsert
                                   │ X-Internal-Token
                             collector (Python)
```

## Services and boundaries

| Service | Responsibility | Must not |
|---|---|---|
| `web` | Read-only UI, forms for interactive flows, calls core-api only | Talk to PostgreSQL, hold secrets, call LLM providers |
| `core-api` | Public + internal APIs, validation, deduplication, persistence, LLM calls, email | Bypass Flyway for schema changes |
| `collector` | Fetch listings from sources, normalize to `VacancyDraft`, report `source_runs` | Write to the database, bypass captchas/auth/ToS |
| `postgres` | Single source of truth | — |
| `redis` | Reserved for later caching/queues | Required for Stage 0 |

## Request flows

1. **Read path (web):** browser → Next.js server component → `lib/api.ts` →
   `core-api /api/v1/*` → PostgreSQL → JSON.
2. **Ingestion path (collector):** company adapter `fetch(query)` checks `robots.txt` for
   the origin, sends at most one request per second per host, obtains raw listings (JSON or
   SSR HTML) → `normalize()` → `VacancyDraft` list → `core-api
   /internal/v1/vacancies:upsert` in chunks of 50 with `X-Internal-Token` → validate →
   deduplicate → persist. The collector classifies seniority, work format and profession
   from the listing text (rule-based, deterministic) and lets core-api own deduplication and
   storage. The collector never opens a database connection and never bypasses a
   403/captcha: it stops and reports the source as failed.
3. **LLM path (later):** core-api builds a prompt from stored data → OpenAI-compatible
   provider (OpenCode Go) → structured JSON validated by Bean Validation → `llm_runs`
   audit row. Web only receives the result through the public API.

## Company source adapters

```text
career site ──HTTP (robots.txt, ≤1 rps)──▶ adapter.fetch() ──▶ RawVacancy
                                                       │
                                              adapter.normalize()
                                                       ▼
                                                  VacancyDraft ──▶ core-api
```

| Source | Transport | Pagination | Notes |
|---|---|---|---|
| `MTS` | JSON `/api/v2/vacancies` | `pagination[page]` | API returns 25 items/page and shuffles order; deduplicate by `id` |
| `YANDEX` | JSON `/jobs/api/jobs/publications/` | `next` cursor | public endpoint used by the site; no auth |
| `VK` | SSR HTML `/vacancy/` | first page only | `/load_more/` is disallowed by `robots.txt`; do not imitate it |

Rules every adapter follows:

- read `robots.txt` once per origin and stop if the path is disallowed;
- use `COLLECTOR_USER_AGENT`, never impersonate a browser;
- retry only `429`/`5xx`, honour `Retry-After`, and never retry a `403`/captcha;
- keep `raw_json` for debugging but never log titles, descriptions, tokens or emails;
- add a fixture + offline test for every new adapter.

## Configuration

All configuration comes from environment variables (see `infra/.env.example`):
`POSTGRES_*`, `CORE_API_PORT`, `INTERNAL_TOKEN`, `OPENCODE_GO_BASE_URL`,
`OPENCODE_GO_API_KEY`, `COLLECTOR_USER_AGENT`. The collector also reads `CORE_API_URL`,
`MTS_BASE_URL`, `YANDEX_BASE_URL`, `VK_BASE_URL`, `REQUEST_TIMEOUT`,
`RATE_LIMIT_SECONDS`; the legacy hh.ru adapter additionally reads `HH_BASE_URL`,
`HH_AREA`, `HH_PER_PAGE`, `HH_ACCESS_TOKEN` (optional). Web reads `NEXT_PUBLIC_API_URL`.

## Data and migrations

- Schema changes only through Flyway migrations in
  `core-api/src/main/resources/db/migration`. `V1__baseline.sql` establishes the
  history table; `V2__core_domain.sql` adds business tables; `V3__seed_dictionaries.sql`
  seeds professions, skills and pilot sources; `V4__source_catalog.sql` adds the company
  career-site dictionary used by stage 2.
- Hibernate runs with `ddl-auto: validate`: entities must match migrations, never
  create tables at runtime.
- Deduplication keys and normalization rules are owned by core-api, so all sources
  share one policy: `(source_id, external_id)` identifies an occurrence, and a SHA-256
  content hash over normalized title/company/location/salary prevents cross-source
  duplicates. List endpoints read `vacancy_sources` for the original URL.
- Raw collector payloads are stored in `jsonb` only after credential-like keys
  (`token`, `password`, `secret`, `authorization`, `cookie`, `api_key`, `session`)
  are stripped.

## Error handling and observability

- Public errors use RFC 7807 Problem Details.
- `/actuator/health` exposes liveness/readiness probes and database health.
- Source failures are logged and reported through `source_runs` (later stage).
- Logs must not contain email bodies, tokens, or full résumé text.

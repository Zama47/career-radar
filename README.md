# Career Radar

Личная операционная система поиска работы: сбор вакансий, аналитика рынка, fit-score резюме,
application tracker и read-only почта. Один пользователь, без биллинга и мультитенантности.

**Текущий этап — Stage 5 (резюме и LLM):** мастер-резюме и варианты, fit-score,
черновик сопроводительного письма, адаптация резюме под вакансию и журнал `llm_runs`
с токенами, задержкой и стоимостью. API — `/api/v1/resumes/*` и AI-эндпоинты,
web — страница `/resume` и панель проверки на карточке вакансии. Почта и
application tracker — в следующих этапах.

## Стек и зафиксированные версии

| Слой | Версия |
|---|---|
| Java / Gradle | Java 21 (toolchain), Gradle 8.14.3 |
| Spring Boot | 3.5.16 (последний стабильный 3.x) |
| springdoc-openapi | 2.8.17 (линия 2.8.x для Boot 3.5) |
| PostgreSQL | 16 (образ `postgres:16-alpine`) |
| Python | 3.12+ (проверено на 3.13), httpx + pydantic + selectolax |
| Node / pnpm | Node 24, pnpm 10 |
| Next.js / React | 16.4.5 / 19.3.1 |
| Tailwind / shadcn/ui | Tailwind 4.3.5, shadcn/ui (new-york, CSS variables) |
| TypeScript | 5.9.3 (strict) |

## Структура

```text
career-radar/
  core-api/          Java 21 + Spring Boot, единственный владелец БД
  collector/         Python 3.12, парсинг и нормализация, без записи в БД
  web/               Next.js App Router + Tailwind + shadcn/ui
  infra/             docker-compose.yml, .env.example, Caddyfile (позже)
  docs/              architecture.md
  gradlew(.bat)      общий Gradle wrapper (модуль :core-api)
  Makefile           короткие команды для разработки
```

## Быстрый старт

### 1. Переменные окружения

```bash
cp infra/.env.example infra/.env
# отредактируйте POSTGRES_PASSWORD и INTERNAL_TOKEN
```

Для stage 3 добавьте в `infra/.env`:

```bash
# не короче 32 байт; смена значения разлогинивает все токены
JWT_SECRET=<openssl rand -base64 48>
JWT_TTL_SECONDS=3600
APP_USER_EMAIL=you@example.com
# BCrypt-хеш пароля (сам пароль в env не хранится):
#   ./gradlew :core-api:hashPassword
#   .\gradlew.bat :core-api:hashPassword      # Windows
APP_USER_PASSWORD_HASH=$2a$10$...
```

`APP_USER_EMAIL` и `APP_USER_PASSWORD_HASH` — единственный пользователь приложения.
Пустой/короткий `JWT_SECRET` останавливает core-api при старте (fail fast), а не выдаёт
подделываемые токены.

Для stage 5 добавьте LLM-настройки (OpenCode Go, OpenAI-compatible):

```bash
# база должна включать версию: клиент вызывает POST {base}/chat/completions
OPENCODE_GO_BASE_URL=https://your-provider.example/v1
OPENCODE_GO_API_KEY=...
LLM_DEFAULT_MODEL=deepseek-v4.1-flash
LLM_WRITING_MODEL=deepseek-v4-pro
LLM_TIMEOUT_SECONDS=60
LLM_MAX_RETRIES=2
LLM_CACHE_TTL_HOURS=168
# опционально: USD за 1M токенов; при 0 стоимость в llm_runs будет нулевой,
# но токены и latency пишутся всегда
LLM_FLASH_INPUT_PRICE=0
LLM_FLASH_OUTPUT_PRICE=0
LLM_PRO_INPUT_PRICE=0
LLM_PRO_OUTPUT_PRICE=0
# локальное хранилище PDF (в git не попадает)
RESUME_FILES_DIR=./data/resumes
RESUME_MAX_BYTES=10485760
```

`OPENCODE_GO_*` читает только core-api. В web и collector эти переменные не передаются,
а ключ никогда не логируется и не возвращается в API-ответах.

`.env` в git не попадает (см. `.gitignore`).

### 2. PostgreSQL + Redis

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis
docker compose -f infra/docker-compose.yml ps   # оба контейнера healthy
```

Если `POSTGRES_PASSWORD` не задан в `infra/.env`, compose остановится с понятной ошибкой —
это намеренно, пароля по умолчанию нет. Порт по умолчанию `5433`, чтобы не конфликтовать
с локально установленным PostgreSQL на `5432`; измените `POSTGRES_PORT`, если он занят.

### 3. core-api

```bash
./gradlew :core-api:bootRun          # Linux/macOS
.\gradlew.bat :core-api:bootRun      # Windows PowerShell
```

При локальном запуске core-api сам подхватывает `infra/.env` (`spring.config.import`),
поэтому пароль и порт из шага 1 применяются без ручного экспорта переменных.
В Docker/production используйте обычные env-переменные.

Проверка:

```bash
curl http://localhost:8080/api/v1/ping        # {"status":"ok"}
curl http://localhost:8080/actuator/health    # {"status":"UP"}
open http://localhost:8080/swagger-ui.html    # OpenAPI UI
```

### 3a. API этапа 1

Внутренний приём вакансий (только collector, заголовок `X-Internal-Token` = `INTERNAL_TOKEN`
из `infra/.env`):

```bash
curl -X POST http://localhost:8080/internal/v1/vacancies:upsert \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $INTERNAL_TOKEN" \
  -d '{"source":"HH","items":[
        {"externalId":"1","title":"Android Developer","url":"https://hh.ru/vacancy/1",
         "companyName":"ООО «Ромашка»","salaryFrom":150000,"salaryTo":220000,
         "currency":"RUB","remote":true}
      ]}'
# {"received":1,"created":1,"updated":0,"skipped":0}
```

Повторный тот же батч даёт `updated`, а тот же контент с другим `externalId` — `skipped`
(вакансия не дублируется, добавляется связь с источником).

Публичный поиск (stage 3 требует JWT — см. ниже):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}' | jq -r .accessToken)

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/vacancies?page=0&size=20&q=kotlin&profession=ANDROID&seniority=JUNIOR&remote=REMOTE&salaryMin=100000&sort=publishedAt,desc"
# {"items":[...],"page":0,"size":20,"total":1}
```

Словари (`professions`, `skills`, `sources`) заполняются миграциями
`V3__seed_dictionaries.sql` (базовые значения) и `V4__source_catalog.sql` (41 карьерный сайт
из каталога источников этапа 2). Схема этапа — `V2__core_domain.sql`: `V1__baseline.sql`
остаётся пустым baseline, который уже применён на dev-БД.

### 3b. API этапа 3 — auth, dashboard, детали

Один пользователь из env. JWT HS256, время жизни `JWT_TTL_SECONDS` (по умолчанию 3600 с).

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}'
# {"accessToken":"eyJ...","expiresIn":3600}
```

Защищены все `/api/v1/**`, кроме `/api/v1/auth/login` и `/api/v1/ping`. Без токена API
отвечает `401` в формате RFC 7807 (`application/problem+json`). `/internal/**` по-прежнему
использует `X-Internal-Token`, а не JWT.

```bash
# KPI для dashboard
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/dashboard
# {"totalVacancies":73,"newToday":0,"internships":15,"remoteShare":0.0,...}

# детальная карточка (id из списка)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/vacancies/3f1d9c62-5a0f-4f2c-9d5b-1c7a2b8e4f60

# словарь профессий для фильтра
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/professions
```

Параметры поиска: `q` (title/company/description), `profession`, `seniority`,
`remote`, `salaryMin` (совпадение, если `max(salary_to, salary_from) >= salaryMin`),
`page` (с 0), `size` (1..100), `sort` — `publishedAt,desc` (default), `salaryFrom,desc`,
`firstSeenAt,desc`. Неизвестный `sort` и отрицательный `salaryMin` дают `400`.

Swagger UI: `http://localhost:8080/swagger-ui.html` — схема `bearerAuth` уже описана.

### 3c. API этапа 4 — аналитика рынка

Все эндпоинты требуют JWT. Общие query-параметры: `from`, `to` (ISO-даты, включительно),
`profession` (код словаря, регистр не важен), `seniority`, `remote`
(`OFFICE|HYBRID|REMOTE|UNKNOWN`). «Дата рынка» — `coalesce(published_at, first_seen_at)`:
если источник не отдал дату публикации, используется дата первого обнаружения.

```bash
# KPI: всего, новых, медиана/p25/p75, remote share, топ профессий и навыков
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/analytics/overview?from=2026-10-01&to=2026-10-31&profession=ANDROID&top=5"

# динамика: bucket=day|week|month
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/analytics/timeline?from=2026-10-01&to=2026-10-31&bucket=week"

# топ навыков: count и share от числа вакансий выборки
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/analytics/skills?profession=ANDROID&limit=20"

# перцентили зарплат
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/analytics/salaries?profession=JAVA_BACKEND&seniority=JUNIOR"
```

Семантика, которую стоит помнить при сверке с SQL:

- `totalVacancies`, `topProfessions`, `topSkills`, timeline и перцентили берут только
  `status = 'ACTIVE'`; архивные дубликаты не искажают картину.
- Зарплата считается по `coalesce(salary_from, salary_to)`; `percentile_cont` возвращает
  `null`, если в выборке нет ни одной вакансии с зарплатой. `currency` — мода валюты
  выборки (обычно `RUB`).
- `newVacancies` — вакансии с `first_seen_at` внутри запрошенного периода; если `from`
  не задан, считаются впервые найденные сегодня (UTC).
- `share` навыка — доля вакансий выборки, где навык встречается.
- `top` для overview: 1..20 (по умолчанию 5); `limit` для skills: 1..100 (по умолчанию 20).
  Некорректные `bucket`, даты (`from > to`), `top`, `limit` и значения enum дают `400`.

Индексы под агрегаты — `V5__analytics_indexes.sql`: частичные индексы по дате рынка и
зарплате для `ACTIVE`-вакансий плюс `(skill_id, vacancy_id)` для top-skills. Materialized
views намеренно не добавлены: на текущих объёмах (<100k) они не нужны.

### 3d. API этапа 5 — резюме и AI-черновики

Все эндпоинты требуют JWT. `resumeId` и `vacancyId` в теле — публичные UUID из API.

```bash
# создать мастер-резюме; contentJson — любой JSON-объект
curl -X POST http://localhost:8080/api/v1/resumes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Android master","contentJson":{"summary":"Kotlin, Compose"},
       "contentText":"Kotlin, Compose, Room, проект CoffeeApp","isMaster":true}'

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/resumes
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/resumes/{id}
# PUT — те же поля; если isMaster не передавать, флаг не меняется
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/resumes/{id}          # 204
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/resumes/{id}/file -o resume.pdf
```

PDF передаётся в `pdfBase64` (можно с `data:`-префиксом). Файл проверяется по сигнатуре
`%PDF-`, лимит — `RESUME_MAX_BYTES`, имя генерируется из UUID резюме. `contentText`
обязателен и не может быть пустым: именно он уходит в LLM.

```bash
# fit-score
curl -X POST http://localhost:8080/api/v1/vacancies/{vacancyId}/fit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"resumeId":"..."}'
# {"score":78,"verdict":"good_match","strengths":["Kotlin"],"missing":["CI"],
#  "reasons":["..."],"model":"deepseek-v4.1-flash",
#  "tokens":{"input":3200,"output":450},"cached":false}

# черновик письма; tone = professional | friendly | concise
curl -X POST http://localhost:8080/api/v1/vacancies/{vacancyId}/cover-letter \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"resumeId":"...","tone":"professional"}'
# {"subject":"...","text":"...","model":"deepseek-v4-pro","tokens":{...},"cached":false}

# адаптация резюме; мастер-резюме не перезаписывается
curl -X POST http://localhost:8080/api/v1/resumes/{resumeId}/tailor \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"vacancyId":"..."}'
# {"contentJson":{...},"diff":["..."],"warnings":["..."],"model":"...","tokens":{...}}
```

Семантика AI-слоя:

- Ответы кэшируются на `LLM_CACHE_TTL_HOURS` (по умолчанию 168 ч) по ключу
  `sha256(task + model + prompt_version + resume_hash + vacancy_hash)`.
  `"forceRefresh": true` в теле обходит кэш и перезаписывает его.
- Резюме обрезается до 8k символов, описание вакансии — до 6k; лимиты вывода —
  800/1200/2000 токенов для fit/письма/tailor.
- Каждый вызов пишется в `llm_runs`: `task`, `model`, `prompt_version`, `input_tokens`,
  `output_tokens`, `cost_usd`, `latency_ms`, `status` (`SUCCESS`/`FAILED`) и ошибка.
  Невалидный JSON от модели даёт `502 Problem Details` и `FAILED`-строку с реальными
  токенами, поэтому расход остаётся видимым.
- Пустые `OPENCODE_GO_BASE_URL`/`OPENCODE_GO_API_KEY` дают `503` с понятным `detail`,
  а не stacktrace. Письма не отправляются: stage 5 создаёт только черновики.

### 4. collector

```bash
python -m venv collector/.venv
collector/.venv/Scripts/activate        # Windows
# source collector/.venv/bin/activate   # Linux/macOS
pip install -e "collector[dev]"

python -m collector --help
python -m collector ping                # проверить связь с core-api
python -m collector sources             # MTS, VK, YANDEX
```

#### 4a. Сбор вакансий с карьерных сайтов компаний

Активные источники этапа: **MTS** (публичный JSON API), **YANDEX** (JSON endpoint,
который использует сам сайт) и **VK** (SSR HTML первой страницы). Каждый адаптер проверяет
`robots.txt`, держит не более 1 запроса в секунду на хост, не обходит 403/капчу и не пишет
в БД напрямую.

```bash
# 30 вакансий из всех источников, без записи в core-api
python -m collector companies --source all --max-items 30 --dry-run

# стажировки: seniority=INTERN или title/description содержит стажер/trainee/intern
python -m collector companies --source all --internships --max-items 10

# один источник, фильтр по словам и сохранение сырых данных для фикстур
python -m collector companies --source mts --query java --pages 2 \
  --output /tmp/mts_page_raw.json
```

`--output` пишет отдельный файл и не трогает фикстуры тестов (`collector/tests/fixtures/`).

`--dry-run` не создаёт клиент core-api. Без `--dry-run` нормализованные `VacancyDraft`
уходят в `POST /internal/v1/vacancies:upsert` батчами не больше 50. Для `--internships`
по умолчанию просматривается до 5 страниц, потому что стажировок мало в общей выдаче;
`--max-items` ограничивает итоговое число уже после фильтра. `--query` применяется
клиентски (по словам в заголовке/описании), чтобы одинаково работать на всех источниках.

Ограничения источников (проверено 07.11.2026):

- **MTS**: API отдаёт 25 элементов на страницу и перемешивает порядок между запросами.
  Пагинация — `pagination[page]`, дедупликация по `id`. `description`, `requirements`,
  `tasks` и зарплата у большинства вакансий пустые — это ограничение источника,
  нормализатор сохраняет то, что есть.
- **YANDEX**: используется публичный endpoint `/jobs/api/jobs/publications/`, который
  вызывает сам сайт; пагинация — по `next`-курсору, до 40 элементов на страницу.
  Страница `/yaintern/` не отдаёт список вакансий, поэтому `--internships` фильтрует общий
  список. На 07.11.2026 в общем списке Яндекса не было вакансий со словом «стажер» —
  это нормально для текущей выдачи.
- **VK**: SSR-страница `/vacancy/` отдаёт 25 карточек. Дальнейшая подгрузка идёт через
  `/load_more/`, который запрещён `robots.txt`; адаптер честно читает только первую
  страницу и не имитирует пагинацию. При капче/403 адаптер останавливается.

#### 4b. Legacy hh.ru adapter (не используется)

Адаптер `hh` оставлен в коде для истории: публичный `GET /vacancies` у hh.ru закрыт
с апреля 2026, API для соискателей отключён 15.12.2025. Скрапинг и обход капчи не делаем.

Коллектор читает `CORE_API_URL`, `INTERNAL_TOKEN`, `COLLECTOR_USER_AGENT`,
`MTS_BASE_URL`, `YANDEX_BASE_URL`, `VK_BASE_URL`, `REQUEST_TIMEOUT`, `RATE_LIMIT_SECONDS`
(а для legacy-адаптера — `HH_USER_AGENT`, `HH_BASE_URL`, `HH_AREA`, `HH_PER_PAGE` и
опциональный `HH_ACCESS_TOKEN`) из окружения. `COLLECTOR_USER_AGENT` имеет приоритет над
`HH_USER_AGENT`. При локальном запуске удобно загрузить `infra/.env`:

```powershell
# PowerShell
Get-Content infra/.env | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  Set-Item -Path "Env:$name" -Value $value
}
```

```bash
# bash
set -a; . infra/.env; set +a
```

Запуск:

```bash
python -m collector hh --query "android стажер" --pages 1 --max-items 10
# {"dry_run": false, "normalized": 10, "by_seniority": {...}, ...,
#  "received": 10, "created": 10, "updated": 0, "skipped": 0}

python -m collector hh --query "android стажер" --pages 2 --dry-run
# только сбор и нормализация, core-api не вызывается

python -m collector hh --query "android стажер" --pages 1 \
  --output collector/tests/fixtures/hh_page.json
# сохранить сырой ответ hh.ru для тестов
```

Правила и ограничения:

- не более 1 запроса в секунду (`RATE_LIMIT_SECONDS`), `429` и `Retry-After`
  соблюдаются, `403` не обходится;
- повторный запуск не создаёт дубликаты: core-api дедуплицирует по
  `(source, externalId)` и по контент-хешу;
- `raw_json` уходит в core-api и хранится в `vacancy_sources.raw_json` после вычистки
  ключей вида `token`/`password`; тела вакансий и токены в логи не пишутся;
- `description` берётся из сниппета поисковой выдачи (`requirement` + `responsibility`),
  очищается от HTML и обрезается до 20 000 символов. Полные описания (отдельный запрос
  на вакансию) — задача следующего этапа.

> **403 от hh.ru — это не баг коллектора.** С апреля 2026 `GET /vacancies` закрыт для
> анонимных запросов: нужен токен приложения `HH_ACCESS_TOKEN`. Регистрация приложения —
> https://dev.hh.ru (личный кабинет → «Добавить приложение»), токен выдаётся через
> `POST https://api.hh.ru/token` с `grant_type=client_credentials`. Сама регистрация может
> требовать подтверждённого аккаунта и модерации; если приложение не одобряют, легального
> способа автоматически собирать выдачу hh.ru нет — нужен другой источник.
> Обход капчи/ограничений и парсинг сайта не используются.

### 5. web

```bash
pnpm --dir web install
pnpm --dir web dev      # http://localhost:3000
```

Страницы:

- `/login` — форма входа; `POST /api/auth/login` в Next route handler кладёт JWT в
  **httpOnly** cookie `cr_token`, в клиентский JavaScript токен не попадает.
- `/` — dashboard: фильтры (профессия, грейд, формат работы, период 30/90/180/365 дней
  или «всё время»), 4 KPI (всего, новых, медианная зарплата, remote share), график
  публикаций по неделям, топ навыков и топ профессий, последние 5 вакансий.
- `/analytics` — большие графики: недельная динамика, межквартильный диапазон зарплат
  p25–p75 с медианой, топ-20 навыков, remote share и сравнение профессий. Под каждым
  графиком — 1–2 вывода «что это значит»; пустая выборка показывает «Нет данных за период».
- `/vacancies` — таблица с фильтрами (поиск, профессия, грейд, формат, зарплата от,
  сортировка) и пагинацией. Фильтры живут в URL query params.
- `/vacancies/[id]` — детальная карточка: описание, навыки, источник и ссылка на оригинал.
  Ниже — панель «Проверка под вакансию»: выбор резюме, fit-score с цветным badge,
  сильные и недостающие навыки, черновик письма в модалке («Скопировать», «Сохранить .txt»,
  «Перегенерировать») и адаптация резюме с diff/warnings.
- `/resume` — мастер-резюме и варианты: список, редактор `contentText` и `contentJson`,
  флаг master, загрузка/скачивание PDF. JSON валидируется в браузере до отправки.

Мутации из браузера идут через Next route handlers (`web/app/api/resumes/*`,
`web/app/api/vacancies/[id]/*`): они читают httpOnly cookie и проксируют запрос в core-api,
поэтому JWT по-прежнему не попадает в клиентский JavaScript.

Графики — Recharts (`lib/chart-theme.ts`, `components/charts/*`), фильтры аналитики —
`components/analytics-filters.tsx`. Период превращается в `from`/`to` в
`lib/period.ts`; сами фильтры остаются в URL.

`lib/api.ts` — единственный клиент core-api: читает cookie на сервере, добавляет
`Authorization: Bearer`, при `401` делает redirect на `/login`. Ошибки core-api показываются
карточкой ошибки, пустая выдача — empty state.

`NEXT_PUBLIC_API_URL` задаёт адрес core-api (по умолчанию `http://localhost:8080`);
это единственная переменная, которая попадает в клиентский бандл, и она не секретная.

## Проверки (Definition of Done этапа)

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis
./gradlew :core-api:test
python -m pytest collector/tests
python -m ruff check collector
python -m collector companies --source all --max-items 30 --dry-run
python -m collector companies --source all --internships --max-items 10 --dry-run
pnpm --dir web build
```

Ожидаемо:

- контейнеры `career-radar-postgres` и `career-radar-redis` — `healthy`;
- `PingControllerTest` проходит; интеграционные тесты с Testcontainers применяют Flyway
  на чистой БД и проверяют `ddl-auto: validate` (нужен Docker; без него тесты этапа 1 падают,
  а `CareerRadarApplicationTests` пропускается);
- тесты stage 3: `AuthControllerTest` (login ok / wrong password / wrong email / 400),
  `DashboardControllerTest` (counts, только ACTIVE), `PublicVacancyControllerTest`
  (401 без токена, фильтры, `salaryMin`, сортировка, detail, 404);
- тесты stage 4: `AnalyticsControllerTest` — overview counts и перцентили, фильтры
  profession/seniority/remote, `newVacancies` внутри периода, timeline по day/week/month,
  top skills с `share`, пустая БД, `400` на невалидные `bucket`/даты/`top`/`limit`/enum;
- тесты stage 5: `ResumeControllerTest` (CRUD, master-флаг, PDF upload/download, 400/404),
  `FitScoreServiceTest` (валидный JSON, `FAILED`-запись и 502 на мусор, кэш, forceRefresh,
  пустой `contentText` без вызова LLM), `CoverLetterServiceTest` (модель/тон/промт,
  сохранение варианта, запрет пустого резюме), `TailorServiceTest` (draft, обязательный
  warning, пустой `contentJson` → 502), `OpenAiCompatibleLlmClientTest` (WireMock:
  usage/model, retry 5xx, отсутствие retry на 4xx, 503 без конфига);
- тесты collector проходят без сетевых вызовов (httpx `MockTransport`, `respx` для core-api);
- `next build` завершается успешно.

Проверено вручную на dev-БД: `created=3`, повторный POST — `updated=3`, дубль по контент-хешу —
`skipped=1`; `GET /api/v1/vacancies` отдаёт вакансии; `/swagger-ui.html` и `/v3/api-docs` — 200;
`raw_json` в БД не содержит полей вида `token`/`password`.

Этап 2 проверен живым прогоном 07.11.2026 (core-api + PostgreSQL + реальные сайты):

- `companies --source all --max-items 30`: первый прогон — `created=30`; повторный —
  `updated=19, created=9, skipped=2` (MTS перемешивает выдачу между запросами, поэтому
  часть `id` в новом сэмпле отличается; `skipped` — совпадение контент-хеша, дубликатов
  в `vacancies` нет).
- `companies --source yandex --max-items 30`: первый прогон — `created=25, updated=5`,
  повторный — `created=0, updated=30` (курсорная выдача Яндекса детерминирована).
- `companies --source all --internships --max-items 10`: просмотрено 117 записей,
  отобрано 10, все `seniority=INTERN`.
- В БД: 73 вакансии, 15 `INTERN`, 0 повторяющихся `content_hash`; публичный
  `GET /api/v1/vacancies?seniority=INTERN` отдаёт `total=15`.
- VK отдаёт 25 SSR-карточек; `--source vk --dry-run` собирает 25 вакансий без капчи.
  Yandex `--dry-run` — 30 вакансий; MTS `--dry-run` — 25.

Этап 3 проверен живым прогоном (core-api + web + dev-БД):

- `POST /api/v1/auth/login` с корректными env-кредами — `200` и JWT; неверный пароль — `401`
  Problem Details; `GET /api/v1/vacancies` без токена — `401`.
- `GET /api/v1/dashboard` и `/api/v1/vacancies` с токеном отдают данные stage 2;
  `GET /api/v1/vacancies/{id}` — карточку с источником; несуществующий id — `404`.
- В браузере: логин → dashboard (4 KPI + последние вакансии) → `/vacancies` с фильтрами
  в URL → карточка вакансии → logout очищает cookie и возвращает на `/login`.

Этап 4 проверен живым прогоном (core-api :8090 + web :3001 + dev-БД, 07.11.2026):

- Flyway применил `V5__analytics_indexes.sql` на dev-БД (`now at version v5`).
- Seed через `/internal/v1/vacancies:upsert`: `created=4`. Сверка `/analytics/overview`
  с SQL по тем же фильтрам (`profession=ANDROID`, ноябрь 2026): `total=5`, `median=75000`,
  `p25=57500`, `p75=97500`, remote `3/5 = 0.6` — значения совпали.
- `/analytics/salaries` вернул те же перцентили и `sampleSize=4`; `/analytics/timeline`
  по неделям суммируется в `totalVacancies`.
- Фильтры меняют данные: `remote=REMOTE` → 3 вакансии и `remoteShare=1.0`;
  `profession=ML&seniority=INTERN` за 30 дней → пустая выборка и «Нет данных за период».
- В браузере: логин → dashboard (4 KPI, line-график, top skills/professions, последние
  вакансии) → `/analytics` (недельная динамика, зарплатный диапазон, remote share,
  топ-20 навыков, сравнение профессий) → смена фильтров обновляет URL и цифры.
  Ошибок в консоли нет.
- Важно: `vacancy_skills` пока наполняется только вручную/тестами — коллектор этапа 2
  не передаёт навыки. API и график top-skills готовы; на реальных данных график будет
  пустым, пока не появится классификация навыков (следующий этап).

Этап 5 проверен живым прогоном (core-api :8091 + локальный OpenAI-compatible стаб +
dev-БД, 09.11.2026). Реальный ключ OpenCode Go не использовался, чтобы не тратить токены;
HTTP-контракт, промпты, парсинг и запись в БД проверены на стабе:

- Flyway применил `V6__resume_llm.sql` на dev-БД (`now at version v6`).
- `POST /api/v1/resumes` создал мастер-резюме с PDF; `GET` списка/деталей и
  `GET /{id}/file` вернули данные и `application/pdf`; файл сохранён под именем UUID.
- `POST /vacancies/{id}/fit` с `forceRefresh` → `score=78`, `verdict=good_match`,
  `missing=["Coroutines Flow","CI"]`, токены `1234/234`; повторный запрос без флага →
  `cached=true` и один вызов модели.
- `POST /cover-letter` → `subject`, текст и `model=deepseek-v4-pro`; повторный запрос →
  `cached=true`. `POST /tailor` → `diff=2`, `warnings=2` (включая обязательный
  «проверьте факты»), `contentJson` вернулся, мастер-резюме не изменилось.
- В БД: `llm_runs` содержит `SUCCESS`-строки с токенами, latency и `cost_usd=0` при
  нулевых ценах; `resume_variants` — fit/letter/tailor; `llm_cache` — 3 записи с TTL
  на 7 дней. Тестовые строки после проверки удалены.
- Next route handlers проверены через реальную cookie-сессию: `POST /api/auth/login` →
  `POST /api/resumes` (201), `PUT` (200), `DELETE` (204), повторный `GET` (404), а также
  `fit`/`cover-letter`/`tailor` через прокси — все ответы совпали с прямыми вызовами core-api.
- Отдельный тест подтверждает: без `OPENCODE_GO_*` AI-эндпоинт отвечает `503`
  с понятным `detail`, а не `500`.

## Docker-образы

Три Dockerfile многостадийные; контекст сборки — корень репозитория для core-api
(нужен общий Gradle wrapper), `web/` для web и `collector/` для collector:

```bash
docker build -f core-api/Dockerfile -t career-radar-core-api .
docker build -t career-radar-web ./web
docker build -t career-radar-collector ./collector
```

Все три образа собраны и проверены вручную: web-контейнер отдаёт `/`, core-api-контейнер
отвечает `{"status":"ok"}` на `/api/v1/ping`, collector печатает версию. В образе
core-api каталог `/app/data/resumes` создан и принадлежит runtime-пользователю,
он объявлен как `VOLUME /app/data` — PDF-резюме переживут пересоздание контейнера
только при смонтированном volume.

`core-api` и `web` пока не включены в `docker-compose.yml` намеренно: по этапу 0
достаточно postgres + redis, а приложение запускается локально.

## Безопасность

- Секреты только в `infra/.env`, файл в `.gitignore`; в репозитории есть лишь `.env.example`.
- Один пользователь: `APP_USER_EMAIL` + BCrypt-хеш `APP_USER_PASSWORD_HASH`. Пароль в
  открытом виде не хранится; хеш генерируется `./gradlew :core-api:hashPassword`.
- JWT (HS256, `JWT_SECRET` ≥ 32 байт) живёт в httpOnly cookie `cr_token` (`SameSite=Lax`,
  `Secure` в production) и передаётся на сервере как `Authorization: Bearer`. Токен не
  попадает в клиентский бандл или localStorage.
- Без токена `/api/v1/**` отвечает `401` Problem Details; `/internal/**` защищён отдельным
  `INTERNAL_TOKEN` (сравнение в постоянном времени), JWT там не принимается.
- LLM-ключ (`OPENCODE_GO_API_KEY`) читает только core-api; в промпты и `llm_runs` не
  попадают тела писем, токены и ключи. PDF-резюме лежат в `RESUME_FILES_DIR` (по умолчанию
  `./data/resumes`), каталог `data/` в `.gitignore`, имя файла генерируется из UUID.
- Collector не пишет в БД и не обходит капчи/авторизацию/ToS; User-Agent честный,
  `robots.txt` проверяется до первого запроса, частота — не более 1 rps на хост.

## Документация

- `docs/architecture.md` — границы сервисов и поток данных.
- OpenAPI: `http://localhost:8080/swagger-ui.html`.

# Career Radar

Личная операционная система поиска работы: сбор вакансий, аналитика рынка, fit-score резюме,
application tracker и read-only почта. Один пользователь, без биллинга и мультитенантности.

**Текущий этап — Stage 2 (карьерные сайты компаний):** Python-коллектор собирает вакансии
и стажировки с карьерных сайтов МТС, Яндекса и VK, нормализует их в `VacancyDraft`
(грейд, формат работы, профессия) и отправляет батчами в core-api. hh.ru не используется:
его публичный API закрыт, а обход ограничений запрещён. Аналитика, резюме, LLM, почта и
остальные источники — в следующих этапах.

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

Публичный поиск:

```bash
curl "http://localhost:8080/api/v1/vacancies?page=0&size=20&q=kotlin&profession=ANDROID&seniority=JUNIOR&remote=REMOTE"
# {"items":[...],"page":0,"size":20,"total":1}
```

Словари (`professions`, `skills`, `sources`) заполняются миграциями
`V3__seed_dictionaries.sql` (базовые значения) и `V4__source_catalog.sql` (41 карьерный сайт
из каталога источников этапа 2). Схема этапа — `V2__core_domain.sql`: `V1__baseline.sql`
остаётся пустым baseline, который уже применён на dev-БД.

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

Страница `/` показывает заголовок Career Radar и живой статус core-api.

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

## Docker-образы

Три Dockerfile многостадийные; контекст сборки — корень репозитория для core-api
(нужен общий Gradle wrapper), `web/` для web и `collector/` для collector:

```bash
docker build -f core-api/Dockerfile -t career-radar-core-api .
docker build -t career-radar-web ./web
docker build -t career-radar-collector ./collector
```

Все три образа собраны и проверены вручную: web-контейнер отдаёт `/`, core-api-контейнер
отвечает `{"status":"ok"}` на `/api/v1/ping`, collector печатает версию.

`core-api` и `web` пока не включены в `docker-compose.yml` намеренно: по этапу 0
достаточно postgres + redis, а приложение запускается локально.

## Безопасность

- Секреты только в `infra/.env`, файл в `.gitignore`; в репозитории есть лишь `.env.example`.
- `INTERNAL_TOKEN` защищает `/internal/**` (фильтр `InternalTokenFilter`, сравнение в
  постоянном времени). Если токен не задан, внутренний API отвечает `503`, а не работает открыто.
- Collector не пишет в БД и не обходит капчи/авторизацию/ToS; User-Agent честный,
  `robots.txt` проверяется до первого запроса, частота — не более 1 rps на хост.

## Документация

- `docs/architecture.md` — границы сервисов и поток данных.
- OpenAPI: `http://localhost:8080/swagger-ui.html`.

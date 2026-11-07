# Career Radar

Личная операционная система поиска работы: сбор вакансий, аналитика рынка, fit-score резюме,
application tracker и read-only почта. Один пользователь, без биллинга и мультитенантности.

**Текущий этап — Stage 1 (core domain):** схема БД, словари, дедупликация и приём вакансий
от collector. core-api принимает батч по внутреннему токену, хранит вакансии, компании,
источники и связи с источниками, отдаёт публичный список с пагинацией и фильтрами.
Аналитика, резюме, LLM и почта — в следующих этапах.

## Стек и зафиксированные версии

| Слой | Версия |
|---|---|
| Java / Gradle | Java 21 (toolchain), Gradle 8.14.3 |
| Spring Boot | 3.5.16 (последний стабильный 3.x) |
| springdoc-openapi | 2.8.17 (линия 2.8.x для Boot 3.5) |
| PostgreSQL | 16 (образ `postgres:16-alpine`) |
| Python | 3.12+ (проверено на 3.13), httpx + pydantic |
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

Словари (`professions`, `skills`, `sources`) заполняются миграцией `V3__seed_dictionaries.sql`.
Схема этапа — `V2__core_domain.sql`: `V1__baseline.sql` остаётся пустым baseline, который уже
применён на dev-БД.

### 4. collector

```bash
python -m venv collector/.venv
collector/.venv/Scripts/activate        # Windows
# source collector/.venv/bin/activate   # Linux/macOS
pip install -e "collector[dev]"

python -m collector --help
python -m collector ping                # проверить связь с core-api
python -m collector sources             # пока пусто: адаптеры появятся позже
```

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
pnpm --dir web build
```

Ожидаемо:

- контейнеры `career-radar-postgres` и `career-radar-redis` — `healthy`;
- `PingControllerTest` проходит; интеграционные тесты с Testcontainers применяют Flyway
  на чистой БД и проверяют `ddl-auto: validate` (нужен Docker; без него тесты этапа 1 падают,
  а `CareerRadarApplicationTests` пропускается);
- тесты collector проходят без сетевых вызовов (httpx `MockTransport`);
- `next build` завершается успешно.

Проверено вручную на dev-БД: `created=3`, повторный POST — `updated=3`, дубль по контент-хешу —
`skipped=1`; `GET /api/v1/vacancies` отдаёт вакансии; `/swagger-ui.html` и `/v3/api-docs` — 200;
`raw_json` в БД не содержит полей вида `token`/`password`.

## Docker-образы

Оба Dockerfile многостадийные; контекст сборки — корень репозитория для core-api
(нужен общий Gradle wrapper) и `web/` для web:

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
- Collector не пишет в БД и не обходит капчи/авторизацию/ToS; User-Agent честный.

## Документация

- `docs/architecture.md` — границы сервисов и поток данных.
- OpenAPI: `http://localhost:8080/swagger-ui.html`.

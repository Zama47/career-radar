# Career Radar

Собирает вакансии с карьерных сайтов, хранит их в PostgreSQL и показывает в браузере:
сколько новых, какие навыки просят, как двигаются зарплаты. К вакансии можно приложить
резюме и получить оценку совпадения, черновик сопроводительного письма или вариант
резюме под конкретный оффер.

Проект личный, под одного пользователя. Один логин, без регистрации и биллинга.
Наружу уходят только запросы к LLM-провайдеру, и то если вы сами заполнили ключ.

## Из чего состоит

```text
браузер ──▶ web (Next.js) ──▶ core-api (Spring Boot) ──▶ PostgreSQL
                                  ▲
                                  │ /internal/v1/vacancies:upsert
                             collector (Python)
```

| Компонент | Роль |
|---|---|
| `core-api` | Единственный владелец базы: API, авторизация, дедупликация, агрегаты, вызовы LLM |
| `collector` | Забирает вакансии у MTS, Яндекса и VK, приводит их к общему виду. В базу не пишет |
| `web` | Дашборд, аналитика, список вакансий и резюме. JWT лежит в httpOnly cookie |
| `postgres` | Хранилище. Redis есть в compose, но пока не используется |

## Что уже работает

Коллектор обходит MTS, Яндекс и VK, проверяет `robots.txt` и держит не больше одного
запроса в секунду на хост. Повторный запуск не плодит копии: core-api сравнивает источник
и `externalId`, а также хеш содержимого.

В поиске есть фильтры по профессии, грейду, формату работы и зарплате. На дашборде KPI,
динамика публикаций, медиана и квартили зарплат, топ навыков и профессий. Фильтры живут
в URL, так что ссылку на выборку можно сохранить.

Резюме хранятся с PDF, под вакансию можно сделать вариант. Модель считает fit-score,
пишет черновик письма и адаптирует резюме, показывая diff и предупреждения. Ответы
кэшируются, каждый вызов пишется в `llm_runs` с токенами, задержкой и стоимостью.

## Запуск

Нужны JDK 21, Docker, Node 24 с pnpm и Python 3.12+. Подробности по переменным
окружения и все команды есть в [docs/development.md](docs/development.md).

Сначала окружение и база:

```bash
cp infra/.env.example infra/.env
# заполните POSTGRES_PASSWORD, INTERNAL_TOKEN, JWT_SECRET, APP_USER_EMAIL,
# APP_USER_PASSWORD_HASH; для AI-эндпоинтов ещё OPENCODE_GO_*
docker compose -f infra/docker-compose.yml up -d postgres redis
```

Дальше core-api и web:

```bash
./gradlew :core-api:bootRun             # http://localhost:8080
pnpm --dir web install
pnpm --dir web dev                      # http://localhost:3000
```

При локальном запуске core-api сам читает `infra/.env`. Swagger:
`http://localhost:8080/swagger-ui.html`.

Коллектор:

```bash
python -m venv collector/.venv
collector/.venv/Scripts/activate        # Windows
# source collector/.venv/bin/activate   # Linux/macOS
pip install -e "collector[dev]"

python -m collector companies --source all --max-items 30 --dry-run
```

Без `--dry-run` собранные вакансии уйдут в core-api.

## Проверки

```bash
./gradlew :core-api:test
python -m pytest collector/tests
python -m ruff check collector
pnpm --dir web build
```

Интеграционным тестам core-api нужен запущенный Docker: Testcontainers поднимает
PostgreSQL и прогоняет миграции Flyway на чистой базе.

## Ограничения

- Навыки из текста вакансий пока не классифицируются, поэтому график топ-навыков
  на свежих данных будет пустым. API и график готовы, не хватает разбора.
- VK отдаёт только первую страницу: дальше стоит `robots.txt`, обходить не пытаемся.
- Адаптер hh.ru оставлен для истории. Публичный API у них закрыт, капчу не обходим.
- Почта и трекер откликов не сделаны. Сейчас модель готовит только черновики,
  ничего не отправляет.

## Ссылки

- [docs/architecture.md](docs/architecture.md) — границы сервисов и потоки данных.
- [docs/development.md](docs/development.md) — переменные, API, коллектор, проверки по этапам.

Стек: Java 21 и Spring Boot 3.5, PostgreSQL 16, Python 3.12, Next.js 16 и React 19,
Tailwind 4, Recharts.

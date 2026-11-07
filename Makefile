# Career Radar — developer shortcuts.
# Windows: run the underlying commands from README.md (make is optional).

COMPOSE := docker compose -f infra/docker-compose.yml
PYTHON ?= python

.PHONY: help up down logs ps api web collector test build clean

help:
	@echo "up        - start postgres + redis"
	@echo "down      - stop postgres + redis"
	@echo "logs      - follow postgres/redis logs"
	@echo "api       - run core-api on :8080"
	@echo "web       - run web on :3000"
	@echo "collector - collector CLI help"
	@echo "test      - core-api tests, collector tests, web build"
	@echo "build     - bootJar + web production build"

up:
	$(COMPOSE) up -d postgres redis

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

api:
	./gradlew :core-api:bootRun

web:
	pnpm --dir web dev

collector:
	$(PYTHON) -m collector --help

test:
	./gradlew :core-api:test
	$(PYTHON) -m pytest collector/tests
	pnpm --dir web build

build:
	./gradlew :core-api:bootJar
	pnpm --dir web build

clean:
	./gradlew clean
	rm -rf web/.next collector/.pytest_cache collector/.ruff_cache

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import httpx

from collector.config import CollectorConfig
from collector.sources.base import SourceQuery
from collector.sources.mts import MtsSource

FIXTURE = Path(__file__).parent / "fixtures" / "mts_page.json"


def load_fixture() -> dict[str, Any]:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def make_source(handler: Any) -> MtsSource:
    config = CollectorConfig.from_env({})
    client = httpx.Client(
        transport=httpx.MockTransport(handler), base_url=config.mts_base_url
    )
    return MtsSource(config, client=client, sleep=lambda _: None)


def test_fetch_maps_mts_items_to_raw_vacancies() -> None:
    payload = load_fixture()
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        requests.append(request)
        return httpx.Response(200, json=payload)

    with make_source(handler) as source:
        raws = source.fetch(SourceQuery(max_pages=1))

    assert len(raws) == 5
    assert len(requests) == 1
    assert requests[0].url.params["pagination[page]"] == "1"

    first = raws[0]
    assert first.source == "MTS"
    assert first.external_id == "696751070893834400"
    assert first.title == "Начинающий специалист банка г. Уфа"
    assert first.company == "ПАО МТС-Банк"
    assert first.location == "Уфа"
    assert first.currency == "RUB"
    assert first.url.endswith("/vacancy/696751070893834313")
    assert first.published_at is not None
    assert first.published_at.year == 2026


def test_fetch_uses_pagination_and_deduplicates() -> None:
    payload = load_fixture()
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        requests.append(request)
        return httpx.Response(200, json=payload)

    with make_source(handler) as source:
        raws = source.fetch(SourceQuery(max_pages=2))

    assert len(requests) == 2
    assert requests[1].url.params["pagination[page]"] == "2"
    assert len(raws) == 5


def test_fetch_honours_max_items_and_query_filter() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        limited = source.fetch(SourceQuery(max_pages=1, max_items=2))
        java_only = source.fetch(SourceQuery(max_pages=1, text="Java"))

    assert len(limited) == 2
    assert [raw.title for raw in java_only] == [
        "Разработчик интеграционных решений (Java)",
        "Ведущий разработчик Java",
    ]


def test_normalize_maps_experience_work_format_and_profession() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        drafts = [source.normalize(raw) for raw in source.fetch(SourceQuery(max_pages=1))]

    assert [draft.seniority for draft in drafts] == [
        "INTERN",
        "MIDDLE",
        "INTERN",
        "JUNIOR",
        "SENIOR",
    ]
    assert {draft.remote_type for draft in drafts} == {"OFFICE"}
    assert [draft.profession for draft in drafts] == [
        "OTHER",
        "JAVA_BACKEND",
        "OTHER",
        "JAVA_BACKEND",
        "OTHER",
    ]
    assert drafts[0].company == "ПАО МТС-Банк"
    assert drafts[0].location == "Уфа"
    assert drafts[0].currency == "RUB"
    assert all(draft.remote is False for draft in drafts)


def test_normalize_parses_salary_remote_and_html_description() -> None:
    payload = load_fixture()
    item = dict(payload["data"][0])
    item.update(
        {
            "salaryFrom": 120000,
            "salaryTo": 180000,
            "description": "<p>Kotlin и Jetpack Compose</p>",
            "requirements": ["Android", "Coroutines"],
            "tasks": ["Разработка приложения"],
            "workFormats": [{"title": "Удалённо"}],
        }
    )
    payload = {"data": [item], "meta": {"pagination": {"pageCount": 1}}}

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        draft = source.normalize(source.fetch(SourceQuery(max_pages=1))[0])

    assert draft.salary_from == 120000
    assert draft.salary_to == 180000
    assert draft.remote_type == "REMOTE"
    assert draft.remote is True
    assert draft.profession == "ANDROID"
    assert "Kotlin" in (draft.description or "")
    assert "Разработка приложения" in (draft.description or "")


def test_adapter_implements_company_source_contract() -> None:
    from collector.sources.base import CompanySource

    config = CollectorConfig.from_env({})
    source = MtsSource(config)
    try:
        assert isinstance(source, CompanySource)
    finally:
        source.close()

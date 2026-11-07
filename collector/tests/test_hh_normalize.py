from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import httpx
import pytest

from collector.config import CollectorConfig
from collector.models import SearchQuery
from collector.sources.base import SourceError
from collector.sources.hh import HhSource

FIXTURE = Path(__file__).parent / "fixtures" / "hh_page.json"


def load_fixture() -> dict[str, Any]:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def make_source(
    handler: Any,
    *,
    sleeps: list[float] | None = None,
    max_attempts: int = 3,
) -> HhSource:
    config = CollectorConfig.from_env({"HH_PER_PAGE": "100"})
    client = httpx.Client(transport=httpx.MockTransport(handler), base_url=config.hh_base_url)
    recorded = sleeps if sleeps is not None else []
    return HhSource(
        config,
        client=client,
        sleep=lambda seconds: recorded.append(seconds),
        max_attempts=max_attempts,
    )


def test_fetch_maps_hh_items_to_raw_vacancies() -> None:
    payload = load_fixture()
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json=payload)

    with make_source(handler) as source:
        raws = source.fetch(SearchQuery(text="android стажер", area=1, max_pages=1))

    assert len(raws) == 5
    assert len(requests) == 1
    params = requests[0].url.params
    assert params["text"] == "android стажер"
    assert params["area"] == "1"
    assert params["per_page"] == "100"
    assert params["only_with_salary"] == "false"

    first = raws[0]
    assert first.source == "HH"
    assert first.external_id == "112233445"
    assert first.title == "Android-разработчик (стажер)"
    assert first.company == "МТС"
    assert first.location == "Москва"
    assert first.salary_from == 50_000
    assert first.salary_to == 70_000
    assert first.currency == "RUB"
    assert first.published_at is not None
    assert first.published_at.year == 2026
    assert first.raw_json["id"] == "112233445"


def test_normalize_fixture_items() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        drafts = [source.normalize(raw) for raw in source.fetch(SearchQuery(text="android"))]

    expected = [
        ("INTERN", "REMOTE", "ANDROID", 50_000, 70_000, "RUB"),
        ("JUNIOR", "OFFICE", "JAVA_BACKEND", None, None, None),
        ("MIDDLE", "HYBRID", "SYSTEM_ANALYST", 150_000, 190_000, "RUB"),
        ("SENIOR", "REMOTE", "DATA_ANALYST", 180_000, None, "RUB"),
        ("LEAD", "REMOTE", "FRONTEND", 5_000, 8_000, "USD"),
    ]

    assert len(drafts) == len(expected)
    for draft, (seniority, remote_type, profession, salary_from, salary_to, currency) in zip(
        drafts, expected, strict=True
    ):
        assert draft.source == "HH"
        assert draft.seniority == seniority
        assert draft.remote_type == remote_type
        assert draft.profession == profession
        assert draft.remote is (remote_type == "REMOTE")
        assert draft.salary_from == salary_from
        assert draft.salary_to == salary_to
        assert draft.currency == currency
        assert draft.url.startswith("https://hh.ru/vacancy/")
        assert draft.company
        assert draft.location

    assert drafts[0].description is not None
    assert "<" not in drafts[0].description
    assert "Kotlin и Jetpack Compose" in drafts[0].description
    assert "coroutines" in drafts[0].description
    assert drafts[1].description is not None
    assert "Spring Boot" in drafts[1].description


def test_fetch_respects_max_items() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        raws = source.fetch(SearchQuery(text="android", max_items=2))

    assert len(raws) == 2
    assert [raw.external_id for raw in raws] == ["112233445", "223344556"]


def test_fetch_paginates_until_max_pages() -> None:
    payload = load_fixture()
    first_page = {**payload, "items": payload["items"][:2], "found": 4, "per_page": 2, "page": 0}
    second_page = {
        **payload,
        "items": payload["items"][2:4],
        "found": 4,
        "per_page": 2,
        "page": 1,
    }
    pages = [first_page, second_page]
    calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.params["page"])
        return httpx.Response(200, json=pages[len(calls) - 1])

    with make_source(handler) as source:
        raws = source.fetch(SearchQuery(text="android", max_pages=5, per_page=2))

    assert calls == ["0", "1"]
    assert len(raws) == 4


def test_fetch_retries_after_429_and_honours_retry_after() -> None:
    payload = load_fixture()
    sleeps: list[float] = []
    calls = {"count": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        calls["count"] += 1
        if calls["count"] == 1:
            return httpx.Response(429, headers={"Retry-After": "2"}, text="too many requests")
        return httpx.Response(200, json=payload)

    with make_source(handler, sleeps=sleeps) as source:
        raws = source.fetch(SearchQuery(text="android"))

    assert calls["count"] == 2
    assert 2.0 in sleeps
    assert len(raws) == 5


def test_fetch_stops_after_repeated_429() -> None:
    transport_sleeps: list[float] = []

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(429, headers={"Retry-After": "60"}, text="rate limited")

    with (
        make_source(handler, sleeps=transport_sleeps, max_attempts=2) as source,
        pytest.raises(SourceError, match="429"),
    ):
        source.fetch(SearchQuery(text="android"))


def test_fetch_reports_missing_token_with_actionable_message() -> None:
    with (
        make_source(lambda request: httpx.Response(403, json={"errors": []})) as source,
        pytest.raises(SourceError, match="HH_ACCESS_TOKEN"),
    ):
        source.fetch(SearchQuery(text="android"))


def test_fetch_reports_bad_user_agent() -> None:
    body = {
        "description": "Bad User-Agent header",
        "errors": [{"value": "blacklisted", "type": "bad_user_agent"}],
    }
    with (
        make_source(lambda request: httpx.Response(400, json=body)) as source,
        pytest.raises(SourceError, match="HH_USER_AGENT"),
    ):
        source.fetch(SearchQuery(text="android"))


def test_fetch_skips_items_without_required_fields() -> None:
    payload = {"items": [{"id": "1", "name": "No URL"}], "found": 1}

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        assert source.fetch(SearchQuery(text="android")) == []


def test_fetch_rejects_empty_query() -> None:
    with (
        make_source(lambda request: httpx.Response(200, json=load_fixture())) as source,
        pytest.raises(SourceError, match="empty"),
    ):
        source.fetch(SearchQuery(text="   "))

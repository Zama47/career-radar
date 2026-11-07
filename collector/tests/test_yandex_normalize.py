from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import httpx

from collector.config import CollectorConfig
from collector.sources.base import SourceQuery
from collector.sources.yandex import YandexSource

FIXTURE = Path(__file__).parent / "fixtures" / "yandex_page.json"


def load_fixture() -> dict[str, Any]:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def make_source(handler: Any) -> YandexSource:
    config = CollectorConfig.from_env({})
    client = httpx.Client(
        transport=httpx.MockTransport(handler), base_url=config.yandex_base_url
    )
    return YandexSource(config, client=client, sleep=lambda _: None)


def test_fetch_maps_yandex_results_and_sends_city_hint() -> None:
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
    assert requests[0].url.params["page_size"] == "40"
    assert requests[0].url.params["city"] == "moscow"

    first = raws[0]
    assert first.source == "YANDEX"
    assert first.external_id == "15322"
    assert first.title == "MultiTrack — новый формат найма для опытных бэкендеров"
    assert first.company == "Яндекс"
    assert first.location == "Москва, Санкт-Петербург"
    assert first.url.endswith(
        "/jobs/vacancies/multitrack-_-noviy-format-nayma-dlya-opitnih-bekenderov-15322"
    )
    assert first.published_at is not None
    assert first.published_at.year == 2026


def test_fetch_follows_the_cursor_next_link() -> None:
    payload = load_fixture()
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        requests.append(request)
        if len(requests) == 1:
            return httpx.Response(200, json=payload)
        return httpx.Response(200, json={"results": [payload["results"][0]], "next": None})

    with make_source(handler) as source:
        raws = source.fetch(SourceQuery(max_pages=2, city="Москва"))

    assert len(requests) == 2
    assert "cursor=" in str(requests[1].url)
    assert len(raws) == 5


def test_fetch_applies_query_client_side() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        raws = source.fetch(SourceQuery(max_pages=1, text="DevOps"))

    assert [raw.title for raw in raws] == ["Старший DevOps-инженер"]


def test_normalize_maps_remote_type_and_profession_from_skills() -> None:
    payload = load_fixture()

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        drafts = [source.normalize(raw) for raw in source.fetch(SourceQuery(max_pages=1))]

    assert [draft.remote_type for draft in drafts] == [
        "HYBRID",
        "OFFICE",
        "HYBRID",
        "REMOTE",
        "REMOTE",
    ]
    assert [draft.seniority for draft in drafts] == [
        "UNKNOWN",
        "UNKNOWN",
        "SENIOR",
        "UNKNOWN",
        "UNKNOWN",
    ]
    assert [draft.profession for draft in drafts] == [
        "JAVA_BACKEND",
        "OTHER",
        "DEVOPS",
        "OTHER",
        "OTHER",
    ]
    assert drafts[1].location is None
    assert drafts[1].company == "Яндекс"


def test_normalize_uses_numeric_tail_of_slug_as_external_id() -> None:
    payload = load_fixture()
    payload["results"][0]["publication_slug_url"] = "role-without-numeric-tail"

    with make_source(lambda request: httpx.Response(200, json=payload)) as source:
        raw = source.fetch(SourceQuery(max_pages=1))[0]

    assert raw.external_id == "15322"

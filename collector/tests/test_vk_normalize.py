from __future__ import annotations

from pathlib import Path
from typing import Any

import httpx
import pytest

from collector.config import CollectorConfig
from collector.sources.base import SourceError, SourceQuery
from collector.sources.vk import VkSource

FIXTURE = Path(__file__).parent / "fixtures" / "vk_page.html"


def load_fixture() -> str:
    return FIXTURE.read_text(encoding="utf-8")


def make_source(handler: Any) -> VkSource:
    config = CollectorConfig.from_env({})
    client = httpx.Client(
        transport=httpx.MockTransport(handler), base_url=config.vk_base_url
    )
    return VkSource(config, client=client, sleep=lambda _: None)


def html_response(body: str) -> httpx.Response:
    return httpx.Response(200, text=body, headers={"content-type": "text/html; charset=utf-8"})


def test_fetch_parses_server_rendered_cards() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        requests.append(request)
        return html_response(load_fixture())

    with make_source(handler) as source:
        raws = source.fetch(SourceQuery(max_pages=3))

    assert len(raws) == 3
    assert len(requests) == 1
    assert requests[0].url.path == "/vacancy/"

    first = raws[0]
    assert first.source == "VK"
    assert first.external_id == "55285"
    assert first.title == "Руководитель группы технических систем безопасности"
    assert first.company == "VK"
    assert first.location == "Москва"
    assert first.description == "Подразделение: Служба безопасности"
    assert first.url.endswith("/vacancy/55285/")


def test_fetch_filters_query_and_limits_items() -> None:
    body = load_fixture()

    with make_source(lambda request: html_response(body)) as source:
        procurement = source.fetch(SourceQuery(text="закупкам"))
        limited = source.fetch(SourceQuery(max_items=1))

    assert [raw.title for raw in procurement] == [
        "Менеджер по закупкам",
        "Специалист по закупкам",
    ]
    assert len(limited) == 1


def test_normalize_maps_work_format_and_seniority() -> None:
    body = load_fixture()

    with make_source(lambda request: html_response(body)) as source:
        drafts = [source.normalize(raw) for raw in source.fetch(SourceQuery())]

    assert [draft.remote_type for draft in drafts] == ["OFFICE", "HYBRID", "OFFICE"]
    assert [draft.seniority for draft in drafts] == ["LEAD", "UNKNOWN", "UNKNOWN"]
    assert [draft.profession for draft in drafts] == ["OTHER", "OTHER", "OTHER"]
    assert drafts[1].description == "Подразделение: Блок по экономике и финансам"
    assert drafts[0].company == "VK"


def test_unknown_markup_fails_clearly() -> None:
    body = "<html><body><p>no vacancies here</p></body></html>"

    with (
        make_source(lambda request: html_response(body)) as source,
        pytest.raises(SourceError, match="no recognized vacancy cards"),
    ):
        source.fetch(SourceQuery())

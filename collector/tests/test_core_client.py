from __future__ import annotations

import json

import httpx
import pytest

from collector.config import CollectorConfig
from collector.core_client import INTERNAL_HEADER, CoreApiError, CoreClient
from collector.models import VacancyDraft


def make_client(handler: httpx.MockTransport) -> CoreClient:
    config = CollectorConfig.from_env({"INTERNAL_TOKEN": "test-token"})
    return CoreClient(config, client=httpx.Client(transport=handler, base_url=config.core_api_url))


def test_ping_returns_payload() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(200, json={"status": "ok"}))

    with make_client(transport) as client:
        assert client.ping() == {"status": "ok"}


def test_upsert_sends_internal_token_and_payload() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["token"] = request.headers.get(INTERNAL_HEADER)
        captured["body"] = request.read().decode()
        return httpx.Response(200, json={"received": 1, "created": 1, "updated": 0, "skipped": 0})

    draft = VacancyDraft(
        source_code="hh",
        external_id="123",
        title="Python Developer",
        url="https://hh.ru/vacancy/123",
        company_name="Acme",
        remote=True,
    )

    with make_client(httpx.MockTransport(handler)) as client:
        result = client.upsert_vacancies([draft])

    assert captured["url"] == "http://localhost:8080/internal/v1/vacancies:upsert"
    assert captured["token"] == "test-token"
    body = json.loads(str(captured["body"]))
    assert body["source"] == "HH"
    assert body["items"][0]["external_id"] == "123"
    assert result.created == 1


def test_upsert_groups_drafts_by_source() -> None:
    requests: list[dict[str, object]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.read())
        requests.append(payload)
        count = len(payload["items"])
        return httpx.Response(
            200, json={"received": count, "created": count, "updated": 0, "skipped": 0}
        )

    def draft(external_id: str, source_code: str) -> VacancyDraft:
        return VacancyDraft(
            source_code=source_code,
            external_id=external_id,
            title="Python Developer",
            url=f"https://example.com/{external_id}",
        )

    with make_client(httpx.MockTransport(handler)) as client:
        result = client.upsert_vacancies(
            [draft("1", "hh"), draft("2", "vk"), draft("3", "hh")]
        )

    assert [payload["source"] for payload in requests] == ["HH", "VK"]
    assert len(requests[0]["items"]) == 2
    assert len(requests[1]["items"]) == 1
    assert result.received == 3
    assert result.created == 3


def test_non_2xx_raises_core_api_error() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(401, text="unauthorized"))

    with (
        make_client(transport) as client,
        pytest.raises(CoreApiError) as exc_info,
    ):
        client.ping()

    assert exc_info.value.status_code == 401


def test_transport_failure_raises_core_api_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    with (
        make_client(httpx.MockTransport(handler)) as client,
        pytest.raises(CoreApiError, match="request failed"),
    ):
        client.ping()

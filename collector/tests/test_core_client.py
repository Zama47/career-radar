from __future__ import annotations

import json

import httpx
import pytest
import respx

from collector.config import CollectorConfig
from collector.core_client import INTERNAL_HEADER, CoreApiError, CoreClient
from collector.models import VacancyDraft

UPSERT_URL = "http://localhost:8080/internal/v1/vacancies:upsert"
UPSERT_OK = {"received": 1, "created": 1, "updated": 0, "skipped": 0}


def make_config() -> CollectorConfig:
    return CollectorConfig.from_env({"INTERNAL_TOKEN": "test-token"})


def draft(external_id: str, *, source: str = "hh") -> VacancyDraft:
    return VacancyDraft(
        source=source,
        external_id=external_id,
        title="Python Developer",
        url=f"https://hh.ru/vacancy/{external_id}",
        company="Acme",
        seniority="JUNIOR",
        profession="JAVA_BACKEND",
    )


@respx.mock
def test_ping_returns_payload() -> None:
    respx.get("http://localhost:8080/api/v1/ping").mock(
        return_value=httpx.Response(200, json={"status": "ok"})
    )

    with CoreClient(make_config()) as client:
        assert client.ping() == {"status": "ok"}


@respx.mock
def test_post_batch_sends_token_and_camel_case_payload() -> None:
    route = respx.post(UPSERT_URL).mock(return_value=httpx.Response(200, json=UPSERT_OK))

    with CoreClient(make_config()) as client:
        result = client.post_batch([draft("123")])

    request = route.calls[0].request
    assert request.headers[INTERNAL_HEADER] == "test-token"
    assert "Authorization" not in request.headers

    body = json.loads(request.content)
    assert body["source"] == "HH"
    assert body["items"][0]["externalId"] == "123"
    assert "external_id" not in body["items"][0]
    assert result.created == 1


@respx.mock
def test_post_batch_splits_requests_into_chunks_of_50() -> None:
    sizes: list[int] = []

    def responder(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        count = len(payload["items"])
        sizes.append(count)
        return httpx.Response(
            200, json={"received": count, "created": count, "updated": 0, "skipped": 0}
        )

    route = respx.post(UPSERT_URL).mock(side_effect=responder)

    with CoreClient(make_config()) as client:
        result = client.post_batch([draft(str(index)) for index in range(120)])

    assert route.call_count == 3
    assert sizes == [50, 50, 20]
    assert result.received == 120
    assert result.created == 120


@respx.mock
def test_post_batch_groups_drafts_by_source() -> None:
    sources: list[str] = []

    def responder(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        sources.append(payload["source"])
        count = len(payload["items"])
        return httpx.Response(
            200, json={"received": count, "created": count, "updated": 0, "skipped": 0}
        )

    route = respx.post(UPSERT_URL).mock(side_effect=responder)

    with CoreClient(make_config()) as client:
        client.post_batch([draft("1", source="hh"), draft("2", source="hh"), draft("3")])

    assert route.call_count == 1
    assert sources == ["HH"]


@respx.mock
def test_retries_on_server_error(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("tenacity.nap.sleep", lambda _seconds: None)
    route = respx.post(UPSERT_URL).mock(
        side_effect=[
            httpx.Response(503, text="busy"),
            httpx.Response(200, json=UPSERT_OK),
        ]
    )

    with CoreClient(make_config()) as client:
        result = client.post_batch([draft("123")])

    assert route.call_count == 2
    assert result.created == 1


@respx.mock
def test_does_not_retry_client_errors() -> None:
    route = respx.post(UPSERT_URL).mock(return_value=httpx.Response(401, text="unauthorized"))

    with CoreClient(make_config()) as client, pytest.raises(CoreApiError) as exc_info:
        client.post_batch([draft("123")])

    assert route.call_count == 1
    assert exc_info.value.status_code == 401


@respx.mock
def test_non_2xx_raises_core_api_error(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("tenacity.nap.sleep", lambda _seconds: None)
    respx.get("http://localhost:8080/api/v1/ping").mock(
        return_value=httpx.Response(500, text="boom")
    )

    with CoreClient(make_config()) as client, pytest.raises(CoreApiError) as exc_info:
        client.ping()

    assert exc_info.value.status_code == 500


def test_transport_failure_raises_core_api_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    transport = httpx.MockTransport(handler)
    client = httpx.Client(transport=transport, base_url="http://localhost:8080")

    with pytest.raises(CoreApiError, match="request failed"):
        CoreClient(make_config(), client=client).ping()

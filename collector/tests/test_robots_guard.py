from __future__ import annotations

import httpx
import pytest

from collector.config import CollectorConfig
from collector.sources.base import HttpSource, RobotsGuard, SourceError


def make_guard(text: str, *, status_code: int = 200) -> tuple[RobotsGuard, list[str]]:
    requested: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requested.append(str(request.url))
        return httpx.Response(status_code, text=text)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    guard = RobotsGuard(client, user_agent="CareerRadar/0.1 (test)")
    return guard, requested


def test_disallowed_path_raises_without_requesting_it() -> None:
    guard, requested = make_guard("User-agent: *\nDisallow: /private\n")

    with pytest.raises(SourceError, match="robots.txt disallows"):
        guard.ensure_allowed("https://example.test/private/vacancies")

    assert requested == ["https://example.test/robots.txt"]


def test_wildcard_disallow_matches_query_string() -> None:
    guard, _ = make_guard("User-agent: *\nDisallow: *search=*\n")

    with pytest.raises(SourceError):
        guard.ensure_allowed("https://example.test/vacancies?search=java")

    guard.ensure_allowed("https://example.test/vacancies?limit=25&offset=0")


def test_longer_allow_wins_over_disallow() -> None:
    guard, _ = make_guard(
        "User-agent: *\n"
        "Disallow: /vacancies\n"
        "Allow: /vacancies?cat=internships\n"
    )

    guard.ensure_allowed("https://example.test/vacancies?cat=internships")
    with pytest.raises(SourceError):
        guard.ensure_allowed("https://example.test/vacancies?cat=sales")


def test_missing_robots_txt_means_no_restrictions() -> None:
    guard, requested = make_guard("", status_code=404)

    guard.ensure_allowed("https://example.test/anything")

    assert requested == ["https://example.test/robots.txt"]


def test_specific_user_agent_group_takes_precedence() -> None:
    guard, _ = make_guard(
        "User-agent: *\n"
        "Disallow: /\n"
        "\n"
        "User-agent: CareerRadar\n"
        "Disallow: /admin\n"
    )

    guard.ensure_allowed("https://example.test/vacancies")
    with pytest.raises(SourceError):
        guard.ensure_allowed("https://example.test/admin")


def test_http_source_never_fetches_a_disallowed_url() -> None:
    requested: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requested.append(str(request.url))
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nDisallow: /private\n")
        return httpx.Response(200, text="vacancy payload")

    config = CollectorConfig.from_env({})
    client = httpx.Client(transport=httpx.MockTransport(handler))
    source = HttpSource(config, client=client, sleep=lambda _: None)

    with pytest.raises(SourceError):
        source.get("https://example.test/private")

    assert requested == ["https://example.test/robots.txt"]


def test_http_source_ignores_captcha_words_inside_scripts() -> None:
    body = (
        "<html><head><title>Вакансии</title>"
        "<script>var captchaWidget = 'https://captcha.example';</script></head>"
        "<body><h1>Список вакансий</h1></body></html>"
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        return httpx.Response(200, text=body, headers={"content-type": "text/html"})

    config = CollectorConfig.from_env({})
    client = httpx.Client(transport=httpx.MockTransport(handler))
    source = HttpSource(config, client=client, sleep=lambda _: None)

    assert source.get("https://example.test/vacancies").status_code == 200


def test_http_source_stops_on_visible_captcha_page() -> None:
    body = "<html><body><h1>Подтвердите, что вы не робот</h1></body></html>"

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        return httpx.Response(200, text=body, headers={"content-type": "text/html"})

    config = CollectorConfig.from_env({})
    client = httpx.Client(transport=httpx.MockTransport(handler))
    source = HttpSource(config, client=client, sleep=lambda _: None)

    with pytest.raises(SourceError, match="captcha"):
        source.get("https://example.test/vacancies")


def test_http_source_stops_on_403_without_retrying_it() -> None:
    requested: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requested.append(str(request.url))
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        return httpx.Response(403, text="forbidden")

    config = CollectorConfig.from_env({})
    client = httpx.Client(transport=httpx.MockTransport(handler))
    source = HttpSource(config, client=client, sleep=lambda _: None)

    with pytest.raises(SourceError, match="does not bypass"):
        source.get("https://example.test/vacancies")

    assert requested.count("https://example.test/vacancies") == 1


def test_http_source_waits_between_requests() -> None:
    sleeps: list[float] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\n")
        return httpx.Response(200, text="ok", headers={"content-type": "text/plain"})

    config = CollectorConfig.from_env({})
    client = httpx.Client(transport=httpx.MockTransport(handler))
    source = HttpSource(config, client=client, sleep=sleeps.append)

    source.get("https://example.test/one")
    source.get("https://example.test/two")

    assert sleeps
    assert all(0 < seconds <= 1.5 for seconds in sleeps)

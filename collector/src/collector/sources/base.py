"""Source adapter contract, HTTP plumbing and robots.txt enforcement.

Every company adapter inherits from :class:`HttpSource`, which centralises the rules that are
easy to get wrong per site: one request per second per host, retries for transient failures,
honest error messages for 403/captcha, and a real ``robots.txt`` check before the first fetch.
"""

from __future__ import annotations

import logging
import re
import time
from collections.abc import Callable, Mapping
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from typing import Any, Protocol, runtime_checkable
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field

from collector.config import CollectorConfig
from collector.models import RawVacancy, VacancyDraft

log = logging.getLogger(__name__)

MAX_RETRY_AFTER_SECONDS = 60.0
RETRYABLE_STATUS_CODES = frozenset({429, 500, 502, 503, 504})
CAPTCHA_MARKERS = (
    "captcha",
    "showcaptcha",
    "я не робот",
    "подтвердите, что вы не робот",
    "are you a robot",
    "verify you are human",
)
_CAPTCHA_SCAN_LIMIT = 20_000


class SourceError(RuntimeError):
    """Raised when a source cannot be fetched or parsed.

    Reported to core-api as a failed source run.
    """


class SourceQuery(BaseModel):
    """Parameters for one company career-site collection run."""

    model_config = ConfigDict(frozen=True, str_strip_whitespace=True)

    text: str | None = None
    city: str | None = "Москва"
    only_internships: bool = False
    max_pages: int = Field(default=1, ge=1, le=100)
    max_items: int | None = Field(default=None, ge=1)
    per_page: int = Field(default=100, ge=1, le=200)


@runtime_checkable
class CompanySource(Protocol):
    """A company adapter fetches raw listings and maps each one to a normalized draft.

    Rules for implementations:
    - respect robots.txt and the site's terms of service;
    - use the configured honest User-Agent and a low request rate;
    - never bypass captchas, auth walls, or paywalls.
    """

    code: str
    base_url: str

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        """Fetch raw listings. Must not write to the database."""

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        """Map one raw listing to a VacancyDraft accepted by core-api."""


# --------------------------------------------------------------------------- robots.txt


class RobotsGuard:
    """Downloads ``robots.txt`` once per origin and refuses disallowed paths.

    The parser supports the wildcard and ``$`` patterns used by real career sites (MTS, for
    example, disallows ``*search=*``), which the stdlib ``robotparser`` does not.
    """

    def __init__(
        self,
        client: httpx.Client,
        *,
        user_agent: str,
    ) -> None:
        self._client = client
        self._user_agent = user_agent
        self._cache: dict[str, list[tuple[bool, str]] | None] = {}

    def ensure_allowed(self, url: str) -> None:
        parts = urlsplit(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        target = parts.path or "/"
        if parts.query:
            target = f"{target}?{parts.query}"

        if origin not in self._cache:
            self._cache[origin] = self._load(origin)

        rules = self._cache[origin]
        if rules and not _rules_allow(rules, target):
            raise SourceError(
                f"{origin} robots.txt disallows {parts.path}; refusing to fetch it"
            )

    def _load(self, origin: str) -> list[tuple[bool, str]] | None:
        robots_url = f"{origin}/robots.txt"
        try:
            response = self._client.get(
                robots_url,
                headers={"User-Agent": self._user_agent, "Accept": "text/plain"},
            )
        except httpx.HTTPError as exc:
            raise SourceError(f"could not read robots.txt for {origin}: {exc}") from exc

        # A missing robots.txt means "no restrictions" per the de-facto standard.
        if response.status_code in (404, 410):
            return None
        if response.status_code >= 400:
            raise SourceError(
                f"{origin} robots.txt returned {response.status_code}; refusing to fetch"
            )
        return _parse_robots(response.text, self._user_agent)


def _parse_robots(text: str, user_agent: str) -> list[tuple[bool, str]]:
    groups: list[tuple[list[str], list[tuple[bool, str]]]] = []
    agents: list[str] = []
    rules: list[tuple[bool, str]] = []

    def flush() -> None:
        nonlocal agents, rules
        if agents:
            groups.append((agents, rules))
        agents = []
        rules = []

    for raw_line in text.splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if not line or ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lower()
        value = value.strip()

        if key == "user-agent":
            if rules:
                flush()
            agents.append(value)
        elif key in ("allow", "disallow") and agents:
            rules.append((key == "allow", value))
    flush()

    ua = user_agent.lower()
    specific: list[tuple[bool, str]] = []
    wildcard: list[tuple[bool, str]] = []
    for group_agents, group_rules in groups:
        tokens = [agent.lower() for agent in group_agents]
        if any(token != "*" and token and token in ua for token in tokens):
            specific.extend(group_rules)
        elif "*" in tokens:
            wildcard.extend(group_rules)
    return specific or wildcard


def _rules_allow(rules: list[tuple[bool, str]], target: str) -> bool:
    best_score = -1
    best_allow = True
    for allow, pattern in rules:
        if not pattern:
            continue
        if not _pattern_matches(pattern, target):
            continue
        score = len(pattern)
        if score > best_score or (score == best_score and allow):
            best_score = score
            best_allow = allow
    return best_allow


def _pattern_matches(pattern: str, target: str) -> bool:
    anchored = pattern.endswith("$")
    body = pattern[:-1] if anchored else pattern
    regex = "".join(".*" if char == "*" else re.escape(char) for char in body)
    if anchored:
        regex += "$"
    return re.match(regex, target) is not None


# --------------------------------------------------------------------------- HTTP source


class HttpSource:
    """Shared HTTP behaviour for career-site adapters."""

    code: str = ""
    base_url: str = ""

    def __init__(
        self,
        config: CollectorConfig,
        *,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        max_attempts: int = 3,
        robots_guard: RobotsGuard | None = None,
    ) -> None:
        self._config = config
        self._owns_client = client is None
        self._client = client or httpx.Client(
            timeout=config.request_timeout,
            headers=config.http_headers(),
            follow_redirects=True,
        )
        self._sleep = sleep
        self._max_attempts = max_attempts
        self._last_request_at: dict[str, float] = {}
        self._robots = robots_guard or RobotsGuard(
            client=self._client, user_agent=config.user_agent
        )

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> HttpSource:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    def get(
        self,
        url: str,
        *,
        params: Mapping[str, Any] | None = None,
        headers: Mapping[str, str] | None = None,
    ) -> httpx.Response:
        """GET a URL after a robots check, with rate limiting and retries."""
        request_url = httpx.URL(url, params=params) if params else httpx.URL(url)
        host = request_url.host

        for attempt in range(1, self._max_attempts + 1):
            self._throttle(host)
            self._robots.ensure_allowed(str(request_url))
            self._throttle(host)

            try:
                response = self._client.get(str(request_url), headers=headers)
            except httpx.HTTPError as exc:
                raise SourceError(f"{host} request failed: {exc}") from exc

            if response.status_code in RETRYABLE_STATUS_CODES:
                if attempt == self._max_attempts:
                    raise SourceError(
                        f"{host} returned {response.status_code} after {attempt} attempt(s)"
                    )
                delay = self._retry_after(response) or float(2**attempt)
                log.warning(
                    "%s returned %d, retrying in %.0fs (attempt %d/%d)",
                    host,
                    response.status_code,
                    delay,
                    attempt,
                    self._max_attempts,
                )
                self._sleep(delay)
                continue

            if response.status_code in (401, 403, 439):
                raise SourceError(
                    f"{host} returned {response.status_code}: access denied. "
                    "The adapter stops here and does not bypass the protection."
                )
            if response.status_code >= 400:
                raise SourceError(f"{host} returned {response.status_code} for {url}")
            if _looks_like_captcha(response):
                raise SourceError(
                    f"{host} returned a captcha/anti-bot page. "
                    "The adapter stops here and does not bypass the protection."
                )
            return response

        raise SourceError(f"{host} request failed after {self._max_attempts} attempts")

    def get_json(
        self,
        url: str,
        *,
        params: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        response = self.get(url, params=params)
        try:
            payload = response.json()
        except ValueError as exc:
            raise SourceError(f"{httpx.URL(url).host} returned a non-JSON response") from exc
        if not isinstance(payload, dict):
            raise SourceError(f"{httpx.URL(url).host} returned an unexpected JSON payload")
        return payload

    def _throttle(self, host: str | None) -> None:
        key = host or ""
        last = self._last_request_at.get(key)
        if last is not None:
            remaining = self._config.rate_limit_seconds - (time.monotonic() - last)
            if remaining > 0:
                self._sleep(remaining)
        self._last_request_at[key] = time.monotonic()

    def _retry_after(self, response: httpx.Response) -> float | None:
        raw = response.headers.get("Retry-After")
        if not raw:
            return None

        try:
            seconds = float(raw)
        except ValueError:
            try:
                target = parsedate_to_datetime(raw)
            except (TypeError, ValueError):
                return None
            if target.tzinfo is None:
                target = target.replace(tzinfo=UTC)
            seconds = (target - datetime.now(UTC)).total_seconds()

        return max(1.0, min(seconds, MAX_RETRY_AFTER_SECONDS))


def _looks_like_captcha(response: httpx.Response) -> bool:
    content_type = response.headers.get("content-type", "").lower()
    if "html" not in content_type:
        return False
    # Scripts often mention captcha widgets without the page being a challenge. Only inspect
    # visible text (title and body), not JavaScript or stylesheet contents.
    raw = response.text[:_CAPTCHA_SCAN_LIMIT]
    visible = re.sub(r"<(script|style)\b.*?</\1>", " ", raw, flags=re.IGNORECASE | re.DOTALL)
    visible = re.sub(r"<[^>]+>", " ", visible)
    visible = re.sub(r"\s+", " ", visible).lower()
    return any(marker in visible for marker in CAPTCHA_MARKERS)

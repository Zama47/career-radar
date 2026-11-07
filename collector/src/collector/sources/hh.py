"""hh.ru source adapter built on the official public API (https://api.hh.ru)."""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from typing import Any

import httpx

from collector.config import CollectorConfig
from collector.models import RawVacancy, SearchQuery, VacancyDraft
from collector.normalize import (
    clean_html,
    detect_profession,
    detect_remote,
    detect_seniority,
    normalize_title,
    parse_salary,
)
from collector.sources.base import SourceError

log = logging.getLogger(__name__)

HH_VACANCIES_PATH = "/vacancies"
MAX_PER_PAGE = 100
MAX_RETRY_AFTER_SECONDS = 60.0
_WORK_FORMAT_BY_ID = {"REMOTE": "REMOTE", "HYBRID": "HYBRID", "ON_SITE": "OFFICE"}
_ACCESS_HINT = (
    "hh.ru requires an application token for GET /vacancies: anonymous access was closed in "
    "April 2026. Register an app at https://dev.hh.ru and set HH_ACCESS_TOKEN."
)
_USER_AGENT_HINT = (
    "hh.ru rejected the User-Agent. HH_USER_AGENT must identify the app and a real contact."
)


def _is_user_agent_error(response: httpx.Response) -> bool:
    body = response.text.lower()
    return "bad_user_agent" in body or "user-agent" in body


def _short_error(response: httpx.Response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return response.text.strip()[:120] or "no details"
    errors = payload.get("errors") if isinstance(payload, dict) else None
    if isinstance(errors, list) and errors and isinstance(errors[0], dict):
        return str(errors[0].get("type") or errors[0].get("value") or "unknown")[:120]
    return str(payload)[:120]


class HhSource:
    """Fetches vacancy pages from api.hh.ru and normalizes them for core-api.

    Rate limiting is enforced between *all* outgoing requests (hh.ru asks for at most one
    request per second), and ``429`` responses are honoured via ``Retry-After``.
    """

    code = "HH"

    def __init__(
        self,
        config: CollectorConfig,
        *,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        max_attempts: int = 3,
    ) -> None:
        self._config = config
        self._owns_client = client is None
        self._client = client or httpx.Client(
            base_url=config.hh_base_url,
            timeout=config.request_timeout,
            headers=config.hh_headers(),
        )
        self._sleep = sleep
        self._max_attempts = max_attempts
        self._last_request_at: float | None = None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> HhSource:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    def fetch(self, query: SearchQuery) -> list[RawVacancy]:
        """Page through ``/vacancies`` until ``max_pages``, ``max_items`` or the end."""
        text = query.text.strip()
        if not text:
            raise SourceError("search text must not be empty")

        per_page = max(1, min(query.per_page, MAX_PER_PAGE))
        collected: list[RawVacancy] = []

        for page in range(max(query.max_pages, 1)):
            payload = self._get(
                HH_VACANCIES_PATH,
                {
                    "text": text,
                    "area": query.area,
                    "per_page": per_page,
                    "page": page,
                    "only_with_salary": "false",
                    "order_by": "publication_time",
                },
            )
            page_items = payload.get("items")
            if not isinstance(page_items, list):
                raise SourceError("hh.ru response has no 'items' array")

            for item in page_items:
                raw = self._parse_item(item)
                if raw is None:
                    continue
                collected.append(raw)
                if query.max_items is not None and len(collected) >= query.max_items:
                    return collected

            found = payload.get("found")
            if len(page_items) < per_page:
                break
            if isinstance(found, int) and (page + 1) * per_page >= found:
                break

        log.info(
            "hh search finished: query_len=%d pages=%d items=%d",
            len(text),
            max(query.max_pages, 1),
            len(collected),
        )
        return collected

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        title = normalize_title(raw.title)
        description = clean_html(raw.description)
        remote_type = detect_remote(title, description)
        if remote_type == "UNKNOWN":
            remote_type = self._remote_from_payload(raw.raw_json)

        return VacancyDraft(
            source=self.code,
            external_id=raw.external_id,
            title=title or raw.title,
            url=raw.url,
            company=raw.company,
            description=description or None,
            location=raw.location,
            salary_from=raw.salary_from,
            salary_to=raw.salary_to,
            currency=raw.currency,
            seniority=detect_seniority(title),
            remote_type=remote_type,
            profession=detect_profession(title, description),
            remote=remote_type == "REMOTE",
            published_at=raw.published_at,
            raw_json=raw.raw_json,
        )

    def _parse_item(self, item: Any) -> RawVacancy | None:
        if not isinstance(item, dict):
            return None

        external_id = item.get("id")
        title = item.get("name")
        url = item.get("alternate_url")
        if external_id is None or not title or not url:
            log.debug("skipping hh item without id/name/alternate_url")
            return None

        employer = item.get("employer") or {}
        area = item.get("area") or {}
        salary_from, salary_to, currency = parse_salary(item.get("salary"))

        return RawVacancy(
            source=self.code,
            external_id=str(external_id),
            url=str(url),
            title=str(title),
            company=employer.get("name") if isinstance(employer, dict) else None,
            description=self._description(item),
            location=area.get("name") if isinstance(area, dict) else None,
            salary_from=salary_from,
            salary_to=salary_to,
            currency=currency,
            published_at=self._published_at(item.get("published_at")),
            raw_json=item,
        )

    def _description(self, item: dict[str, Any]) -> str | None:
        """Prefer a full description if present; otherwise join the search snippet.

        The ``/vacancies`` list endpoint returns snippets, not full descriptions. Fetching
        every vacancy detail page would multiply request volume, so the adapter keeps the
        snippet (requirement + responsibility) and leaves detail fetching to a later stage.
        """
        description = item.get("description")
        if isinstance(description, str) and description.strip():
            return description

        snippet = item.get("snippet")
        if not isinstance(snippet, dict):
            return None

        parts = [
            snippet.get(key)
            for key in ("requirement", "responsibility")
            if isinstance(snippet.get(key), str) and snippet.get(key).strip()
        ]
        return "\n\n".join(str(part) for part in parts) or None

    def _remote_from_payload(self, payload: dict[str, Any]) -> str:
        """Fallback to hh.ru's structured work-format fields when text is silent."""
        work_format = payload.get("work_format")
        if isinstance(work_format, list):
            for entry in work_format:
                if isinstance(entry, dict):
                    code = _WORK_FORMAT_BY_ID.get(str(entry.get("id", "")).upper())
                    if code:
                        return code

        schedule = payload.get("schedule")
        if isinstance(schedule, dict):
            name = str(schedule.get("name", "")).lower()
            if "удал" in name or "remote" in name:
                return "REMOTE"
        return "UNKNOWN"

    def _published_at(self, value: Any) -> datetime | None:
        if not isinstance(value, str) or not value.strip():
            return None
        try:
            parsed = datetime.fromisoformat(value)
        except ValueError:
            return None
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=UTC)

    def _get(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        for attempt in range(1, self._max_attempts + 1):
            self._throttle()
            try:
                response = self._client.get(path, params=params)
            except httpx.HTTPError as exc:
                raise SourceError(f"hh.ru request failed: {exc}") from exc

            if response.status_code == 429:
                if attempt == self._max_attempts:
                    raise SourceError("hh.ru rate limit is still active after retries (429)")
                delay = self._retry_after(response) or float(2 * attempt)
                log.warning(
                    "hh.ru returned 429, retrying in %.0fs (attempt %d/%d)",
                    delay,
                    attempt,
                    self._max_attempts,
                )
                self._sleep(delay)
                continue

            if response.status_code >= 500:
                if attempt == self._max_attempts:
                    raise SourceError(f"hh.ru returned {response.status_code} after retries")
                delay = float(2**attempt)
                log.warning(
                    "hh.ru returned %d, retrying in %.0fs (attempt %d/%d)",
                    response.status_code,
                    delay,
                    attempt,
                    self._max_attempts,
                )
                self._sleep(delay)
                continue

            if response.status_code in (400, 401, 403):
                if response.status_code in (401, 403):
                    raise SourceError(f"hh.ru returned {response.status_code}. {_ACCESS_HINT}")
                if _is_user_agent_error(response):
                    raise SourceError(f"hh.ru returned 400. {_USER_AGENT_HINT}")
                raise SourceError(
                    f"hh.ru returned {response.status_code} for {path}: {_short_error(response)}"
                )
            if response.status_code >= 400:
                raise SourceError(f"hh.ru returned {response.status_code} for {path}")

            try:
                payload = response.json()
            except ValueError as exc:
                raise SourceError("hh.ru returned a non-JSON response") from exc
            if not isinstance(payload, dict):
                raise SourceError("hh.ru returned an unexpected payload")
            return payload

        raise SourceError("hh.ru request failed after retries")

    def _throttle(self) -> None:
        if self._last_request_at is not None:
            elapsed = time.monotonic() - self._last_request_at
            remaining = self._config.rate_limit_seconds - elapsed
            if remaining > 0:
                self._sleep(remaining)
        self._last_request_at = time.monotonic()

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

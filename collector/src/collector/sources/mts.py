"""MTS career site adapter built on the public JSON API used by job.mts.ru."""

from __future__ import annotations

import logging
import re
from typing import Any

from collector.models import RawVacancy, VacancyDraft
from collector.normalize import (
    clean_html,
    detect_profession,
    detect_remote,
    detect_seniority,
    matches_query,
    normalize_currency,
    normalize_title,
    parse_datetime,
    text_from_parts,
)
from collector.sources.base import HttpSource, SourceError, SourceQuery

log = logging.getLogger(__name__)

MTS_API_PATH = "/api/v2/vacancies"
DEFAULT_COMPANY = "МТС"


class MtsSource(HttpSource):
    """Fetches ``/api/v2/vacancies`` pages and normalizes them for core-api.

    The API returns 25 items per page and randomises the order between requests. Pagination
    therefore uses ``pagination[page]`` (the parameter the site itself sends), deduplicates by
    ``id``, and never claims that a page is stable.
    """

    code = "MTS"

    def __init__(self, config: Any, **kwargs: Any) -> None:
        super().__init__(config, **kwargs)
        self.base_url = config.mts_base_url

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        collected: dict[str, RawVacancy] = {}
        limit = min(query.per_page, 100)

        for page in range(1, max(query.max_pages, 1) + 1):
            payload = self.get_json(
                f"{self.base_url}{MTS_API_PATH}",
                params={"pagination[page]": page, "limit": limit},
            )
            items = payload.get("data")
            if not isinstance(items, list):
                raise SourceError("job.mts.ru response has no 'data' array")

            for item in items:
                raw = self._parse_item(item)
                if raw is None:
                    continue
                if query.text and not matches_query(
                    f"{raw.title} {raw.description or ''}", query.text
                ):
                    continue
                if raw.external_id in collected:
                    continue
                collected[raw.external_id] = raw
                if query.max_items is not None and len(collected) >= query.max_items:
                    return list(collected.values())

            pagination = (payload.get("meta") or {}).get("pagination") or {}
            page_count = pagination.get("pageCount")
            if not items or (isinstance(page_count, int) and page >= page_count):
                break

        log.info("mts fetch finished: pages=%d items=%d", query.max_pages, len(collected))
        return list(collected.values())

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        title = normalize_title(raw.title) or raw.title
        description = clean_html(raw.description)
        seniority = _seniority(raw.raw_json, title)
        remote_type = _remote_type(raw.raw_json, title, description)

        return VacancyDraft(
            source=self.code,
            external_id=raw.external_id,
            title=title,
            url=raw.url,
            company=raw.company,
            description=description or None,
            location=raw.location,
            salary_from=raw.salary_from,
            salary_to=raw.salary_to,
            currency=raw.currency,
            seniority=seniority,
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
        title = item.get("title")
        slug = item.get("slug")
        if external_id is None or not title or not slug:
            log.debug("skipping MTS item without id/title/slug")
            return None

        description = clean_html(
            "\n\n".join(
                part
                for part in (
                    text_from_parts(item.get("description")),
                    text_from_parts(item.get("requirements")),
                    text_from_parts(item.get("tasks")),
                )
                if part
            )
        )

        return RawVacancy(
            source=self.code,
            external_id=str(external_id),
            url=f"{self.base_url}/vacancy/{slug}",
            title=str(title),
            company=_title_of(item.get("organization")) or DEFAULT_COMPANY,
            description=description or None,
            location=_location(item),
            salary_from=_positive_float(item.get("salaryFrom")),
            salary_to=_positive_float(item.get("salaryTo")),
            currency=normalize_currency(_title_of(item.get("currency"))),
            published_at=parse_datetime(item.get("publishedAt")),
            raw_json=item,
        )


def _title_of(value: Any) -> str | None:
    if isinstance(value, dict):
        title = value.get("title")
        if isinstance(title, str) and title.strip():
            return title.strip()
    if isinstance(value, str) and value.strip():
        return value.strip()
    return None


def _location(item: dict[str, Any]) -> str | None:
    location = ", ".join(
        part
        for part in (_title_of(item.get("region")), text_from_parts(item.get("address")))
        if part
    )
    return location[:255] or None


def _positive_float(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def _seniority(item: dict[str, Any], title: str) -> str:
    experience = _title_of(item.get("experience")) or ""
    text = re.sub(r"[–—]", "-", experience.lower())
    if "без опыта" in text or "нет опыта" in text:
        return "INTERN"
    if "1-3" in text:
        return "JUNIOR"
    if "3-6" in text:
        return "MIDDLE"
    if "6+" in text or "более 6" in text:
        return "SENIOR"
    return detect_seniority(title)


def _remote_type(item: dict[str, Any], title: str, description: str) -> str:
    formats = " ".join(
        (_title_of(entry) or "").lower() for entry in item.get("workFormats") or []
    )
    if "удал" in formats or "remote" in formats:
        return "REMOTE"
    if "гибрид" in formats or "hybrid" in formats or "комбинир" in formats:
        return "HYBRID"
    if "офис" in formats or "office" in formats:
        return "OFFICE"
    return detect_remote(title, description)

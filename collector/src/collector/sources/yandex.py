"""Yandex careers adapter.

The public page is a Next.js app whose own JSON endpoint
(``/jobs/api/jobs/publications/``) is referenced in the SSR payload. Using that endpoint is
less brittle than scraping React Server Component markup and still requires no auth.
"""

from __future__ import annotations

import logging
from typing import Any
from urllib.parse import urljoin

from collector.models import RawVacancy, VacancyDraft
from collector.normalize import (
    clean_html,
    detect_profession,
    detect_remote,
    detect_seniority,
    matches_query,
    normalize_title,
    parse_datetime,
    text_from_parts,
)
from collector.sources.base import HttpSource, SourceError, SourceQuery

log = logging.getLogger(__name__)

PUBLICATIONS_PATH = "/jobs/api/jobs/publications/"
MAX_PAGE_SIZE = 40
DEFAULT_COMPANY = "Яндекс"

_CITY_SLUGS = {
    "москва": "moscow",
    "санкт-петербург": "saint-petersburg",
    "екатеринбург": "ekaterinburg",
    "новосибирск": "novosibirsk",
    "нижний новгород": "nizhniy-novgorod",
}


class YandexSource(HttpSource):
    """Fetches the careers JSON endpoint and follows its cursor-based ``next`` links."""

    code = "YANDEX"

    def __init__(self, config: Any, **kwargs: Any) -> None:
        super().__init__(config, **kwargs)
        self.base_url = config.yandex_base_url

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        collected: dict[str, RawVacancy] = {}
        next_url: str | None = f"{self.base_url}{PUBLICATIONS_PATH}"
        seen_cursors: set[str] = set()

        params: dict[str, Any] = {"page_size": min(query.per_page, MAX_PAGE_SIZE)}
        if query.text:
            params["text"] = query.text
        city_slug = _CITY_SLUGS.get((query.city or "").strip().lower())
        if city_slug:
            params["city"] = city_slug

        for page in range(1, max(query.max_pages, 1) + 1):
            if not next_url or next_url in seen_cursors:
                break
            seen_cursors.add(next_url)

            payload = self.get_json(next_url, params=params if page == 1 else None)
            results = payload.get("results")
            if not isinstance(results, list):
                raise SourceError("yandex.ru jobs API response has no 'results' array")

            for result in results:
                raw = self._parse_result(result)
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

            next_path = payload.get("next")
            next_url = urljoin(f"{self.base_url}/", next_path) if next_path else None

        log.info("yandex fetch finished: pages=%d items=%d", query.max_pages, len(collected))
        return list(collected.values())

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        title = normalize_title(raw.title) or raw.title
        description = clean_html(raw.description)
        vacancy = raw.raw_json.get("vacancy") or {}
        skill_names = " ".join(
            str(skill.get("name", ""))
            for skill in vacancy.get("skills") or []
            if isinstance(skill, dict)
        )
        classification_text = f"{title} {skill_names} {description}"
        remote_type = _remote_type(vacancy, title, description)

        return VacancyDraft(
            source=self.code,
            external_id=raw.external_id,
            title=title,
            url=raw.url,
            company=raw.company,
            description=description or None,
            location=raw.location,
            seniority=detect_seniority(title),
            remote_type=remote_type,
            profession=detect_profession(classification_text, description),
            remote=remote_type == "REMOTE",
            published_at=raw.published_at,
            raw_json=raw.raw_json,
        )

    def _parse_result(self, result: Any) -> RawVacancy | None:
        if not isinstance(result, dict):
            return None

        slug = result.get("publication_slug_url")
        title = result.get("title")
        if not isinstance(slug, str) or not slug.strip() or not title:
            log.debug("skipping Yandex result without publication_slug_url/title")
            return None

        tail = slug.rsplit("-", 1)[-1]
        external_id = tail if tail.isdigit() else str(result.get("id") or "")
        if not external_id:
            return None

        vacancy = result.get("vacancy") or {}
        cities = [
            str(city.get("name"))
            for city in vacancy.get("cities") or []
            if isinstance(city, dict) and city.get("name")
        ]

        return RawVacancy(
            source=self.code,
            external_id=external_id,
            url=f"{self.base_url}/jobs/vacancies/{slug}",
            title=str(title),
            company=DEFAULT_COMPANY,
            description=text_from_parts(result.get("short_summary")) or None,
            location=", ".join(cities)[:255] or None,
            published_at=parse_datetime(result.get("modified")),
            raw_json=result,
        )


def _remote_type(vacancy: dict[str, Any], title: str, description: str) -> str:
    modes = " ".join(
        f"{mode.get('slug', '')} {mode.get('name', '')}".lower()
        for mode in vacancy.get("work_modes") or []
        if isinstance(mode, dict)
    )
    if "remote" in modes or "удал" in modes:
        return "REMOTE"
    if "mixed" in modes or "гибрид" in modes or "комбинир" in modes:
        return "HYBRID"
    if "office" in modes or "офис" in modes:
        return "OFFICE"
    return detect_remote(title, description)

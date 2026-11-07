"""VK careers adapter for the server-rendered vacancy listing.

The listing at ``/vacancy/`` renders the first 25 cards in HTML. Further cards are loaded by
``/load_more/``, which ``robots.txt`` disallows, so the adapter intentionally stops after the
first page and records that limitation in the README instead of imitating pagination.
"""

from __future__ import annotations

import logging
import re
from typing import Any

from selectolax.parser import HTMLParser

from collector.models import RawVacancy, VacancyDraft
from collector.normalize import (
    detect_profession,
    detect_remote,
    detect_seniority,
    matches_query,
    normalize_title,
)
from collector.sources.base import HttpSource, SourceError, SourceQuery

log = logging.getLogger(__name__)

LISTING_PATH = "/vacancy/"
DEFAULT_COMPANY = "VK"
_VACANCY_HREF = re.compile(r"^/vacancy/(\d+)/?(?:\?.*)?$")


class VkSource(HttpSource):
    """Parses VK's server-rendered vacancy cards; never calls the disallowed load-more URL."""

    code = "VK"

    def __init__(self, config: Any, **kwargs: Any) -> None:
        super().__init__(config, **kwargs)
        self.base_url = config.vk_base_url

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        response = self.get(f"{self.base_url}{LISTING_PATH}")
        tree = HTMLParser(response.text)

        cards = _current_cards(tree)
        if not cards:
            cards = _legacy_cards(tree)
        if not cards:
            raise SourceError(
                "team.vk.company listing has no recognized vacancy cards; markup may have changed"
            )

        collected: dict[str, RawVacancy] = {}
        for external_id, card in cards:
            raw = self._parse_card(external_id, card)
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
                break

        log.info("vk fetch finished: cards=%d items=%d", len(cards), len(collected))
        return list(collected.values())

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        title = normalize_title(raw.title) or raw.title
        description = (raw.description or "").strip()
        remote_type = _remote_type(raw.raw_json, title, description)

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
            profession=detect_profession(title, description),
            remote=remote_type == "REMOTE",
            raw_json=raw.raw_json,
        )

    def _parse_card(self, external_id: str, card: Any) -> RawVacancy | None:
        title_node = card.css_first("h3")
        if title_node is None:
            return None
        title = title_node.text(strip=True)
        if not title:
            return None

        parts = _description_parts(card)
        company, department, place = _card_identity(parts)
        location, work_format = _split_place(place)
        description = f"Подразделение: {department}" if department else None

        return RawVacancy(
            source=self.code,
            external_id=external_id,
            url=f"{self.base_url}/vacancy/{external_id}/",
            title=title,
            company=company,
            description=description,
            location=location,
            raw_json={
                "href": f"/vacancy/{external_id}/",
                "html": card.html,
                "workFormat": work_format,
            },
        )


def _current_cards(tree: HTMLParser) -> list[tuple[str, Any]]:
    cards: list[tuple[str, Any]] = []
    for anchor in tree.css("a"):
        match = _VACANCY_HREF.match(anchor.attributes.get("href") or "")
        if match and anchor.css_first("h3"):
            cards.append((match.group(1), anchor))
    return cards


def _legacy_cards(tree: HTMLParser) -> list[tuple[str, Any]]:
    cards: list[tuple[str, Any]] = []
    for anchor in tree.css("a.result-item"):
        match = _VACANCY_HREF.match(anchor.attributes.get("href") or "")
        if match:
            cards.append((match.group(1), anchor))
    return cards


def _description_parts(card: Any) -> list[str]:
    nodes = card.css("div[class*='ItemDescriptionText'] > div")
    if not nodes:
        nodes = card.css(".result-item-unit")
    return [node.text(strip=True) for node in nodes if node.text(strip=True)]


def _card_identity(parts: list[str]) -> tuple[str, str | None, str | None]:
    """Return ``(company, department, place)`` from the card's two text blocks."""
    if len(parts) >= 2:
        department, place = parts[0], parts[1]
    elif len(parts) == 1:
        department, place = None, parts[0]
    else:
        department, place = None, None

    if department and department.lower() in ("vk", "vkontakte"):
        department = None
    return DEFAULT_COMPANY, department, place


def _split_place(place: str | None) -> tuple[str | None, str | None]:
    if not place:
        return None, None
    if "," in place:
        city, work_format = place.rsplit(",", 1)
        return city.strip()[:255] or None, work_format.strip() or None
    return place.strip()[:255] or None, None


def _remote_type(payload: dict[str, Any], title: str, description: str) -> str:
    work_format = str(payload.get("workFormat") or "").lower()
    if "удал" in work_format or "remote" in work_format:
        return "REMOTE"
    if "комбинир" in work_format or "гибрид" in work_format or "гибк" in work_format:
        return "HYBRID"
    if "офис" in work_format or "office" in work_format:
        return "OFFICE"
    return detect_remote(title, description)

"""Source adapter contract. One adapter per job board or career site."""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from collector.models import RawVacancy, SearchQuery, VacancyDraft


class SourceError(RuntimeError):
    """Raised when a source cannot be fetched or parsed.

    Reported to core-api as a failed source run.
    """


@runtime_checkable
class SourceAdapter(Protocol):
    """A source adapter fetches raw listings and maps each one to a normalized draft.

    Rules for implementations:
    - respect robots.txt and the site's terms of service;
    - use the configured honest User-Agent and a low request rate;
    - never bypass captchas, auth walls, or paywalls.
    """

    code: str

    def fetch(self, query: SearchQuery) -> list[RawVacancy]:
        """Fetch raw listings. Must not write to the database."""

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        """Map one raw listing to a VacancyDraft accepted by core-api."""

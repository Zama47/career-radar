"""Source adapter contract. One adapter per job board or career site."""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from collector.models import VacancyDraft


class SourceError(RuntimeError):
    """Raised when a source cannot be fetched or parsed.

    Reported to core-api as a failed source run.
    """


class VacancySource(ABC):
    """A source adapter fetches raw listings and maps each one to a normalized draft.

    Rules for implementations:
    - respect robots.txt and the site's terms of service;
    - use the configured honest User-Agent and a low request rate;
    - never bypass captchas, auth walls, or paywalls.
    """

    code: str

    @abstractmethod
    def fetch(self) -> list[dict[str, Any]]:
        """Fetch raw listings. Must not write to the database."""

    @abstractmethod
    def normalize(self, raw: dict[str, Any]) -> VacancyDraft:
        """Map one raw listing to a VacancyDraft accepted by core-api."""

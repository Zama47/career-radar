"""Source adapter registry. Adapters are added in later stages."""

from __future__ import annotations

from collector.sources.base import VacancySource

__all__ = ["VacancySource", "available_sources"]


def available_sources() -> dict[str, type[VacancySource]]:
    """Return the registered adapters keyed by source code."""
    return {}

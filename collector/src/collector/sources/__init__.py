"""Source adapter registry."""

from __future__ import annotations

from collector.sources.base import SourceAdapter, SourceError
from collector.sources.hh import HhSource

__all__ = ["HhSource", "SourceAdapter", "SourceError", "available_sources"]


def available_sources() -> dict[str, type[HhSource]]:
    """Return the registered adapters keyed by source code."""
    return {HhSource.code: HhSource}

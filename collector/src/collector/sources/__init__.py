"""Source adapter registry.

``HH`` remains available as a legacy adapter but is not part of the active career-site
catalogue: hh.ru's public vacancy API is closed and the stage does not use it.
"""

from __future__ import annotations

from collector.sources.base import (
    CompanySource,
    HttpSource,
    RobotsGuard,
    SourceError,
    SourceQuery,
)
from collector.sources.hh import HhSource
from collector.sources.mts import MtsSource
from collector.sources.vk import VkSource
from collector.sources.yandex import YandexSource

__all__ = [
    "CompanySource",
    "HhSource",
    "HttpSource",
    "MtsSource",
    "RobotsGuard",
    "SourceError",
    "SourceQuery",
    "VkSource",
    "YandexSource",
    "available_sources",
]


def available_sources() -> dict[str, type[CompanySource]]:
    """Return the registered company adapters keyed by source code."""
    return {
        MtsSource.code: MtsSource,
        YandexSource.code: YandexSource,
        VkSource.code: VkSource,
    }

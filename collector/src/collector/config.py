"""Runtime configuration for the collector, sourced exclusively from environment variables."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass

DEFAULT_CORE_API_URL = "http://localhost:8080"
DEFAULT_MTS_BASE_URL = "https://job.mts.ru"
DEFAULT_YANDEX_BASE_URL = "https://yandex.ru"
DEFAULT_VK_BASE_URL = "https://team.vk.company"
DEFAULT_HH_BASE_URL = "https://api.hh.ru"
DEFAULT_USER_AGENT = "CareerRadar/0.1 (personal project; contact: unknown)"
DEFAULT_INTERNAL_TOKEN = "change-me"


@dataclass(frozen=True, slots=True)
class CollectorConfig:
    """Everything the collector needs at runtime.

    Personal data, tokens and the real contact email never live in the repository: only
    ``infra/.env.example`` is committed, and the values below are safe defaults.
    """

    core_api_url: str = DEFAULT_CORE_API_URL
    internal_token: str = DEFAULT_INTERNAL_TOKEN
    user_agent: str = DEFAULT_USER_AGENT
    mts_base_url: str = DEFAULT_MTS_BASE_URL
    yandex_base_url: str = DEFAULT_YANDEX_BASE_URL
    vk_base_url: str = DEFAULT_VK_BASE_URL
    hh_base_url: str = DEFAULT_HH_BASE_URL
    hh_area: int = 1
    hh_per_page: int = 100
    hh_access_token: str | None = None
    request_timeout: float = 30.0
    rate_limit_seconds: float = 1.0

    @property
    def hh_user_agent(self) -> str:
        """Backwards-compatible alias for the single honest collector User-Agent."""
        return self.user_agent

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> CollectorConfig:
        source = os.environ if env is None else env

        core_api_url = _require_http_url(
            "CORE_API_URL", source.get("CORE_API_URL", DEFAULT_CORE_API_URL).rstrip("/")
        )
        mts_base_url = _require_http_url(
            "MTS_BASE_URL", source.get("MTS_BASE_URL", DEFAULT_MTS_BASE_URL).rstrip("/")
        )
        yandex_base_url = _require_http_url(
            "YANDEX_BASE_URL", source.get("YANDEX_BASE_URL", DEFAULT_YANDEX_BASE_URL).rstrip("/")
        )
        vk_base_url = _require_http_url(
            "VK_BASE_URL", source.get("VK_BASE_URL", DEFAULT_VK_BASE_URL).rstrip("/")
        )
        hh_base_url = _require_http_url(
            "HH_BASE_URL", source.get("HH_BASE_URL", DEFAULT_HH_BASE_URL).rstrip("/")
        )

        request_timeout = _positive_float(
            "REQUEST_TIMEOUT",
            source.get("REQUEST_TIMEOUT", source.get("COLLECTOR_HTTP_TIMEOUT", "30")),
        )
        rate_limit_seconds = _positive_float(
            "RATE_LIMIT_SECONDS", source.get("RATE_LIMIT_SECONDS", "1.0")
        )
        hh_area = _positive_int("HH_AREA", source.get("HH_AREA", "1"))
        hh_per_page = _positive_int("HH_PER_PAGE", source.get("HH_PER_PAGE", "100"))
        if hh_per_page > 100:
            raise ValueError("HH_PER_PAGE must be between 1 and 100 (hh.ru API limit)")

        access_token = source.get("HH_ACCESS_TOKEN", "").strip() or None
        user_agent = (
            source.get("COLLECTOR_USER_AGENT", "").strip()
            or source.get("HH_USER_AGENT", "").strip()
            or DEFAULT_USER_AGENT
        )

        return cls(
            core_api_url=core_api_url,
            internal_token=source.get("INTERNAL_TOKEN", DEFAULT_INTERNAL_TOKEN),
            user_agent=user_agent,
            mts_base_url=mts_base_url,
            yandex_base_url=yandex_base_url,
            vk_base_url=vk_base_url,
            hh_base_url=hh_base_url,
            hh_area=hh_area,
            hh_per_page=hh_per_page,
            hh_access_token=access_token,
            request_timeout=request_timeout,
            rate_limit_seconds=rate_limit_seconds,
        )

    def http_headers(self) -> dict[str, str]:
        """Headers for career sites: one honest identity, no browser impersonation."""
        return {
            "User-Agent": self.user_agent,
            "Accept": "text/html,application/json;q=0.9,*/*;q=0.8",
        }

    def hh_headers(self) -> dict[str, str]:
        """Headers for api.hh.ru: honest identity, JSON, optional app token."""
        headers = {
            "User-Agent": self.user_agent,
            "Accept": "application/json",
        }
        if self.hh_access_token:
            headers["Authorization"] = f"Bearer {self.hh_access_token}"
        return headers

    def core_headers(self) -> dict[str, str]:
        """Headers for core-api. The internal token is added per request by the client."""
        return {
            "User-Agent": self.user_agent,
            "Accept": "application/json",
        }


def _require_http_url(name: str, value: str) -> str:
    if not value.startswith(("http://", "https://")):
        raise ValueError(f"{name} must be an http(s) URL, got {value!r}")
    return value


def _positive_float(name: str, raw: str) -> float:
    try:
        value = float(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be numeric, got {raw!r}") from exc
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _positive_int(name: str, raw: str) -> int:
    try:
        value = int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer, got {raw!r}") from exc
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value

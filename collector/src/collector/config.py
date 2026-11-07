"""Runtime configuration for the collector, sourced exclusively from environment variables."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass

DEFAULT_CORE_API_URL = "http://localhost:8080"
DEFAULT_USER_AGENT = "CareerRadar/0.1 (personal project; contact: unknown)"
DEFAULT_INTERNAL_TOKEN = "change-me"


@dataclass(frozen=True, slots=True)
class CollectorConfig:
    core_api_url: str = DEFAULT_CORE_API_URL
    internal_token: str = DEFAULT_INTERNAL_TOKEN
    hh_user_agent: str = DEFAULT_USER_AGENT
    request_timeout: float = 20.0

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> CollectorConfig:
        source = os.environ if env is None else env

        core_api_url = source.get("CORE_API_URL", DEFAULT_CORE_API_URL).rstrip("/")
        if not core_api_url.startswith(("http://", "https://")):
            raise ValueError(f"CORE_API_URL must be an http(s) URL, got {core_api_url!r}")

        timeout_raw = source.get("COLLECTOR_HTTP_TIMEOUT", "20")
        try:
            timeout = float(timeout_raw)
        except ValueError as exc:
            raise ValueError(
                f"COLLECTOR_HTTP_TIMEOUT must be numeric, got {timeout_raw!r}"
            ) from exc
        if timeout <= 0:
            raise ValueError("COLLECTOR_HTTP_TIMEOUT must be positive")

        return cls(
            core_api_url=core_api_url,
            internal_token=source.get("INTERNAL_TOKEN", DEFAULT_INTERNAL_TOKEN),
            hh_user_agent=source.get("HH_USER_AGENT", DEFAULT_USER_AGENT),
            request_timeout=timeout,
        )

    def as_httpx_headers(self) -> dict[str, str]:
        return {
            "User-Agent": self.hh_user_agent,
            "Accept": "application/json",
        }

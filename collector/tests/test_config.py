from __future__ import annotations

import pytest

from collector.config import DEFAULT_CORE_API_URL, DEFAULT_USER_AGENT, CollectorConfig


def test_defaults_when_env_is_empty() -> None:
    config = CollectorConfig.from_env({})

    assert config.core_api_url == DEFAULT_CORE_API_URL
    assert config.hh_user_agent == DEFAULT_USER_AGENT
    assert config.internal_token == "change-me"
    assert config.request_timeout == 20.0


def test_reads_values_from_env_and_trims_trailing_slash() -> None:
    config = CollectorConfig.from_env(
        {
            "CORE_API_URL": "http://core-api:8080/",
            "INTERNAL_TOKEN": "secret-token",
            "HH_USER_AGENT": "CareerRadar/0.1 (contact: test@example.com)",
            "COLLECTOR_HTTP_TIMEOUT": "5.5",
        }
    )

    assert config.core_api_url == "http://core-api:8080"
    assert config.internal_token == "secret-token"
    assert config.request_timeout == 5.5
    assert config.as_httpx_headers()["User-Agent"].startswith("CareerRadar/0.1")


@pytest.mark.parametrize("bad_url", ["core-api:8080", "ftp://core-api", ""])
def test_rejects_non_http_core_api_url(bad_url: str) -> None:
    with pytest.raises(ValueError, match="CORE_API_URL"):
        CollectorConfig.from_env({"CORE_API_URL": bad_url})


@pytest.mark.parametrize("bad_timeout", ["abc", "0", "-1"])
def test_rejects_invalid_timeout(bad_timeout: str) -> None:
    with pytest.raises(ValueError, match="COLLECTOR_HTTP_TIMEOUT"):
        CollectorConfig.from_env({"COLLECTOR_HTTP_TIMEOUT": bad_timeout})

from __future__ import annotations

import pytest

from collector.config import (
    DEFAULT_CORE_API_URL,
    DEFAULT_HH_BASE_URL,
    DEFAULT_MTS_BASE_URL,
    DEFAULT_USER_AGENT,
    DEFAULT_VK_BASE_URL,
    DEFAULT_YANDEX_BASE_URL,
    CollectorConfig,
)


def test_defaults_when_env_is_empty() -> None:
    config = CollectorConfig.from_env({})

    assert config.core_api_url == DEFAULT_CORE_API_URL
    assert config.hh_base_url == DEFAULT_HH_BASE_URL
    assert config.mts_base_url == DEFAULT_MTS_BASE_URL
    assert config.yandex_base_url == DEFAULT_YANDEX_BASE_URL
    assert config.vk_base_url == DEFAULT_VK_BASE_URL
    assert config.user_agent == DEFAULT_USER_AGENT
    assert config.hh_user_agent == DEFAULT_USER_AGENT
    assert config.internal_token == "change-me"
    assert config.request_timeout == 30.0
    assert config.rate_limit_seconds == 1.0
    assert config.hh_area == 1
    assert config.hh_per_page == 100
    assert config.hh_access_token is None
    assert "Authorization" not in config.hh_headers()
    assert config.http_headers()["User-Agent"] == DEFAULT_USER_AGENT


def test_reads_values_from_env_and_trims_trailing_slash() -> None:
    config = CollectorConfig.from_env(
        {
            "CORE_API_URL": "http://core-api:8080/",
            "INTERNAL_TOKEN": "secret-token",
            "COLLECTOR_USER_AGENT": "CareerRadar/0.1 (contact: test@example.com)",
            "MTS_BASE_URL": "https://job.mts.ru/",
            "YANDEX_BASE_URL": "https://yandex.ru/",
            "VK_BASE_URL": "https://team.vk.company/",
            "HH_USER_AGENT": "ignored-when-collector-ua-is-set",
            "HH_BASE_URL": "https://api.hh.ru/",
            "HH_AREA": "2",
            "HH_PER_PAGE": "50",
            "HH_ACCESS_TOKEN": "hh-app-token",
            "REQUEST_TIMEOUT": "5.5",
            "RATE_LIMIT_SECONDS": "2",
        }
    )

    assert config.core_api_url == "http://core-api:8080"
    assert config.mts_base_url == "https://job.mts.ru"
    assert config.yandex_base_url == "https://yandex.ru"
    assert config.vk_base_url == "https://team.vk.company"
    assert config.hh_base_url == "https://api.hh.ru"
    assert config.internal_token == "secret-token"
    assert config.request_timeout == 5.5
    assert config.rate_limit_seconds == 2.0
    assert config.hh_area == 2
    assert config.hh_per_page == 50
    assert config.hh_headers()["User-Agent"].startswith("CareerRadar/0.1")
    assert config.hh_headers()["Authorization"] == "Bearer hh-app-token"
    assert "Authorization" not in config.core_headers()


def test_legacy_hh_user_agent_is_used_when_collector_ua_is_absent() -> None:
    config = CollectorConfig.from_env(
        {"HH_USER_AGENT": "CareerRadar/0.1 (contact: legacy@example.com)"}
    )

    assert config.user_agent.startswith("CareerRadar/0.1")
    assert "legacy@example.com" in config.http_headers()["User-Agent"]


def test_legacy_timeout_variable_is_still_supported() -> None:
    config = CollectorConfig.from_env({"COLLECTOR_HTTP_TIMEOUT": "4"})

    assert config.request_timeout == 4.0


@pytest.mark.parametrize("bad_url", ["core-api:8080", "ftp://core-api", ""])
def test_rejects_non_http_core_api_url(bad_url: str) -> None:
    with pytest.raises(ValueError, match="CORE_API_URL"):
        CollectorConfig.from_env({"CORE_API_URL": bad_url})


@pytest.mark.parametrize("bad_url", ["api.hh.ru", "ftp://api.hh.ru"])
def test_rejects_non_http_hh_base_url(bad_url: str) -> None:
    with pytest.raises(ValueError, match="HH_BASE_URL"):
        CollectorConfig.from_env({"HH_BASE_URL": bad_url})


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("MTS_BASE_URL", "job.mts.ru"),
        ("YANDEX_BASE_URL", "ftp://yandex.ru"),
        ("VK_BASE_URL", ""),
    ],
)
def test_rejects_non_http_company_base_urls(name: str, value: str) -> None:
    with pytest.raises(ValueError, match=name):
        CollectorConfig.from_env({name: value})


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("REQUEST_TIMEOUT", "abc"),
        ("REQUEST_TIMEOUT", "0"),
        ("RATE_LIMIT_SECONDS", "-1"),
        ("HH_AREA", "0"),
        ("HH_PER_PAGE", "101"),
        ("HH_PER_PAGE", "many"),
    ],
)
def test_rejects_invalid_numeric_settings(name: str, value: str) -> None:
    with pytest.raises(ValueError, match=name):
        CollectorConfig.from_env({name: value})

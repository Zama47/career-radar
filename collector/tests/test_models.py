from __future__ import annotations

import pytest
from pydantic import ValidationError

from collector.models import VacancyDraft


def test_valid_draft_is_accepted_with_defaults() -> None:
    draft = VacancyDraft(
        source="HH",
        external_id="42",
        title="  Backend Engineer  ",
        url="https://hh.ru/vacancy/42",
        salary_from=100_000,
        salary_to=150_000,
        currency="RUB",
    )

    assert draft.title == "Backend Engineer"
    assert draft.remote is False
    assert draft.seniority == "UNKNOWN"
    assert draft.remote_type == "UNKNOWN"
    assert draft.profession == "OTHER"
    assert draft.raw_json == {}


def test_rejects_relative_url() -> None:
    with pytest.raises(ValidationError, match="absolute http"):
        VacancyDraft(source="HH", external_id="1", title="X", url="/vacancy/1")


def test_rejects_inverted_salary_range() -> None:
    with pytest.raises(ValidationError, match="salary_to"):
        VacancyDraft(
            source="HH",
            external_id="1",
            title="X",
            url="https://hh.ru/vacancy/1",
            salary_from=200_000,
            salary_to=100_000,
        )


def test_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        VacancyDraft(
            source="HH",
            external_id="1",
            title="X",
            url="https://hh.ru/vacancy/1",
            unexpected="value",
        )


def test_to_payload_uses_camel_case_and_drops_nulls() -> None:
    draft = VacancyDraft(
        source="HH",
        external_id="42",
        title="Android-разработчик (стажер)",
        url="https://hh.ru/vacancy/42",
        company="МТС",
        location="Москва",
        salary_from=50_000,
        salary_to=None,
        currency="RUB",
        seniority="INTERN",
        remote_type="REMOTE",
        profession="ANDROID",
        remote=True,
        raw_json={"id": "42"},
    )

    payload = draft.to_payload()

    assert payload["externalId"] == "42"
    assert payload["salaryFrom"] == 50_000
    assert payload["remoteType"] == "REMOTE"
    assert payload["rawJson"] == {"id": "42"}
    assert "external_id" not in payload
    assert "salaryTo" not in payload
    assert "publishedAt" not in payload

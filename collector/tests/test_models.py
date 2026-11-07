from __future__ import annotations

import pytest
from pydantic import ValidationError

from collector.models import VacancyDraft


def test_valid_draft_is_accepted() -> None:
    draft = VacancyDraft(
        source_code="hh",
        external_id="42",
        title="  Backend Engineer  ",
        url="https://hh.ru/vacancy/42",
        salary_from=100_000,
        salary_to=150_000,
        currency="RUB",
    )

    assert draft.title == "Backend Engineer"
    assert draft.remote is False
    assert draft.raw == {}


def test_rejects_relative_url() -> None:
    with pytest.raises(ValidationError, match="absolute http"):
        VacancyDraft(source_code="hh", external_id="1", title="X", url="/vacancy/1")


def test_rejects_inverted_salary_range() -> None:
    with pytest.raises(ValidationError, match="salary_to"):
        VacancyDraft(
            source_code="hh",
            external_id="1",
            title="X",
            url="https://hh.ru/vacancy/1",
            salary_from=200_000,
            salary_to=100_000,
        )


def test_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        VacancyDraft(
            source_code="hh",
            external_id="1",
            title="X",
            url="https://hh.ru/vacancy/1",
            unexpected="value",
        )

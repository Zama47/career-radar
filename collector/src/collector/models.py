"""Transport and domain models shared between collectors and core-api."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel


@dataclass(frozen=True, slots=True)
class SearchQuery:
    """Parameters for one source search; adapters translate them to their own API."""

    text: str
    area: int | None = None
    max_pages: int = 1
    max_items: int | None = None
    per_page: int = 100


class RawVacancy(BaseModel):
    """One listing as returned by a source, before normalization.

    ``raw_json`` keeps the untouched source payload for debugging and fixtures. It is stored
    by core-api after credential-like keys are stripped.
    """

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    source: str = Field(min_length=1, max_length=32)
    external_id: str = Field(min_length=1, max_length=255)
    url: str = Field(min_length=1, max_length=2048)
    title: str = Field(min_length=1, max_length=500)
    company: str | None = Field(default=None, max_length=255)
    description: str | None = None
    location: str | None = Field(default=None, max_length=255)
    salary_from: float | None = Field(default=None, ge=0)
    salary_to: float | None = Field(default=None, ge=0)
    currency: str | None = Field(default=None, max_length=8)
    published_at: datetime | None = None
    raw_json: dict[str, Any] = Field(default_factory=dict)


class VacancyDraft(BaseModel):
    """Normalized vacancy produced by a source adapter and consumed by core-api.

    Attributes stay snake_case in Python, but the JSON sent to core-api uses camelCase
    (``externalId``, ``salaryFrom``, ...), matching ``VacancyDraftRequest`` on the Java side.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )

    source: str = Field(min_length=1, max_length=32)
    external_id: str = Field(min_length=1, max_length=255)
    title: str = Field(min_length=1, max_length=500)
    url: str = Field(min_length=1, max_length=2048)
    company: str | None = Field(default=None, max_length=255)
    description: str | None = None
    location: str | None = Field(default=None, max_length=255)
    salary_from: float | None = Field(default=None, ge=0)
    salary_to: float | None = Field(default=None, ge=0)
    currency: str | None = Field(default=None, max_length=8)
    seniority: str = Field(default="UNKNOWN", max_length=16)
    remote_type: str = Field(default="UNKNOWN", max_length=16)
    profession: str = Field(default="OTHER", max_length=64)
    remote: bool = False
    published_at: datetime | None = None
    raw_json: dict[str, Any] = Field(default_factory=dict)

    @field_validator("url")
    @classmethod
    def url_must_be_absolute(cls, value: str) -> str:
        if not value.startswith(("http://", "https://")):
            raise ValueError("url must be an absolute http(s) URL")
        return value

    @model_validator(mode="after")
    def salary_range_is_sane(self) -> VacancyDraft:
        if (
            self.salary_to is not None
            and self.salary_from is not None
            and self.salary_to < self.salary_from
        ):
            raise ValueError("salary_to must be greater than or equal to salary_from")
        return self

    def to_payload(self) -> dict[str, Any]:
        """JSON-ready dict with camelCase keys and nulls dropped."""
        return self.model_dump(mode="json", by_alias=True, exclude_none=True)


class UpsertResult(BaseModel):
    """Minimal response shape returned by core-api's internal upsert endpoint."""

    model_config = ConfigDict(extra="allow")

    received: int | None = None
    created: int | None = None
    updated: int | None = None
    skipped: int | None = None

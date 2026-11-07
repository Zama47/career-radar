"""Transport models shared with core-api's internal upsert endpoint."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class VacancyDraft(BaseModel):
    """Normalized vacancy produced by a source adapter and consumed by core-api."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    source_code: str = Field(min_length=1, max_length=64)
    external_id: str = Field(min_length=1, max_length=256)
    title: str = Field(min_length=1, max_length=512)
    url: str = Field(min_length=1, max_length=2048)
    company_name: str | None = Field(default=None, max_length=512)
    description: str | None = None
    salary_from: int | None = Field(default=None, ge=0)
    salary_to: int | None = Field(default=None, ge=0)
    currency: str | None = Field(default=None, max_length=8)
    remote: bool = False
    published_at: datetime | None = None
    raw: dict[str, Any] = Field(default_factory=dict)

    @field_validator("url")
    @classmethod
    def url_must_be_absolute(cls, value: str) -> str:
        if not value.startswith(("http://", "https://")):
            raise ValueError("url must be an absolute http(s) URL")
        return value

    @field_validator("salary_to")
    @classmethod
    def salary_range_is_sane(cls, value: int | None, info: Any) -> int | None:
        salary_from = info.data.get("salary_from")
        if value is not None and salary_from is not None and value < salary_from:
            raise ValueError("salary_to must be greater than or equal to salary_from")
        return value


class UpsertResult(BaseModel):
    """Minimal response shape returned by core-api's internal upsert endpoint."""

    model_config = ConfigDict(extra="allow")

    received: int | None = None
    created: int | None = None
    updated: int | None = None
    skipped: int | None = None

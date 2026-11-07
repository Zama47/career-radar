"""HTTP client for core-api's internal endpoints. The collector never touches the database."""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

import httpx

from collector.config import CollectorConfig
from collector.models import UpsertResult, VacancyDraft

INTERNAL_HEADER = "X-Internal-Token"


class CoreApiError(RuntimeError):
    """Raised when core-api rejects a request or cannot be reached."""

    def __init__(self, message: str, *, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


class CoreClient:
    def __init__(self, config: CollectorConfig, client: httpx.Client | None = None) -> None:
        self._config = config
        self._owns_client = client is None
        self._client = client or httpx.Client(
            base_url=config.core_api_url,
            timeout=config.request_timeout,
            headers=config.as_httpx_headers(),
        )

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> CoreClient:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    def ping(self) -> dict[str, Any]:
        return self._request("GET", "/api/v1/ping")

    def upsert_vacancies(self, drafts: Iterable[VacancyDraft]) -> UpsertResult:
        """Send drafts grouped by source, matching the core-api batch contract.

        The internal endpoint expects ``{"source": "HH", "items": [...]}`` and reports
        per-batch counters, so a mixed list of drafts is split by ``source_code``.
        """
        batches: dict[str, list[VacancyDraft]] = {}
        for draft in drafts:
            batches.setdefault(draft.source_code.upper(), []).append(draft)

        received = created = updated = skipped = 0
        for source_code, batch in batches.items():
            body = self._request(
                "POST",
                "/internal/v1/vacancies:upsert",
                json={
                    "source": source_code,
                    "items": [draft.model_dump(mode="json") for draft in batch],
                },
                headers={INTERNAL_HEADER: self._config.internal_token},
            )
            result = UpsertResult.model_validate(body)
            received += result.received or 0
            created += result.created or 0
            updated += result.updated or 0
            skipped += result.skipped or 0

        return UpsertResult(received=received, created=created, updated=updated, skipped=skipped)

    def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        try:
            response = self._client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            raise CoreApiError(f"core-api request failed: {exc}") from exc

        if response.status_code >= 400:
            raise CoreApiError(
                f"core-api returned {response.status_code}: {response.text[:500]}",
                status_code=response.status_code,
            )
        try:
            return response.json()
        except ValueError as exc:
            raise CoreApiError("core-api returned a non-JSON response") from exc

"""HTTP client for core-api's internal endpoints. The collector never touches the database."""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

import httpx
from tenacity import retry, retry_if_exception, stop_after_attempt, wait_exponential

from collector.config import CollectorConfig
from collector.models import UpsertResult, VacancyDraft

INTERNAL_HEADER = "X-Internal-Token"
UPSERT_PATH = "/internal/v1/vacancies:upsert"
BATCH_SIZE = 50
RETRYABLE_STATUS_CODES = frozenset({429, 500, 502, 503, 504})


class CoreApiError(RuntimeError):
    """Raised when core-api rejects a request or cannot be reached."""

    def __init__(
        self,
        message: str,
        *,
        status_code: int | None = None,
        retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.retryable = retryable


def _is_retryable(exc: BaseException) -> bool:
    return isinstance(exc, CoreApiError) and exc.retryable


class CoreClient:
    """Posts normalized batches to core-api, 50 items per request."""

    def __init__(
        self,
        config: CollectorConfig,
        client: httpx.Client | None = None,
        *,
        batch_size: int = BATCH_SIZE,
    ) -> None:
        if batch_size <= 0:
            raise ValueError("batch_size must be positive")
        self._config = config
        self._batch_size = batch_size
        self._owns_client = client is None
        self._client = client or httpx.Client(
            base_url=config.core_api_url,
            timeout=config.request_timeout,
            headers=config.core_headers(),
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

    def post_batch(self, items: Iterable[VacancyDraft]) -> UpsertResult:
        """Send drafts in chunks of at most ``batch_size``, grouped by source.

        The internal endpoint takes one source per request, so mixed input is split. Counters
        are summed across chunks; core-api remains the single owner of deduplication.
        """
        batches: dict[str, list[VacancyDraft]] = {}
        for draft in items:
            batches.setdefault(draft.source.upper(), []).append(draft)

        received = created = updated = skipped = 0
        for source, drafts in batches.items():
            for start in range(0, len(drafts), self._batch_size):
                chunk = drafts[start : start + self._batch_size]
                result = self._post_chunk(source, chunk)
                received += result.received or 0
                created += result.created or 0
                updated += result.updated or 0
                skipped += result.skipped or 0

        return UpsertResult(received=received, created=created, updated=updated, skipped=skipped)

    def _post_chunk(self, source: str, chunk: list[VacancyDraft]) -> UpsertResult:
        body = self._request(
            "POST",
            UPSERT_PATH,
            json={
                "source": source,
                "items": [draft.to_payload() for draft in chunk],
            },
            headers={INTERNAL_HEADER: self._config.internal_token},
        )
        return UpsertResult.model_validate(body)

    @retry(
        retry=retry_if_exception(_is_retryable),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=0.5, min=0.5, max=4),
        reraise=True,
    )
    def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        try:
            response = self._client.request(method, path, **kwargs)
        except httpx.TransportError as exc:
            raise CoreApiError(f"core-api request failed: {exc}", retryable=True) from exc

        if response.status_code >= 400:
            raise CoreApiError(
                f"core-api returned {response.status_code}: {response.text[:500]}",
                status_code=response.status_code,
                retryable=response.status_code in RETRYABLE_STATUS_CODES,
            )
        try:
            return response.json()
        except ValueError as exc:
            raise CoreApiError("core-api returned a non-JSON response") from exc

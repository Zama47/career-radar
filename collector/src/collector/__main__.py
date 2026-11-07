"""Collector CLI entry point: ``python -m collector``."""

from __future__ import annotations

import argparse
import json
import logging
import sys
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from pydantic import ValidationError

from collector import __version__
from collector.config import CollectorConfig
from collector.core_client import CoreApiError, CoreClient
from collector.models import RawVacancy, SearchQuery, VacancyDraft
from collector.sources.base import SourceError
from collector.sources.hh import HhSource

log = logging.getLogger(__name__)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="collector",
        description=(
            "Career Radar collector: fetches vacancies and forwards normalized drafts to core-api."
        ),
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {__version__}")

    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("ping", help="Call core-api's liveness endpoint and print the status.")
    subparsers.add_parser("sources", help="List registered vacancy source adapters.")

    hh = subparsers.add_parser(
        "hh", help="Collect vacancies from hh.ru and upsert them into core-api."
    )
    hh.add_argument("--query", required=True, help='Search text, e.g. "android стажер".')
    hh.add_argument("--pages", type=_positive_int, default=1, help="Maximum API pages (default 1).")
    hh.add_argument(
        "--max-items",
        type=_positive_int,
        default=None,
        help="Stop after this many vacancies.",
    )
    hh.add_argument(
        "--dry-run",
        action="store_true",
        help="Fetch and normalize only; print counts and do not call core-api.",
    )
    hh.add_argument(
        "--output",
        type=Path,
        default=None,
        metavar="PATH",
        help="Save the raw hh.ru payload as JSON for fixtures.",
    )

    return parser


def run_ping() -> int:
    config = CollectorConfig.from_env()
    try:
        with CoreClient(config) as client:
            payload = client.ping()
    except CoreApiError as exc:
        print(f"core-api unreachable at {config.core_api_url}: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"core_api_url": config.core_api_url, **payload}, ensure_ascii=False))
    return 0


def run_sources() -> int:
    from collector.sources import available_sources

    codes = sorted(available_sources())
    if not codes:
        print("no source adapters registered yet")
        return 0
    print("\n".join(codes))
    return 0


def run_hh(args: argparse.Namespace) -> int:
    config = CollectorConfig.from_env()
    query = SearchQuery(
        text=args.query,
        area=config.hh_area,
        max_pages=args.pages,
        max_items=args.max_items,
        per_page=config.hh_per_page,
    )

    try:
        with HhSource(config) as source:
            raw_items = source.fetch(query)
            drafts = _normalize_all(source, raw_items)
    except SourceError as exc:
        print(f"hh.ru collection failed: {exc}", file=sys.stderr)
        return 1

    log.info("collected raw=%d normalized=%d", len(raw_items), len(drafts))

    if args.output is not None:
        _write_raw(args.output, args.query, raw_items)

    if args.dry_run:
        print(json.dumps(_summary(drafts, dry_run=True), ensure_ascii=False))
        return 0

    try:
        with CoreClient(config) as client:
            result = client.post_batch(drafts)
    except CoreApiError as exc:
        print(f"core-api upsert failed: {exc}", file=sys.stderr)
        return 1

    summary: dict[str, Any] = _summary(drafts, dry_run=False)
    summary.update(
        {
            "received": result.received,
            "created": result.created,
            "updated": result.updated,
            "skipped": result.skipped,
        }
    )
    print(json.dumps(summary, ensure_ascii=False))
    return 0


def _normalize_all(source: HhSource, raws: list[RawVacancy]) -> list[VacancyDraft]:
    drafts: list[VacancyDraft] = []
    seen: set[str] = set()
    for raw in raws:
        if raw.external_id in seen:
            continue
        try:
            draft = source.normalize(raw)
        except ValidationError as exc:
            log.warning(
                "skipping vacancy %s: %d validation error(s)", raw.external_id, exc.error_count()
            )
            continue
        seen.add(raw.external_id)
        drafts.append(draft)
    return drafts


def _write_raw(path: Path, query: str, raws: list[RawVacancy]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "items": [raw.raw_json for raw in raws],
        "found": len(raws),
        "page": 0,
        "pages": 1,
        "per_page": len(raws),
        "query": query,
        "fetched_at": datetime.now(UTC).isoformat(),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    log.info("raw payload saved to %s", path)


def _summary(drafts: list[VacancyDraft], *, dry_run: bool) -> dict[str, Any]:
    return {
        "dry_run": dry_run,
        "normalized": len(drafts),
        "by_seniority": dict(Counter(draft.seniority for draft in drafts)),
        "by_remote_type": dict(Counter(draft.remote_type for draft in drafts)),
        "by_profession": dict(Counter(draft.profession for draft in drafts)),
    }


def _positive_int(value: str) -> int:
    try:
        number = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"expected an integer, got {value!r}") from exc
    if number <= 0:
        raise argparse.ArgumentTypeError("value must be positive")
    return number


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    # httpx logs every URL at INFO; keep collector logs to counts and errors only.
    logging.getLogger("httpx").setLevel(logging.WARNING)
    args = build_parser().parse_args(argv)
    if args.command == "ping":
        return run_ping()
    if args.command == "sources":
        return run_sources()
    if args.command == "hh":
        return run_hh(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

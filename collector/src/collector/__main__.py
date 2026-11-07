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
from collector.normalize import is_internship_text
from collector.sources import CompanySource, SourceError, SourceQuery, available_sources
from collector.sources.hh import HhSource

log = logging.getLogger(__name__)

SOURCE_ALIASES = {"mts": "MTS", "yandex": "YANDEX", "vk": "VK"}
DEFAULT_INTERNSHIP_PAGES = 5


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

    companies = subparsers.add_parser(
        "companies",
        help="Collect vacancies from company career sites and upsert them into core-api.",
    )
    companies.add_argument(
        "--source",
        choices=(*SOURCE_ALIASES, "all"),
        default="all",
        help="Career site to use (default: all).",
    )
    companies.add_argument(
        "--query",
        default=None,
        help='Optional client-side word filter, e.g. "android".',
    )
    companies.add_argument(
        "--city",
        default="Москва",
        help="City hint for sources that support it (default: Москва).",
    )
    companies.add_argument(
        "--internships",
        action="store_true",
        help="Keep only internships/trainee roles (seniority=INTERN or matching title).",
    )
    companies.add_argument(
        "--pages",
        type=_positive_int,
        default=None,
        help=(
            "Maximum pages per source (default: 1, or 5 with --internships because "
            "internships are sparse)."
        ),
    )
    companies.add_argument(
        "--max-items",
        type=_positive_int,
        default=None,
        help="Stop after this many normalized vacancies.",
    )
    companies.add_argument(
        "--per-page",
        type=_positive_int,
        default=100,
        help="Page size hint passed to sources (default: 100; sources may cap it).",
    )
    companies.add_argument(
        "--dry-run",
        action="store_true",
        help="Fetch and normalize only; do not call core-api.",
    )
    companies.add_argument(
        "--output",
        type=Path,
        default=None,
        metavar="PATH",
        help="Save raw source payloads as JSON for fixtures.",
    )

    hh = subparsers.add_parser(
        "hh", help="Legacy hh.ru collector (public API is closed; kept for reference)."
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
    codes = sorted(available_sources())
    if not codes:
        print("no source adapters registered yet")
        return 0
    print("\n".join(codes))
    return 0


def run_companies(args: argparse.Namespace) -> int:
    config = CollectorConfig.from_env()
    registry = available_sources()
    codes = _selected_codes(args.source)

    pages = args.pages if args.pages is not None else (
        DEFAULT_INTERNSHIP_PAGES if args.internships else 1
    )
    pages = min(pages, 100)
    per_page = min(args.per_page, 200)
    drafts: list[VacancyDraft] = []
    raw_by_source: dict[str, list[RawVacancy]] = {}
    fetched = 0
    errors: list[str] = []

    for code in codes:
        if args.max_items is not None and len(drafts) >= args.max_items:
            break

        source_class = registry.get(code)
        if source_class is None:
            errors.append(f"{code}: adapter is not registered")
            continue

        remaining = None if args.max_items is None else max(args.max_items - len(drafts), 1)
        query = SourceQuery(
            text=args.query,
            city=args.city,
            only_internships=args.internships,
            max_pages=pages,
            max_items=None if args.internships else remaining,
            per_page=per_page,
        )

        try:
            with source_class(config) as source:
                raws = source.fetch(query)
                normalized = _normalize_all(source, raws)
        except SourceError as exc:
            message = f"{code}: {exc}"
            errors.append(message)
            print(f"source {code} failed: {exc}", file=sys.stderr)
            continue

        fetched += len(raws)
        raw_by_source[code] = raws
        if args.internships:
            normalized = [draft for draft in normalized if _is_internship(draft)]

        drafts.extend(normalized)
        log.info(
            "source %s: fetched=%d normalized=%d total=%d",
            code,
            len(raws),
            len(normalized),
            len(drafts),
        )

    if args.max_items is not None:
        drafts = drafts[: args.max_items]

    if args.output is not None:
        _write_raw(args.output, args, pages, raw_by_source)

    summary = _summary(drafts, dry_run=args.dry_run, fetched=fetched, errors=errors)

    if args.dry_run:
        print(json.dumps(summary, ensure_ascii=False))
        return 0

    if not drafts:
        print(
            "no vacancies collected; core-api was not called"
            + (f" ({len(errors)} source error(s))" if errors else ""),
            file=sys.stderr,
        )
        print(json.dumps(summary, ensure_ascii=False))
        return 1

    try:
        with CoreClient(config) as client:
            result = client.post_batch(drafts)
    except CoreApiError as exc:
        print(f"core-api upsert failed: {exc}", file=sys.stderr)
        return 1

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
        _write_hh_raw(args.output, args.query, raw_items)

    if args.dry_run:
        print(
            json.dumps(
                _summary(drafts, dry_run=True, fetched=len(raw_items), errors=[]),
                ensure_ascii=False,
            )
        )
        return 0

    try:
        with CoreClient(config) as client:
            result = client.post_batch(drafts)
    except CoreApiError as exc:
        print(f"core-api upsert failed: {exc}", file=sys.stderr)
        return 1

    summary: dict[str, Any] = _summary(
        drafts, dry_run=False, fetched=len(raw_items), errors=[]
    )
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


def _selected_codes(source: str) -> list[str]:
    if source == "all":
        return list(available_sources())
    return [SOURCE_ALIASES[source]]


def _normalize_all(
    source: CompanySource, raws: list[RawVacancy]
) -> list[VacancyDraft]:
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


def _is_internship(draft: VacancyDraft) -> bool:
    return (
        draft.seniority == "INTERN"
        or is_internship_text(draft.title)
        or is_internship_text(draft.description)
    )


def _write_raw(
    path: Path,
    args: argparse.Namespace,
    pages: int,
    raw_by_source: dict[str, list[RawVacancy]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "fetched_at": datetime.now(UTC).isoformat(),
        "query": {
            "text": args.query,
            "city": args.city,
            "internships": args.internships,
            "pages": pages,
        },
        "sources": {
            code: {
                "count": len(raws),
                "items": [raw.raw_json for raw in raws],
            }
            for code, raws in raw_by_source.items()
        },
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    log.info("raw payload saved to %s", path)


def _write_hh_raw(path: Path, query: str, raws: list[RawVacancy]) -> None:
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


def _summary(
    drafts: list[VacancyDraft],
    *,
    dry_run: bool,
    fetched: int,
    errors: list[str],
) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "dry_run": dry_run,
        "fetched": fetched,
        "normalized": len(drafts),
        "by_seniority": dict(Counter(draft.seniority for draft in drafts)),
        "by_remote_type": dict(Counter(draft.remote_type for draft in drafts)),
        "by_profession": dict(Counter(draft.profession for draft in drafts)),
    }
    if errors:
        summary["errors"] = errors
    return summary


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
    if args.command == "companies":
        return run_companies(args)
    if args.command == "hh":
        return run_hh(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

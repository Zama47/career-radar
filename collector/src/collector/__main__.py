"""Collector CLI entry point: ``python -m collector``."""

from __future__ import annotations

import argparse
import json
import sys

from collector import __version__
from collector.config import CollectorConfig
from collector.core_client import CoreApiError, CoreClient


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="collector",
        description=(
            "Career Radar collector: fetches vacancies and forwards normalized drafts "
            "to core-api."
        ),
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {__version__}")

    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("ping", help="Call core-api's liveness endpoint and print the status.")
    subparsers.add_parser("sources", help="List registered vacancy source adapters.")

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


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "ping":
        return run_ping()
    if args.command == "sources":
        return run_sources()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

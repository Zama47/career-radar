from __future__ import annotations

import pytest

from collector.__main__ import build_parser, main


def test_parser_requires_subcommand() -> None:
    with pytest.raises(SystemExit):
        build_parser().parse_args([])


def test_parser_accepts_ping() -> None:
    args = build_parser().parse_args(["ping"])
    assert args.command == "ping"


def test_sources_command_with_empty_registry(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["sources"]) == 0
    assert "no source adapters" in capsys.readouterr().out

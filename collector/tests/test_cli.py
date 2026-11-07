from __future__ import annotations

import json
from pathlib import Path

import pytest

from collector.__main__ import build_parser, main
from collector.models import RawVacancy, SearchQuery, UpsertResult, VacancyDraft


class FakeHhSource:
    def __init__(self, config: object, **kwargs: object) -> None:
        self.config = config

    def __enter__(self) -> FakeHhSource:
        return self

    def __exit__(self, *exc_info: object) -> None:
        return None

    def fetch(self, query: SearchQuery) -> list[RawVacancy]:
        assert query.text == "android стажер"
        assert query.max_pages == 2
        return [
            RawVacancy(
                source="HH",
                external_id="1",
                url="https://hh.ru/vacancy/1",
                title="Android-разработчик (стажер)",
                company="МТС",
                location="Москва",
                raw_json={"id": "1", "name": "Android-разработчик (стажер)"},
            )
        ]

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        return VacancyDraft(
            source="HH",
            external_id=raw.external_id,
            title=raw.title,
            url=raw.url,
            company=raw.company,
            seniority="INTERN",
            remote_type="REMOTE",
            profession="ANDROID",
            remote=True,
        )


class ExplodingCoreClient:
    def __init__(self, *args: object, **kwargs: object) -> None:
        raise AssertionError("--dry-run must not create a core-api client")


class FakeCoreClient:
    def __init__(self, config: object) -> None:
        self.config = config

    def __enter__(self) -> FakeCoreClient:
        return self

    def __exit__(self, *exc_info: object) -> None:
        return None

    def post_batch(self, drafts: list[VacancyDraft]) -> UpsertResult:
        assert len(drafts) == 1
        return UpsertResult(received=1, created=1, updated=0, skipped=0)


def test_parser_requires_subcommand() -> None:
    with pytest.raises(SystemExit):
        build_parser().parse_args([])


def test_parser_accepts_ping() -> None:
    args = build_parser().parse_args(["ping"])
    assert args.command == "ping"


def test_parser_accepts_hh_options() -> None:
    args = build_parser().parse_args(
        ["hh", "--query", "android стажер", "--pages", "2", "--max-items", "10", "--dry-run"]
    )

    assert args.command == "hh"
    assert args.query == "android стажер"
    assert args.pages == 2
    assert args.max_items == 10
    assert args.dry_run is True


def test_parser_requires_query_for_hh() -> None:
    with pytest.raises(SystemExit):
        build_parser().parse_args(["hh"])


def test_sources_command_lists_company_career_sites(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["sources"]) == 0
    assert capsys.readouterr().out.strip().splitlines() == ["MTS", "VK", "YANDEX"]


def test_dry_run_does_not_call_core(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr("collector.__main__.HhSource", FakeHhSource)
    monkeypatch.setattr("collector.__main__.CoreClient", ExplodingCoreClient)

    assert main(["hh", "--query", "android стажер", "--pages", "2", "--dry-run"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["dry_run"] is True
    assert summary["normalized"] == 1
    assert summary["by_seniority"] == {"INTERN": 1}
    assert summary["by_profession"] == {"ANDROID": 1}


def test_output_writes_raw_fixture(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.setattr("collector.__main__.HhSource", FakeHhSource)
    monkeypatch.setattr("collector.__main__.CoreClient", ExplodingCoreClient)
    output = tmp_path / "hh_page.json"

    args = ["hh", "--query", "android стажер", "--pages", "2", "--dry-run", "--output", str(output)]

    assert main(args) == 0

    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["query"] == "android стажер"
    assert len(payload["items"]) == 1
    assert payload["items"][0]["id"] == "1"


def test_hh_command_posts_batch_to_core(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr("collector.__main__.HhSource", FakeHhSource)
    monkeypatch.setattr("collector.__main__.CoreClient", FakeCoreClient)

    assert main(["hh", "--query", "android стажер", "--pages", "2"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["created"] == 1
    assert summary["normalized"] == 1

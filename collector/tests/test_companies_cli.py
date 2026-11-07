from __future__ import annotations

import json
from pathlib import Path

import pytest

from collector.__main__ import build_parser, main
from collector.models import RawVacancy, UpsertResult, VacancyDraft
from collector.sources.base import SourceError, SourceQuery


def make_raw(source: str, external_id: str, title: str, seniority: str = "UNKNOWN") -> RawVacancy:
    return RawVacancy(
        source=source,
        external_id=external_id,
        url=f"https://example.test/{external_id}",
        title=title,
        raw_json={"seniority": seniority},
    )


class FakeSource:
    code = "MTS"
    base_url = "https://example.test"
    instances: list[FakeSource] = []
    raws: list[RawVacancy] = []

    def __init__(self, config: object, **kwargs: object) -> None:
        self.config = config
        self.query: SourceQuery | None = None
        type(self).instances.append(self)

    def __enter__(self) -> FakeSource:
        return self

    def __exit__(self, *exc_info: object) -> None:
        return None

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        self.query = query
        return list(type(self).raws)

    def normalize(self, raw: RawVacancy) -> VacancyDraft:
        return VacancyDraft(
            source=self.code,
            external_id=raw.external_id,
            title=raw.title,
            url=raw.url,
            seniority=raw.raw_json.get("seniority", "UNKNOWN"),
        )


class FakeMtsSource(FakeSource):
    code = "MTS"


class FakeYandexSource(FakeSource):
    code = "YANDEX"
    raws = [make_raw("YANDEX", "y-1", "DevOps Engineer")]


class FailingSource(FakeSource):
    code = "MTS"

    def fetch(self, query: SourceQuery) -> list[RawVacancy]:
        raise SourceError("example.test returned 403: access denied")


class ExplodingCoreClient:
    def __init__(self, *args: object, **kwargs: object) -> None:
        raise AssertionError("core-api must not be created")


class FakeCoreClient:
    def __init__(self, config: object) -> None:
        self.config = config

    def __enter__(self) -> FakeCoreClient:
        return self

    def __exit__(self, *exc_info: object) -> None:
        return None

    def post_batch(self, drafts: list[VacancyDraft]) -> UpsertResult:
        return UpsertResult(received=len(drafts), created=len(drafts), updated=0, skipped=0)


@pytest.fixture(autouse=True)
def reset_sources(monkeypatch: pytest.MonkeyPatch) -> None:
    for cls in (FakeMtsSource, FakeYandexSource, FailingSource):
        cls.instances = []
    monkeypatch.setattr("collector.__main__.CoreClient", ExplodingCoreClient)


def use_sources(monkeypatch: pytest.MonkeyPatch, **sources: type[FakeSource]) -> None:
    monkeypatch.setattr("collector.__main__.available_sources", lambda: dict(sources))


def test_parser_accepts_companies_options() -> None:
    args = build_parser().parse_args(
        [
            "companies",
            "--source",
            "vk",
            "--query",
            "android",
            "--city",
            "Москва",
            "--internships",
            "--pages",
            "2",
            "--max-items",
            "10",
            "--dry-run",
            "--output",
            "fixtures/vk.json",
        ]
    )

    assert args.command == "companies"
    assert args.source == "vk"
    assert args.internships is True
    assert args.pages == 2
    assert args.max_items == 10
    assert args.dry_run is True


def test_dry_run_does_not_call_core(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    FakeMtsSource.raws = [
        make_raw("MTS", "1", "Android Developer (стажер)", "INTERN"),
        make_raw("MTS", "2", "Java Developer", "JUNIOR"),
    ]
    use_sources(monkeypatch, MTS=FakeMtsSource)

    assert main(["companies", "--source", "mts", "--dry-run"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["dry_run"] is True
    assert summary["fetched"] == 2
    assert summary["normalized"] == 2
    assert summary["by_seniority"] == {"INTERN": 1, "JUNIOR": 1}


def test_internships_filters_and_scans_more_pages(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    FakeMtsSource.raws = [
        make_raw("MTS", "1", "Android Developer (стажер)", "INTERN"),
        make_raw("MTS", "2", "Java Developer", "JUNIOR"),
        make_raw("MTS", "3", "Trainee QA Engineer", "UNKNOWN"),
    ]
    use_sources(monkeypatch, MTS=FakeMtsSource)

    assert main(["companies", "--source", "mts", "--internships", "--dry-run"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["normalized"] == 2
    assert summary["by_seniority"] == {"INTERN": 1, "UNKNOWN": 1}
    assert FakeMtsSource.instances[0].query is not None
    assert FakeMtsSource.instances[0].query.only_internships is True
    assert FakeMtsSource.instances[0].query.max_pages == 5
    assert FakeMtsSource.instances[0].query.max_items is None


def test_posts_batch_to_core(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    FakeMtsSource.raws = [make_raw("MTS", "1", "Android Developer")]
    use_sources(monkeypatch, MTS=FakeMtsSource)
    monkeypatch.setattr("collector.__main__.CoreClient", FakeCoreClient)

    assert main(["companies", "--source", "mts"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["created"] == 1
    assert summary["normalized"] == 1


def test_output_saves_raw_payloads(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    FakeMtsSource.raws = [make_raw("MTS", "1", "Android Developer")]
    use_sources(monkeypatch, MTS=FakeMtsSource)
    output = tmp_path / "mts_page.json"

    assert main(["companies", "--source", "mts", "--dry-run", "--output", str(output)]) == 0

    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["sources"]["MTS"]["count"] == 1
    assert payload["sources"]["MTS"]["items"][0]["seniority"] == "UNKNOWN"


def test_max_items_stops_before_next_source(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    FakeMtsSource.raws = [make_raw("MTS", "1", "Android Developer")]
    use_sources(monkeypatch, MTS=FakeMtsSource, YANDEX=FakeYandexSource)

    assert main(["companies", "--source", "all", "--max-items", "1", "--dry-run"]) == 0

    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["normalized"] == 1
    assert len(FakeMtsSource.instances) == 1
    assert FakeYandexSource.instances == []


def test_continues_when_one_source_fails(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    use_sources(monkeypatch, MTS=FailingSource, YANDEX=FakeYandexSource)

    assert main(["companies", "--source", "all", "--dry-run"]) == 0

    captured = capsys.readouterr()
    assert "source MTS failed" in captured.err
    summary = json.loads(captured.out.strip().splitlines()[-1])
    assert summary["normalized"] == 1
    assert summary["errors"][0].startswith("MTS:")


def test_returns_nonzero_when_all_sources_fail(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    use_sources(monkeypatch, MTS=FailingSource)

    assert main(["companies", "--source", "mts"]) == 1

    assert "no vacancies collected" in capsys.readouterr().err

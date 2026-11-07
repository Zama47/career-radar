from __future__ import annotations

import pytest

from collector.normalize import (
    clean_html,
    detect_profession,
    detect_remote,
    detect_seniority,
    is_internship_text,
    matches_query,
    normalize_currency,
    normalize_search_text,
    normalize_title,
    parse_datetime,
    parse_salary,
    text_from_parts,
    truncate_description,
)


@pytest.mark.parametrize(
    ("title", "expected"),
    [
        ("Android-разработчик (стажер)", "INTERN"),
        ("Trainee QA Engineer", "INTERN"),
        ("Стажёр-аналитик", "INTERN"),
        ("Junior Python Developer", "JUNIOR"),
        ("Джуниор разработчик", "JUNIOR"),
        ("Middle Java Developer", "MIDDLE"),
        ("Мидл аналитик", "MIDDLE"),
        ("Senior Data Analyst", "SENIOR"),
        ("Старший разработчик", "SENIOR"),
        ("Team Lead", "LEAD"),
        ("Ведущий инженер", "LEAD"),
        ("Android Developer", "UNKNOWN"),
    ],
)
def test_detect_seniority(title: str, expected: str) -> None:
    assert detect_seniority(title) == expected


@pytest.mark.parametrize(
    ("title", "description", "expected"),
    [
        ("Android Developer", "Полная удалённая работа", "REMOTE"),
        ("Remote Android Developer", "", "REMOTE"),
        ("Разработчик", "Можно дистанционно", "REMOTE"),
        ("Backend Developer", "Гибридный формат", "HYBRID"),
        ("Hybrid Data Engineer", "", "HYBRID"),
        ("Офисный сотрудник", "", "OFFICE"),
        ("Office Manager", "", "OFFICE"),
        ("Android Developer", "Обычная команда", "UNKNOWN"),
    ],
)
def test_detect_remote(title: str, description: str, expected: str) -> None:
    assert detect_remote(title, description) == expected


@pytest.mark.parametrize(
    ("title", "description", "expected"),
    [
        ("Android-разработчик (стажер)", "", "ANDROID"),
        ("Kotlin Developer", "", "ANDROID"),
        ("Java Backend Developer", "", "JAVA_BACKEND"),
        ("Spring Boot Engineer", "", "JAVA_BACKEND"),
        ("JavaScript Developer", "", "FRONTEND"),
        ("Системный аналитик", "", "SYSTEM_ANALYST"),
        ("System Analyst", "", "SYSTEM_ANALYST"),
        ("Data Analyst", "", "DATA_ANALYST"),
        ("Аналитик данных", "", "DATA_ANALYST"),
        ("BI Analyst", "", "BI_ANALYST"),
        ("QA Engineer", "", "QA"),
        ("Тестировщик", "", "QA"),
        ("DevOps Engineer", "", "DEVOPS"),
        ("Frontend Developer", "", "FRONTEND"),
        ("ML Engineer", "", "ML"),
        ("Product Manager", "", "OTHER"),
        ("Инженер", "Нужен Kotlin и Compose", "ANDROID"),
    ],
)
def test_detect_profession(title: str, description: str, expected: str) -> None:
    assert detect_profession(title, description) == expected


def test_clean_html_keeps_text_and_drops_markup() -> None:
    raw = "<p>Hello</p><p>World &amp; Co</p><highlighttext>Kotlin</highlighttext><br>Next"

    assert clean_html(raw) == "Hello\n\nWorld & Co\nKotlin\nNext"


def test_clean_html_removes_script_and_style() -> None:
    raw = "<style>p{color:red}</style><p>Visible</p><script>alert(1)</script>"

    assert clean_html(raw) == "Visible"


def test_clean_html_handles_empty_values() -> None:
    assert clean_html(None) == ""
    assert clean_html("") == ""


def test_clean_html_truncates_long_descriptions() -> None:
    result = clean_html("<p>" + "x" * 25_000 + "</p>")

    assert len(result) == 20_000


def test_parse_salary_reads_bounds_and_currency() -> None:
    assert parse_salary({"from": 50_000, "to": 70_000, "currency": "RUR"}) == (
        50_000.0,
        70_000.0,
        "RUB",
    )


def test_parse_salary_handles_missing_and_zero_values() -> None:
    assert parse_salary(None) == (None, None, None)
    assert parse_salary({"from": 0, "to": 0, "currency": None}) == (None, None, None)
    assert parse_salary({"from": 180_000, "to": None, "currency": "usd"}) == (
        180_000.0,
        None,
        "USD",
    )


def test_parse_salary_repairs_inverted_range() -> None:
    assert parse_salary({"from": 90_000, "to": 40_000}) == (40_000.0, 90_000.0, None)


def test_normalize_title_collapses_whitespace() -> None:
    assert normalize_title("  Android\u00a0 разработчик   (стажер)  ") == (
        "Android разработчик (стажер)"
    )


def test_normalize_currency_maps_hh_rur_to_iso_rub() -> None:
    assert normalize_currency("RUR") == "RUB"
    assert normalize_currency(" eur ") == "EUR"
    assert normalize_currency(None) is None


def test_truncate_description_respects_limit() -> None:
    assert len(truncate_description("y" * 100, limit=10)) == 10


def test_matches_query_is_case_and_yo_insensitive() -> None:
    assert matches_query("Стажёр Android-разработчик", "стажер android")
    assert not matches_query("Java Developer", "android")


def test_is_internship_text_matches_russian_and_english_markers() -> None:
    assert is_internship_text("Инженер-стажер")
    assert is_internship_text("Trainee QA Engineer")
    assert is_internship_text("Internship program")
    assert not is_internship_text("Senior Java Developer")


def test_normalize_search_text_folds_yo_and_case() -> None:
    assert normalize_search_text("  Стажёр   Android ") == "стажер android"


def test_parse_datetime_accepts_z_suffix_and_naive_values() -> None:
    zulu = parse_datetime("2026-11-07T00:00:00.000Z")
    naive = parse_datetime("2026-11-07T00:00:00")

    assert zulu is not None and zulu.tzinfo is not None
    assert naive is not None and naive.tzinfo is not None
    assert parse_datetime("not-a-date") is None


def test_text_from_parts_flattens_lists_and_objects() -> None:
    assert text_from_parts(["Java", "", "Kotlin"]) == "Java\nKotlin"
    assert text_from_parts({"text": "Android"}) == "Android"
    assert text_from_parts(None) == ""

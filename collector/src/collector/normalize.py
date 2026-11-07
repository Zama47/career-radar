"""Pure normalization helpers: HTML cleanup, salary parsing, rule-based classification.

Everything here is deterministic and network-free so it can be tested on fixtures.
"""

from __future__ import annotations

import re
from collections.abc import Mapping
from datetime import UTC, datetime
from html.parser import HTMLParser
from typing import Any

DESCRIPTION_LIMIT = 20_000

_BLOCK_TAGS = {
    "br",
    "div",
    "li",
    "ol",
    "p",
    "section",
    "table",
    "tr",
    "ul",
}
_SKIP_TAGS = {"script", "style"}

_CURRENCY_ALIASES = {"RUR": "RUB"}

_SENIORITY_RULES: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"\b(?:стаж[её]р\w*|стажировк\w*|trainee|intern)\b", re.IGNORECASE), "INTERN"),
    (re.compile(r"\b(?:junior|джун\w*|младш\w*)\b", re.IGNORECASE), "JUNIOR"),
    (re.compile(r"\b(?:middle|мидл\w*)\b", re.IGNORECASE), "MIDDLE"),
    (re.compile(r"\b(?:lead|лид\w*|ведущ\w*|руководител\w*)\b", re.IGNORECASE), "LEAD"),
    (re.compile(r"\b(?:senior|сеньор\w*|старш\w*)\b", re.IGNORECASE), "SENIOR"),
)

_REMOTE_RULES: tuple[tuple[re.Pattern[str], str], ...] = (
    (
        re.compile(
            r"(?:удал[её]нн\w*|дистанционн\w*|\bremote\b|\bwork from home\b)", re.IGNORECASE
        ),
        "REMOTE",
    ),
    (re.compile(r"(?:гибрид\w*|\bhybrid\b)", re.IGNORECASE), "HYBRID"),
    (re.compile(r"(?:офис\w*|\boffice\b|\bon-?site\b)", re.IGNORECASE), "OFFICE"),
)

_PROFESSION_RULES: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"(?:android|kotlin|jetpack)", re.IGNORECASE), "ANDROID"),
    (re.compile(r"(?:\bjava\b|\bspring\b|\bspring boot\b)", re.IGNORECASE), "JAVA_BACKEND"),
    (
        re.compile(r"(?:системн\w*\s+аналитик\w*|\bsystem analyst\b)", re.IGNORECASE),
        "SYSTEM_ANALYST",
    ),
    (
        re.compile(r"(?:data analyst|аналитик\w*\s+данных|дата-?аналитик\w*)", re.IGNORECASE),
        "DATA_ANALYST",
    ),
    (re.compile(r"(?:\bbi\b|bi-?аналитик\w*|power bi|tableau)", re.IGNORECASE), "BI_ANALYST"),
    (
        re.compile(r"(?:\bqa\b|тестировщик\w*|тестировани\w*|quality assurance)", re.IGNORECASE),
        "QA",
    ),
    (re.compile(r"(?:devops|\bsre\b|kubernetes)", re.IGNORECASE), "DEVOPS"),
    (
        re.compile(
            r"(?:frontend|front-end|фронтенд\w*|фронт-энд\w*|\breact\b|\bvue\b|\bangular\b"
            r"|javascript|typescript)",
            re.IGNORECASE,
        ),
        "FRONTEND",
    ),
    (
        re.compile(
            r"(?:\bml\b|machine learning|машинн\w*\s+обучени\w*|data scientist)", re.IGNORECASE
        ),
        "ML",
    ),
)

_WHITESPACE = re.compile(r"[ \t\xa0\u2009]+")
_NEWLINES = re.compile(r"\n\s*\n+")
_INTERNSHIP = re.compile(
    r"\b(?:стаж[её]р\w*|стажировк\w*|trainee|intern(?:ship)?)\b", re.IGNORECASE
)


class _TextExtractor(HTMLParser):
    """Collect human-readable text from an HTML fragment."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._parts: list[str] = []
        self._skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in _SKIP_TAGS:
            self._skip_depth += 1
        elif tag in _BLOCK_TAGS:
            self._parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in _SKIP_TAGS and self._skip_depth:
            self._skip_depth -= 1
        elif tag in _BLOCK_TAGS:
            self._parts.append("\n")

    def handle_data(self, data: str) -> None:
        if not self._skip_depth:
            self._parts.append(data)

    def text(self) -> str:
        return "".join(self._parts)


def clean_html(value: str | None, *, limit: int = DESCRIPTION_LIMIT) -> str:
    """Turn an HTML snippet into plain text and cap its length.

    hh.ru returns HTML fragments with ``<p>``/``<br>``/``<ul>``; tags are dropped, block
    elements become line breaks, entities are decoded, and runs of whitespace are collapsed.
    """
    if not value:
        return ""

    parser = _TextExtractor()
    parser.feed(value)
    parser.close()
    text = _WHITESPACE.sub(" ", parser.text())
    text = "\n".join(line.strip() for line in text.split("\n"))
    text = _NEWLINES.sub("\n\n", text).strip()
    return text[:limit].rstrip()


def normalize_title(value: str) -> str:
    """Trim, collapse inner whitespace and drop dangling separators."""
    text = _WHITESPACE.sub(" ", value.replace("\u00a0", " ")).strip()
    return text.strip(" -–—,;:")


def normalize_currency(value: str | None) -> str | None:
    if not value:
        return None
    code = value.strip().upper()
    return _CURRENCY_ALIASES.get(code, code)


def parse_salary(
    salary: Mapping[str, Any] | None,
) -> tuple[float | None, float | None, str | None]:
    """Extract ``(from, to, currency)`` from an hh-style ``salary`` object.

    Missing and zero bounds become ``None``; an inverted range is repaired defensively because
    the transport model refuses ``salaryTo < salaryFrom``.
    """
    if not salary:
        return None, None, None

    salary_from = _as_float(salary.get("from"))
    salary_to = _as_float(salary.get("to"))
    if salary_from is not None and salary_to is not None and salary_to < salary_from:
        salary_from, salary_to = salary_to, salary_from

    return salary_from, salary_to, normalize_currency(_as_str(salary.get("currency")))


def detect_seniority(title: str) -> str:
    """Map grade markers in the vacancy title to core-api's ``Seniority`` enum."""
    return _first_match(_SENIORITY_RULES, title) or "UNKNOWN"


def detect_remote(title: str, description: str = "") -> str:
    """Map work-format markers to core-api's ``RemoteType`` enum."""
    return (
        _first_match(_REMOTE_RULES, title) or _first_match(_REMOTE_RULES, description) or "UNKNOWN"
    )


def detect_profession(title: str, description: str = "") -> str:
    """Rule-based profession code. The title wins; the description is only a fallback."""
    return (
        _first_match(_PROFESSION_RULES, title)
        or _first_match(_PROFESSION_RULES, description)
        or "OTHER"
    )


def truncate_description(value: str, *, limit: int = DESCRIPTION_LIMIT) -> str:
    return value[:limit].rstrip()


def normalize_search_text(value: str) -> str:
    """Lower-case, collapse whitespace and fold ``ё`` so queries match both spellings."""
    return _WHITESPACE.sub(" ", value.replace("ё", "е").replace("Ё", "Е")).strip().lower()


def matches_query(text: str, query: str) -> bool:
    """True when every word of ``query`` occurs in ``text`` (case/``ё`` insensitive)."""
    haystack = normalize_search_text(text)
    return all(token in haystack for token in normalize_search_text(query).split())


def is_internship_text(text: str | None) -> bool:
    """Detect internship wording in a title or description."""
    return bool(text and _INTERNSHIP.search(text))


def text_from_parts(value: Any) -> str:
    """Flatten a string, list of strings or list of small objects into plain text."""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list):
        parts = [text_from_parts(entry) for entry in value]
        return "\n".join(part for part in parts if part)
    if isinstance(value, dict):
        for key in ("text", "title", "name", "description"):
            candidate = value.get(key)
            if isinstance(candidate, str) and candidate.strip():
                return candidate.strip()
    return ""


def parse_datetime(value: Any) -> datetime | None:
    """Parse an ISO-8601 timestamp, accepting the ``Z`` suffix used by most APIs."""
    if not isinstance(value, str) or not value.strip():
        return None
    raw = value.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(raw)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed



def _first_match(rules: tuple[tuple[re.Pattern[str], str], ...], text: str) -> str | None:
    if not text:
        return None
    for pattern, code in rules:
        if pattern.search(text):
            return code
    return None


def _as_float(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def _as_str(value: Any) -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    return value

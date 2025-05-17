"""Laying out a printed page, once, for every report that prints one.

Four report modules had each grown a copy of the same three lines: join with
newlines, pad a value into a column, write a heading. The copies had already
drifted, so the pinning chart right aligned its numbers and the keyholder
statement left aligned them, and a page with both on it looked like two
documents stapled together.

Everything here is fixed width because everything here gets printed. There is
no wrapping and no truncation: a value longer than its column pushes the rest
of the line along, which is ugly and visible, and both of those are better
than a room number quietly losing its last character.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

RULE = "-"
PAGE_WIDTH = 72


def columns(values: Sequence[object], widths: Sequence[int]) -> str:
    """Values padded into left aligned columns, the last one unpadded."""
    parts: list[str] = []
    for index, value in enumerate(values):
        text = "" if value is None else str(value)
        if index < len(widths):
            parts.append(text.ljust(widths[index]))
        else:
            parts.append(text)
    return "".join(parts).rstrip()


def right(values: Sequence[object], width: int) -> str:
    """Values right aligned in equal columns, which is how numbers read."""
    return "".join(str(value).rjust(width) for value in values)


def heading(names: Sequence[str], widths: Sequence[int]) -> str:
    return columns(names, widths)


def rule(width: int = PAGE_WIDTH) -> str:
    return RULE * width


def titled(title: str, lines: Iterable[str]) -> tuple[str, ...]:
    """A page with a title on it, or the same page without one."""
    body = tuple(lines)
    return (title, *body) if title else body


def as_text(lines: Iterable[str]) -> str:
    return "\n".join(lines)

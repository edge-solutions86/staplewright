"""Exports, in whatever the receiving system happens to eat this year.

Not a serialisation format and not an interchange format. An export here is
one table, flat, with a header row naming columns that a person picked,
because the campus space management system imports csv and nothing else.
Every export hands back rows rather than writing a file, so a caller can send
them to a response, a file, or a test without any of them caring.

Dates are iso and money is in cents, both of which the receiving system reads
without asking. Nothing is quoted by hand: the csv module knows more about
commas in a room description than anybody writing this does.

A table used to be three things sitting next to each other, a header tuple, a
rows function, and a one line wrapper that stapled them together into csv.
That was fine until the finance system asked for json, at which point every
table needed a second wrapper and the two started disagreeing about which
columns were in it. So a table is one object now, and a format is something
you ask it for.
"""

from __future__ import annotations

import csv
import io
import json
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass
from datetime import date
from typing import Any

from hasplib.campus.hardware import Cylinder
from hasplib.campus.openings import Opening
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import IssuedKey
from hasplib.errors import NotFoundError
from hasplib.keys.schedule import ScheduleEntry

Row = Sequence[object]


def to_text(header: Sequence[str], rows: Iterable[Row]) -> str:
    """One csv document, with the header first and unix line endings."""
    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\n")
    writer.writerow(list(header))
    for row in rows:
        writer.writerow(["" if value is None else value for value in row])
    return out.getvalue()


def to_records(header: Sequence[str], rows: Iterable[Row]) -> list[dict[str, Any]]:
    """The same rows as dictionaries, for anything that speaks json."""
    return [dict(zip(header, row, strict=True)) for row in rows]


def to_json(header: Sequence[str], rows: Iterable[Row]) -> str:
    return json.dumps(to_records(header, rows), indent=2, sort_keys=False)


@dataclass(frozen=True)
class Table:
    """One export: what it is called, its columns, and how to build its rows."""

    name: str
    header: tuple[str, ...]
    build: Callable[..., list[Row]]

    def rows(self, *args: Any, **kwargs: Any) -> list[Row]:
        return self.build(*args, **kwargs)

    def csv(self, *args: Any, **kwargs: Any) -> str:
        return to_text(self.header, self.rows(*args, **kwargs))

    def json(self, *args: Any, **kwargs: Any) -> str:
        return to_json(self.header, self.rows(*args, **kwargs))

    def records(self, *args: Any, **kwargs: Any) -> list[dict[str, Any]]:
        return to_records(self.header, self.rows(*args, **kwargs))

    def filename(self, on: date, suffix: str = "csv") -> str:
        return f"hasplib-{self.name}-{on.isoformat()}.{suffix}"


def schedule_rows(entries: Iterable[ScheduleEntry]) -> list[Row]:
    return [
        (
            entry.symbol.render(),
            entry.symbol.level,
            entry.section,
            entry.bitting.render() if entry.bitting else None,
            entry.note,
        )
        for entry in entries
    ]


def opening_rows(openings: Iterable[Opening]) -> list[Row]:
    return [
        (
            opening.number,
            opening.building,
            opening.floor,
            opening.room,
            opening.function.value,
            "yes" if opening.exterior else "no",
            opening.description,
        )
        for opening in openings
    ]


def cylinder_rows(cylinders: Iterable[Cylinder]) -> list[Row]:
    return [
        (
            cylinder.serial,
            cylinder.format.value,
            cylinder.section,
            cylinder.chambers,
            cylinder.installed_at,
            cylinder.position.value if cylinder.position else None,
            cylinder.group,
        )
        for cylinder in cylinders
    ]


def holding_rows(keys: Iterable[IssuedKey], book: DepositBook | None = None) -> list[Row]:
    rows: list[Row] = []
    for key in keys:
        held = book.held_on(key.tag) if book else key.deposit_cents
        rows.append(
            (
                key.tag,
                key.symbol,
                key.keyholder,
                key.issued_on.isoformat(),
                key.due_on.isoformat() if key.due_on else None,
                held,
                key.state.value,
            )
        )
    return rows


SCHEDULE = Table(
    "schedule",
    ("symbol", "level", "section", "bitting", "note"),
    schedule_rows,
)
OPENINGS = Table(
    "openings",
    ("number", "building", "floor", "room", "function", "exterior", "description"),
    opening_rows,
)
CYLINDERS = Table(
    "cylinders",
    ("serial", "format", "section", "chambers", "opening", "position", "group"),
    cylinder_rows,
)
HOLDINGS = Table(
    "holdings",
    ("tag", "symbol", "keyholder", "issued", "due", "deposit_cents", "state"),
    holding_rows,
)

TABLES = {table.name: table for table in (SCHEDULE, OPENINGS, CYLINDERS, HOLDINGS)}


def table(name: str) -> Table:
    """The table somebody asked for by name, which is how the cli reaches one."""
    found = TABLES.get(name)
    if found is None:
        raise NotFoundError("nothing is exported under that name", table=name)
    return found


def table_names() -> tuple[str, ...]:
    return tuple(sorted(TABLES))


def filename(kind: str, on: date, suffix: str = "csv") -> str:
    """What the shop calls the file when it lands in the shared folder."""
    return f"hasplib-{kind}-{on.isoformat()}.{suffix}"

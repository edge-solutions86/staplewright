"""Comma separated files, because that is what the facilities system reads.

Not a serialisation format and not an interchange format. A csv here is one
table, flat, with a header row naming columns that a person picked, and it
exists because the campus space management system imports csv and nothing
else. Every export is a list of rows rather than a written file, so a caller
can send it to a response, a file, or a test without any of them caring.

Dates are iso and money is in cents, both of which the receiving system reads
without asking. Nothing is quoted by hand: the csv module knows more about
commas in a room description than anybody writing this does.
"""

from __future__ import annotations

import csv
import io
from collections.abc import Iterable, Sequence
from datetime import date

from hasplib.campus.hardware import Cylinder
from hasplib.campus.openings import Opening
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import IssuedKey
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


SCHEDULE_HEADER = ("symbol", "level", "section", "bitting", "note")


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


def schedule_csv(entries: Iterable[ScheduleEntry]) -> str:
    return to_text(SCHEDULE_HEADER, schedule_rows(entries))


OPENING_HEADER = ("number", "building", "floor", "room", "function", "exterior", "description")


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


def openings_csv(openings: Iterable[Opening]) -> str:
    return to_text(OPENING_HEADER, opening_rows(openings))


CYLINDER_HEADER = ("serial", "format", "section", "chambers", "opening", "position", "group")


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


def cylinders_csv(cylinders: Iterable[Cylinder]) -> str:
    return to_text(CYLINDER_HEADER, cylinder_rows(cylinders))


HOLDING_HEADER = ("tag", "symbol", "keyholder", "issued", "due", "deposit_cents", "state")


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


def holdings_csv(keys: Iterable[IssuedKey], book: DepositBook | None = None) -> str:
    return to_text(HOLDING_HEADER, holding_rows(keys, book))


def filename(kind: str, on: date) -> str:
    """What the shop calls the file when it lands in the shared folder."""
    return f"hasplib-{kind}-{on.isoformat()}.csv"

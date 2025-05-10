"""Reading a survey back in, and refusing to guess what a bad row meant.

Somebody walks a building with a tablet and a spreadsheet and comes back with
four hundred rows. Twenty of them have the room number in the floor column,
three have a function nobody has heard of, and one has a comma where a door
description should be. The useful thing is not to import three hundred and
seventy six of them quietly: it is to import none of them and hand back a
list of the rows that need fixing, with line numbers.

So every import is two passes. Read and check, which produces rows and
problems and touches nothing, and then apply, which only runs when there are
no problems. A half loaded survey is worse than no survey, because nobody
can tell afterwards which half is real.
"""

from __future__ import annotations

import csv
import io
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from hasplib.campus.openings import Function, Opening

OPENING_COLUMNS = ("number", "building", "floor", "room", "function")


@dataclass(frozen=True)
class RowProblem:
    """One row that cannot be read, and the line it is on."""

    line: int
    column: str
    message: str

    def describe(self) -> str:
        return f"line {self.line}, {self.column}: {self.message}"


@dataclass(frozen=True)
class Reading:
    """What a pass over a file produced."""

    openings: tuple[Opening, ...]
    problems: tuple[RowProblem, ...]

    @property
    def is_clean(self) -> bool:
        return not self.problems

    @property
    def count(self) -> int:
        return len(self.openings)

    def summary(self) -> str:
        return f"{self.count} rows read, {len(self.problems)} to fix"


def _rows(text: str) -> list[dict[str, str]]:
    return list(csv.DictReader(io.StringIO(text)))


def missing_columns(text: str) -> tuple[str, ...]:
    """Columns the file does not have, before any row is looked at."""
    reader = csv.DictReader(io.StringIO(text))
    present = set(reader.fieldnames or ())
    return tuple(name for name in OPENING_COLUMNS if name not in present)


def read_openings(text: str) -> Reading:
    """Read a survey, checking every row and building none of them twice."""
    absent = missing_columns(text)
    if absent:
        missing = tuple(RowProblem(1, name, "column is missing from the file") for name in absent)
        return Reading((), missing)

    openings: list[Opening] = []
    problems: list[RowProblem] = []
    seen: set[str] = set()
    for line, row in enumerate(_rows(text), start=2):
        number = (row.get("number") or "").strip()
        if not number:
            problems.append(RowProblem(line, "number", "an opening needs a number"))
            continue
        if number in seen:
            problems.append(RowProblem(line, "number", "that number is in the file twice"))
            continue
        seen.add(number)
        try:
            function = Function((row.get("function") or "").strip())
        except ValueError:
            problems.append(
                RowProblem(line, "function", f"no such function: {row.get('function')}")
            )
            continue
        exterior = (row.get("exterior") or "").strip().lower() in {"yes", "true", "1"}
        try:
            openings.append(
                Opening(
                    number=number,
                    building=(row.get("building") or "").strip(),
                    floor=(row.get("floor") or "").strip(),
                    room=(row.get("room") or "").strip(),
                    function=function,
                    description=(row.get("description") or "").strip(),
                    exterior=exterior,
                )
            )
        except Exception as refused:
            problems.append(RowProblem(line, "row", str(refused)))
    return Reading(tuple(openings), tuple(problems))


def apply_openings(schedule, reading: Reading) -> tuple[int, tuple[RowProblem, ...]]:
    """Write a clean reading into a schedule, or write none of it.

    Buildings have to be there already. A survey that names a building
    nobody has created is a survey of somewhere else.
    """
    if not reading.is_clean:
        return 0, reading.problems
    problems: list[RowProblem] = []
    for line, opening in enumerate(reading.openings, start=2):
        if opening.building not in {b.code for b in schedule.buildings()}:
            problems.append(RowProblem(line, "building", f"no such building: {opening.building}"))
        elif opening.number in schedule:
            problems.append(RowProblem(line, "number", "that opening is already on the list"))
    if problems:
        return 0, tuple(problems)
    for opening in reading.openings:
        schedule.add(opening)
    return len(reading.openings), ()


def describe(problems: Iterable[RowProblem]) -> tuple[str, ...]:
    return tuple(problem.describe() for problem in problems)


def as_csv(rows: Sequence[Sequence[str]]) -> str:
    """Build a small csv, which the tests and the examples both want."""
    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\n")
    for row in rows:
        writer.writerow(list(row))
    return out.getvalue()

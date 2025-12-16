"""Loading a file into the database, in the two passes it has to happen in.

The rule this exists to hold is that nothing is written until everything
reads. A file of four hundred openings with one bad function code used to
land three hundred and eleven of them and then stop, and the person holding
the file had no way to know which three hundred and eleven.

So a load is a dry run and then a write, and the dry run is the same code
path with the writing switched off rather than a second implementation that
would eventually disagree with the first.

The other rule is that a load never updates. Every row is a create, and a row
whose key already exists is a problem to report rather than a change to make,
because a spreadsheet somebody has been editing for a fortnight is not a
source of truth about the rows it did not change.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from sqlalchemy.orm import Session

from hasplib.campus.openings import Building
from hasplib.control.people import Affiliation, Keyholder, Standing
from hasplib.errors import ConflictError, InputError
from hasplib.persistence.mappers.campus import building_to_row, opening_to_row
from hasplib.persistence.mappers.control import keyholder_to_row
from hasplib.persistence.models.campus import BuildingRow, OpeningRow
from hasplib.persistence.models.control import KeyholderRow
from hasplib.persistence.workspace import Workspace
from hasplib.reports.imports import RowProblem, describe, read_openings

KEYHOLDER_COLUMNS = ("identifier", "name", "affiliation", "department")
BUILDING_COLUMNS = ("code", "name")


@dataclass(frozen=True)
class Load:
    """What a load did, or would have done."""

    kind: str
    written: int
    problems: tuple[RowProblem, ...]
    dry_run: bool = False

    @property
    def is_clean(self) -> bool:
        return not self.problems

    def describe(self) -> tuple[str, ...]:
        head = f"{self.kind}: {self.written} rows"
        if self.dry_run:
            head = f"{self.kind}: {self.written} rows would load"
        if self.is_clean:
            return (head,)
        return (head, f"{len(self.problems)} problems", *describe(self.problems))


def _rows(text: str, columns: Sequence[str]) -> tuple[list[dict[str, str]], list[RowProblem]]:
    import csv
    import io

    reader = csv.DictReader(io.StringIO(text))
    present = set(reader.fieldnames or ())
    absent = [name for name in columns if name not in present]
    if absent:
        return [], [RowProblem(1, name, "column is missing from the file") for name in absent]
    return list(reader), []


class ImportService:
    """Files in, rows out, and nothing written until the whole file reads."""

    def __init__(self, session: Session, workspace: Workspace | None = None) -> None:
        self.session = session
        self.space = workspace or Workspace(session)

    def load_buildings(self, text: str, dry_run: bool = False) -> Load:
        rows, problems = _rows(text, BUILDING_COLUMNS)
        wanted: list[Building] = []
        for line, row in enumerate(rows, start=2):
            code = (row.get("code") or "").strip().upper()
            try:
                built = int(row["built"]) if row.get("built") else None
                wanted.append(Building(code, (row.get("name") or "").strip(), built))
            except (ValueError, InputError, ConflictError) as refused:
                problems.append(RowProblem(line, "row", str(refused)))
                continue
            if self.session.get(BuildingRow, code) is not None:
                problems.append(RowProblem(line, "code", "that building is already there"))
        if problems:
            return Load("buildings", 0, tuple(problems), dry_run)
        if dry_run:
            return Load("buildings", len(wanted), (), True)
        for building in wanted:
            self.session.add(building_to_row(building))
        self.session.flush()
        return Load("buildings", len(wanted), ())

    def load_openings(self, text: str, dry_run: bool = False) -> Load:
        reading = read_openings(text)
        problems = list(reading.problems)
        for line, opening in enumerate(reading.openings, start=2):
            if self.session.get(BuildingRow, opening.building) is None:
                problems.append(
                    RowProblem(line, "building", f"no such building: {opening.building}")
                )
            elif self.session.get(OpeningRow, opening.number) is not None:
                problems.append(RowProblem(line, "number", "that opening is already there"))
        if problems:
            return Load("openings", 0, tuple(problems), dry_run)
        if dry_run:
            return Load("openings", reading.count, (), True)
        for opening in reading.openings:
            self.session.add(opening_to_row(opening))
        self.session.flush()
        return Load("openings", reading.count, ())

    def load_keyholders(self, text: str, dry_run: bool = False) -> Load:
        rows, problems = _rows(text, KEYHOLDER_COLUMNS)
        wanted: list[Keyholder] = []
        for line, row in enumerate(rows, start=2):
            identifier = (row.get("identifier") or "").strip()
            try:
                wanted.append(self._keyholder(row))
            except (ValueError, KeyError, InputError, ConflictError) as refused:
                problems.append(RowProblem(line, "row", str(refused)))
                continue
            if self.session.get(KeyholderRow, identifier) is not None:
                problems.append(RowProblem(line, "identifier", "that person is already there"))
        if problems:
            return Load("keyholders", 0, tuple(problems), dry_run)
        if dry_run:
            return Load("keyholders", len(wanted), (), True)
        for person in wanted:
            self.session.add(keyholder_to_row(person))
        self.session.flush()
        return Load("keyholders", len(wanted), ())

    def _keyholder(self, row: dict[str, str]) -> Keyholder:
        ends = (row.get("ends_on") or "").strip()
        standing = (row.get("standing") or "active").strip()
        return Keyholder(
            identifier=(row.get("identifier") or "").strip(),
            name=(row.get("name") or "").strip(),
            affiliation=Affiliation((row.get("affiliation") or "").strip()),
            department=(row.get("department") or "").strip(),
            standing=Standing(standing),
            ends_on=date.fromisoformat(ends) if ends else None,
        )

    def load(self, kind: str, text: str, dry_run: bool = False) -> Load:
        """One file of a named kind, which is how the command line reaches this."""
        readers = {
            "buildings": self.load_buildings,
            "openings": self.load_openings,
            "keyholders": self.load_keyholders,
        }
        found = readers.get(kind)
        if found is None:
            raise InputError("nothing loads under that name", kind=kind)
        return found(text, dry_run)

    def kinds(self) -> tuple[str, ...]:
        return ("buildings", "keyholders", "openings")

    def columns_for(self, kind: str) -> tuple[str, ...]:
        wanted = {
            "buildings": BUILDING_COLUMNS,
            "openings": ("number", "building", "floor", "room", "function"),
            "keyholders": KEYHOLDER_COLUMNS,
        }
        found = wanted.get(kind)
        if found is None:
            raise InputError("nothing loads under that name", kind=kind)
        return tuple(found)

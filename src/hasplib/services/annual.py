"""Taking the return, filing it, and arguing with last year.

``measure`` and ``file_return`` are separate on purpose. Everybody wants to
run the numbers before they commit to them, and a shop that has to file a
return in order to see it will file three and delete two, which is how a
trend acquires a hole in it.

Filing over a year already on record is refused rather than merged. The one
time somebody wants to do that is after a clean up, when the numbers have got
better, and letting last year improve quietly is exactly what a return exists
to prevent.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.models.annual import AnnualReturnRow
from hasplib.persistence.repositories import Repository
from hasplib.persistence.workspace import Workspace
from hasplib.reports.annual import (
    Return,
    annual_return,
    concentration,
    master_table,
    movement,
    returns_table,
    take,
    unaccounted_table,
)


class ReturnRepository(Repository[AnnualReturnRow]):
    model = AnnualReturnRow
    noun = "annual return"
    order = (AnnualReturnRow.year,)

    def since(self, year: int) -> Sequence[AnnualReturnRow]:
        return self.rows(AnnualReturnRow.year >= year)

    def latest(self) -> AnnualReturnRow | None:
        found = self.all()
        return found[-1] if found else None


def to_return(row: AnnualReturnRow) -> Return:
    return Return(
        year=row.year,
        keys_out=row.keys_out,
        masters_out=row.masters_out,
        unaccounted=row.unaccounted,
        keyholders=row.keyholders,
    )


def to_row(found: Return, on: date, filed_by: str = "", note: str = "") -> AnnualReturnRow:
    return AnnualReturnRow(
        year=found.year,
        measured_on=on,
        keys_out=found.keys_out,
        masters_out=found.masters_out,
        unaccounted=found.unaccounted,
        keyholders=found.keyholders,
        filed_by=filed_by,
        note=note,
    )


class AnnualService:
    """The return: measured off today, filed once, compared against last year."""

    def __init__(self, session: Session, workspace: Workspace | None = None) -> None:
        self.session = session
        self.space = workspace or Workspace(session)
        self.returns = ReturnRepository(session)

    def measure(self, on: date) -> Return:
        """Run the numbers without committing to them."""
        return take(self.space.ledger(), self.space.directory(), on)

    def filed(self, year: int) -> AnnualReturnRow:
        found = self.returns.find(year)
        if found is None:
            raise NotFoundError("no return was filed for that year", year=year)
        return found

    def previous(self, year: int) -> Return | None:
        """The most recent filed return before a year, or nothing."""
        earlier = [row for row in self.returns.all() if row.year < year]
        if not earlier:
            return None
        return to_return(earlier[-1])

    def file_return(self, on: date, filed_by: str = "", note: str = "") -> AnnualReturnRow:
        """Measure and write it down, refusing to file over a year."""
        if self.returns.find(on.year) is not None:
            raise ConflictError("that year is already filed", year=on.year)
        row = to_row(self.measure(on), on, filed_by, note)
        self.session.add(row)
        self.session.flush()
        return row

    def withdraw(self, year: int, note: str) -> AnnualReturnRow:
        """Take a return off the record, which needs a reason on it.

        Not a delete. The row stays and the note says why the numbers on it
        are not to be used, because a year that vanishes from a trend is a
        year somebody has to go and look for in an email.
        """
        if not note.strip():
            raise ConflictError("withdrawing a return says why", year=year)
        row = self.filed(year)
        row.note = f"withdrawn: {note}"
        self.session.flush()
        return row

    def history(self) -> tuple[Return, ...]:
        return tuple(to_return(row) for row in self.returns.all())

    def sheet(self, on: date) -> tuple[str, ...]:
        """The whole return as the page it gets printed on."""
        return annual_return(
            self.space.ledger(),
            self.space.directory(),
            on,
            self.space.coverage(),
            self.previous(on.year),
        )

    def masters(self) -> tuple[str, ...]:
        return master_table(self.space.ledger(), self.space.coverage())

    def unaccounted(self, on: date) -> tuple[str, ...]:
        return unaccounted_table(self.space.ledger(), on)

    def concentration(self, limit: int = 5) -> tuple[tuple[str, int], ...]:
        return concentration(self.space.ledger(), limit)

    def trend(self) -> tuple[str, ...]:
        return returns_table(self.history())

    def against_last_year(self, on: date) -> tuple[str, ...]:
        return movement(self.measure(on), self.previous(on.year))

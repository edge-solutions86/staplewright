"""Handing out the next number in a series, without leaving holes in it.

Every series the shop uses is consecutive and people notice when it is not. A
key tag between 41207 and 41209 exists somewhere by definition, and a work
order number that skips 1042 gets somebody asking what happened on that job.

So the counter row is read with a lock and written in the same transaction as
whatever consumes the number. If the transaction rolls back the number is
never handed out, and a database that does not support the lock clause is
still correct because the update is inside the transaction either way.
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.persistence.models.counters import CounterRow


class Numbering:
    """The next number in each series, taken one at a time."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def open_series(
        self, name: str, prefix: str = "", width: int = 4, start: int = 1
    ) -> CounterRow:
        """Start a series. A series is opened once and then only walked."""
        if not name.strip():
            raise InputError("a series needs a name")
        if width < 1:
            raise InputError("a number is at least one digit wide", series=name)
        if start < 1:
            raise InputError("a series starts at one or later", series=name)
        if self.session.get(CounterRow, name) is not None:
            raise ConflictError("that series is already open", series=name)
        row = CounterRow(name=name, prefix=prefix, width=width, next_value=start)
        self.session.add(row)
        self.session.flush()
        return row

    def _locked(self, name: str) -> CounterRow:
        statement = select(CounterRow).where(CounterRow.name == name)
        if self.session.bind is not None and self.session.bind.dialect.name != "sqlite":
            statement = statement.with_for_update()
        row = self.session.execute(statement).scalar_one_or_none()
        if row is None:
            raise NotFoundError("no such series", series=name)
        return row

    def peek(self, name: str) -> str:
        """What the next number would be, without taking it."""
        row = self._locked(name)
        return row.render(row.next_value)

    def next(self, name: str) -> str:
        """Take the next number. Rolling back puts it back."""
        row = self._locked(name)
        value = row.next_value
        row.next_value = value + 1
        self.session.flush()
        return row.render(value)

    def reserve(self, name: str, count: int) -> tuple[str, ...]:
        """Take a run of numbers at once, for a batch of keys off one order."""
        if count < 1:
            raise InputError("reserving nothing is not a reservation", series=name)
        row = self._locked(name)
        first = row.next_value
        row.next_value = first + count
        self.session.flush()
        return tuple(row.render(value) for value in range(first, first + count))

    def series(self, name: str) -> CounterRow:
        return self._locked(name)

    def all_series(self) -> tuple[CounterRow, ...]:
        statement = select(CounterRow).order_by(CounterRow.name)
        return tuple(self.session.execute(statement).scalars().all())

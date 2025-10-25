"""Getting a table out of the database and into whatever is asking for it.

The tables in the export module take domain objects, not rows, and that is on
purpose: they can be tested against a schedule somebody built in four lines.
What they cannot do is get that schedule out of a database, which is this.

Every method here loads through the loaders rather than querying, so an
export sees exactly what a report sees. An export that queried directly would
eventually disagree with the report of the same name, and then the finance
system and the printed sheet would be arguing about which is right.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date
from typing import Any

from sqlalchemy.orm import Session

from hasplib.errors import InputError, NotFoundError
from hasplib.persistence.loaders import (
    load_deposits,
    load_ledger,
    load_openings,
    load_register,
    load_schedule,
)
from hasplib.persistence.repositories import SystemRepository
from hasplib.reports.exports import (
    CYLINDERS,
    HOLDINGS,
    OPENINGS,
    SCHEDULE,
    Row,
    Table,
    table,
    table_names,
)

FORMATS = ("csv", "json")


class ExportService:
    """Every export the shop hands out, built off the same loaders as the reports."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.systems = SystemRepository(session)

    def names(self) -> tuple[str, ...]:
        return table_names()

    def rows_for(self, name: str, system: str = "") -> tuple[Table, list[Row]]:
        """The table and its rows, which is everything a format needs."""
        wanted = table(name)
        if name == "schedule":
            return wanted, SCHEDULE.rows(self._schedule(system).entries())
        if name == "openings":
            return wanted, OPENINGS.rows(load_openings(self.session))
        if name == "cylinders":
            return wanted, CYLINDERS.rows(load_register(self.session))
        if name == "holdings":
            return wanted, HOLDINGS.rows(
                load_ledger(self.session).out(), load_deposits(self.session)
            )
        raise NotFoundError("nothing is exported under that name", table=name)

    def text(self, name: str, shape: str = "csv", system: str = "") -> str:
        """One export as a document, in csv or json."""
        if shape not in FORMATS:
            raise InputError("that is not a format this exports", shape=shape)
        wanted, rows = self.rows_for(name, system)
        from hasplib.reports.exports import to_json, to_text

        if shape == "json":
            return to_json(wanted.header, rows)
        return to_text(wanted.header, rows)

    def records(self, name: str, system: str = "") -> list[dict[str, Any]]:
        from hasplib.reports.exports import to_records

        wanted, rows = self.rows_for(name, system)
        return to_records(wanted.header, rows)

    def filename(self, name: str, on: date, shape: str = "csv") -> str:
        return table(name).filename(on, shape)

    def every_table(self, on: date, shape: str = "csv") -> dict[str, str]:
        """Every export that does not need a system named, keyed by filename."""
        found: dict[str, str] = {}
        for name in self.names():
            if name == "schedule":
                continue
            found[self.filename(name, on, shape)] = self.text(name, shape)
        return found

    def _schedule(self, system: str) -> Any:
        if not system.strip():
            raise InputError("a schedule export names a system")
        row = self.systems.by_name(system)
        if row is None:
            raise NotFoundError("no such keying system", system=system)
        return load_schedule(self.session, row)

    def systems_to_export(self) -> Sequence[str]:
        return tuple(row.name for row in self.systems.live())

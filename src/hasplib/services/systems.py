"""Opening a keying system, drawing symbols on it, and cutting them.

This is the first place the pieces meet. The chart comes off a table, the
keyway family comes off another one, the policy comes from settings, and the
domain objects that fall out of those are what decide whether a cut is
allowed. Nothing in the domain knows there is a database and nothing in the
database knows what macs is.

Cutting is the interesting method. It goes through the schedule, which checks
the rules and the duplicate, and then through the quarantine, which knows
about cuts retired years ago on systems that no longer exist. Either can
refuse, and the order matters: a cut that breaks macs is a mistake worth
correcting, while a cut that is quarantined is a fact nobody can argue with.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.schedule import KeyingSchedule
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.loaders import (
    load_family,
    load_quarantine,
    load_schedule,
)
from hasplib.persistence.mappers.keying import entry_to_row, groups_from_rows
from hasplib.persistence.mappers.reference import chart_from_row
from hasplib.persistence.models.keying import ScheduleEntryRow, SystemRow
from hasplib.persistence.models.reference import KeywaySectionRow
from hasplib.persistence.repositories import SystemRepository
from hasplib.progression.groups import KeyingGroup
from hasplib.progression.validate import Finding, review
from hasplib.rekey.quarantine import Quarantine
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.rules import CuttingRules, RuleSet


class SystemService:
    """Everything that happens to a keying system, one session at a time."""

    def __init__(self, session: Session, rules: CuttingRules | None = None) -> None:
        self.session = session
        self.systems = SystemRepository(session)
        self.rules = rules or CuttingRules()

    def open_system(
        self,
        name: str,
        chart_id: int,
        family_name: str,
        top_section: str,
        step: int = 2,
        opened_on: date | None = None,
    ) -> SystemRow:
        if self.systems.by_name(name) is not None:
            raise ConflictError("that system is already open", system=name)
        row = SystemRow(
            name=name,
            chart_id=chart_id,
            family_name=family_name,
            top_section=top_section,
            progression_step=step,
            opened_on=opened_on,
        )
        self.systems.add(row)
        self.session.flush()
        top = KeySymbol.parse("GGM")
        self.session.add(ScheduleEntryRow(system_id=row.id, symbol=top, section=top_section))
        self.session.flush()
        return row

    def system(self, name: str) -> SystemRow:
        found = self.systems.by_name(name)
        if found is None:
            raise NotFoundError("no such keying system", system=name)
        return found

    def family(self, family_name: str) -> KeywayFamily:
        statement = select(KeywaySectionRow).where(KeywaySectionRow.family_name == family_name)
        if not self.session.execute(statement).scalars().all():
            raise NotFoundError("no such keyway family", family=family_name)
        return load_family(self.session, family_name)

    def ruleset(self, system: SystemRow) -> RuleSet:
        return RuleSet(chart_from_row(system.chart), self.rules)

    def schedule(self, name: str) -> KeyingSchedule:
        """The whole schedule, rebuilt out of its rows."""
        system = self.system(name)
        self.family(system.family_name)
        return load_schedule(self.session, system, self.rules)

    def draw(self, name: str, symbol: KeySymbol, section: str | None = None) -> ScheduleEntryRow:
        """Put a symbol on the schedule without cutting it."""
        system = self.system(name)
        schedule = self.schedule(name)
        entry = schedule.add(symbol, section)
        row = entry_to_row(entry, system.id)
        self.session.add(row)
        self.session.flush()
        return row

    def cut(self, name: str, symbol: KeySymbol, bitting: Bitting) -> ScheduleEntryRow:
        """Cut a symbol, refusing anything the rules or the quarantine refuse."""
        system = self.system(name)
        schedule = self.schedule(name)
        schedule.assign(symbol, bitting)
        quarantine = self.quarantine()
        if quarantine.is_retired(bitting):
            entry = quarantine.entry(bitting)
            raise ConflictError(
                "that cut is out of service",
                bitting=bitting.render(),
                reason=str(entry.reason),
                retired_on=entry.retired_on.isoformat(),
            )
        row = self._entry_row(system.id, symbol)
        row.bitting = bitting
        self.session.flush()
        return row

    def uncut(self, name: str, symbol: KeySymbol) -> ScheduleEntryRow:
        """Take the cuts off a symbol and leave the symbol on the schedule."""
        system = self.system(name)
        row = self._entry_row(system.id, symbol)
        row.bitting = None
        self.session.flush()
        return row

    def quarantine(self) -> Quarantine:
        return load_quarantine(self.session)

    def groups(self, name: str) -> dict[str, KeyingGroup]:
        system = self.system(name)
        return groups_from_rows(self.systems.groups(system.id))

    def review(self, name: str) -> tuple[Finding, ...]:
        """Everything wrong with one system, worst first."""
        system = self.system(name)
        return review(self.schedule(name), step=system.progression_step)

    def archive(self, name: str) -> SystemRow:
        system = self.system(name)
        if system.archived:
            raise ConflictError("that system is already archived", system=name)
        system.archived = True
        self.session.flush()
        return system

    def _entry_row(self, system_id: int, symbol: KeySymbol) -> ScheduleEntryRow:
        statement = select(ScheduleEntryRow).where(
            ScheduleEntryRow.system_id == system_id,
            ScheduleEntryRow.symbol == symbol.render(),
        )
        found = self.session.execute(statement).scalar_one_or_none()
        if found is None:
            raise NotFoundError("that symbol is not on the schedule", symbol=symbol.render())
        return found

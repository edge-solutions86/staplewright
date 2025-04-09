"""Rebuilding whole domain objects out of a session, in one place.

Two services had each grown a private method that read the same four tables
and built the same coverage map, and they had already started to differ:
one of them passed the group names in and the other did not, so a cylinder
looked unmapped from one side and keyed from the other.

So the loaders live here. They are the only code that knows both a session
and a domain object, which is the seam a repository and a mapper leave
between them.
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.campus.coverage import CoverageMap
from hasplib.campus.hardware import HardwareRegister
from hasplib.campus.openings import OpeningSchedule
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger
from hasplib.control.people import Directory
from hasplib.keys.schedule import KeyingSchedule
from hasplib.persistence.mappers.campus import (
    register_from_rows,
)
from hasplib.persistence.mappers.campus import (
    schedule_from_rows as openings_from_rows,
)
from hasplib.persistence.mappers.control import (
    deposit_book_from_rows,
    directory_from_rows,
    ledger_from_rows,
)
from hasplib.persistence.mappers.keying import groups_from_rows, schedule_from_rows
from hasplib.persistence.mappers.reference import chart_from_row, family_from_rows
from hasplib.persistence.models.campus import BuildingRow, CylinderRow, OpeningRow
from hasplib.persistence.models.control import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
)
from hasplib.persistence.models.keying import GroupRow, ScheduleEntryRow, SystemRow
from hasplib.persistence.models.reference import KeywaySectionRow
from hasplib.persistence.models.rekey import RetiredBittingRow
from hasplib.progression.groups import KeyingGroup
from hasplib.rekey.quarantine import Quarantine, RetirementReason
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.rules import CuttingRules, RuleSet


def _all(session: Session, model):
    return session.execute(select(model)).scalars().all()


def load_openings(session: Session) -> OpeningSchedule:
    return openings_from_rows(_all(session, BuildingRow), _all(session, OpeningRow))


def load_group_names(session: Session) -> dict[int, str]:
    return {row.id: row.name for row in _all(session, GroupRow)}


def load_groups(session: Session) -> dict[str, KeyingGroup]:
    return groups_from_rows(_all(session, GroupRow))


def load_register(session: Session) -> HardwareRegister:
    return register_from_rows(
        load_openings(session), _all(session, CylinderRow), load_group_names(session)
    )


def load_coverage(session: Session) -> CoverageMap:
    """Cylinders, their groups, and the doors those groups sit in."""
    return CoverageMap(load_register(session), load_groups(session))


def load_directory(session: Session) -> Directory:
    return directory_from_rows(_all(session, KeyholderRow), _all(session, AuthorizerRow))


def load_ledger(session: Session) -> KeyLedger:
    return ledger_from_rows(load_directory(session), _all(session, IssuedKeyRow))


def load_deposits(session: Session) -> DepositBook:
    return deposit_book_from_rows(_all(session, DepositLineRow))


def load_family(session: Session, family_name: str) -> KeywayFamily:
    statement = select(KeywaySectionRow).where(KeywaySectionRow.family_name == family_name)
    rows = session.execute(statement).scalars().all()
    return family_from_rows(family_name, rows)


def load_schedule(
    session: Session, system: SystemRow, rules: CuttingRules | None = None
) -> KeyingSchedule:
    statement = select(ScheduleEntryRow).where(ScheduleEntryRow.system_id == system.id)
    rows = session.execute(statement).scalars().all()
    ruleset = RuleSet(chart_from_row(system.chart), rules or CuttingRules())
    return schedule_from_rows(
        system.name, ruleset, load_family(session, system.family_name), system.top_section, rows
    )


def load_quarantine(session: Session, release_after_years: int = 7) -> Quarantine:
    hold = Quarantine(release_after_years)
    for row in _all(session, RetiredBittingRow):
        hold.retire(
            row.bitting,
            RetirementReason(row.reason),
            row.retired_on,
            symbol=row.symbol,
            note=row.note,
        )
    return hold

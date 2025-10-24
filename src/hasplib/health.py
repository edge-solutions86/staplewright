"""Self checks, for the morning somebody asks whether the database is sound.

None of these are unit tests. They are the questions worth asking of a live
database, and every one of them came from something that actually went wrong:
a cylinder installed on an opening nobody has a record of, a key issued to a
person whose appointment ended two years ago, a schedule entry cut to a
bitting that is in quarantine, a keying group with no cylinders on it.

Each check hands back a list of findings rather than raising, because the
point is to see all of them at once. A check that stops at the first problem
turns a morning into a week of one fix at a time.

The severities are the only judgement here. A warning is something to look
at. A fault is something that means a report somewhere is already wrong.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from datetime import date
from enum import IntEnum

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from hasplib.campus.openings import KEYED_FUNCTIONS
from hasplib.persistence.models.campus import CylinderRow, OpeningRow
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.persistence.models.keying import GroupRow, ScheduleEntryRow, SystemRow
from hasplib.persistence.models.rekey import RetiredBittingRow
from hasplib.persistence.models.transfer import TransferRow


class Severity(IntEnum):
    """How much of a problem a finding is."""

    NOTE = 1
    WARNING = 2
    FAULT = 3

    @property
    def label(self) -> str:
        return self.name.lower()


@dataclass(frozen=True)
class Finding:
    """One thing wrong, named well enough to go and look at it."""

    check: str
    severity: Severity
    subject: str
    detail: str

    def describe(self) -> str:
        return f"{self.severity.label:<7} {self.subject:<16} {self.detail}"


def uninstalled_cylinders(session: Session, today: date) -> list[Finding]:
    """Cylinders keyed to a group but sitting on a shelf.

    Not wrong on its own. Wrong in quantity, because a shelf full of keyed
    cores is a shelf full of keys somebody else can read the cuts off.
    """
    statement = select(CylinderRow).where(
        CylinderRow.installed_at.is_(None), CylinderRow.group_id.is_not(None)
    )
    found = session.execute(statement.order_by(CylinderRow.serial)).scalars().all()
    return [
        Finding("shelved keyed cores", Severity.NOTE, row.serial, "keyed and not installed")
        for row in found
    ]


def unkeyed_openings(session: Session, today: date) -> list[Finding]:
    """Installed cylinders nobody wrote a keying group against."""
    statement = select(CylinderRow).where(
        CylinderRow.installed_at.is_not(None), CylinderRow.group_id.is_(None)
    )
    found = session.execute(statement.order_by(CylinderRow.serial)).scalars().all()
    return [
        Finding(
            "unmapped cylinders",
            Severity.FAULT,
            row.serial,
            f"installed at {row.installed_at} and answers to nothing on record",
        )
        for row in found
    ]


def keys_held_by_lapsed_people(session: Session, today: date) -> list[Finding]:
    """Keys still out to somebody whose appointment has ended."""
    statement = (
        select(IssuedKeyRow, KeyholderRow)
        .join(KeyholderRow, IssuedKeyRow.keyholder == KeyholderRow.identifier)
        .where(IssuedKeyRow.state == "out", KeyholderRow.ends_on < today)
        .order_by(IssuedKeyRow.tag)
    )
    found = session.execute(statement).all()
    return [
        Finding(
            "keys out to lapsed holders",
            Severity.FAULT,
            key.tag,
            f"{person.identifier} ended {person.ends_on.isoformat()}",
        )
        for key, person in found
    ]


def quarantined_cuts_in_use(session: Session, today: date) -> list[Finding]:
    """Schedule entries cut to a bitting somebody retired."""
    retired = {
        row.bitting.render(): row
        for row in session.execute(select(RetiredBittingRow)).scalars().all()
    }
    if not retired:
        return []
    statement = select(ScheduleEntryRow).where(ScheduleEntryRow.bitting.is_not(None))
    found = session.execute(statement.order_by(ScheduleEntryRow.symbol)).scalars().all()
    lines = []
    for row in found:
        if row.bitting is None:
            continue
        held = retired.get(row.bitting.render())
        if held is None:
            continue
        lines.append(
            Finding(
                "quarantined cuts in use",
                Severity.FAULT,
                row.symbol.render(),
                f"cut to {row.bitting.render()}, retired as {held.reason}",
            )
        )
    return lines


def groups_with_no_cylinders(session: Session, today: date) -> list[Finding]:
    """Keying groups nothing is keyed to, which is usually a rename gone wrong."""
    counted = (
        select(CylinderRow.group_id, func.count().label("held"))
        .where(CylinderRow.group_id.is_not(None))
        .group_by(CylinderRow.group_id)
        .subquery()
    )
    statement = (
        select(GroupRow)
        .outerjoin(counted, GroupRow.id == counted.c.group_id)
        .where(counted.c.held.is_(None))
        .order_by(GroupRow.name)
    )
    found = session.execute(statement).scalars().all()
    return [
        Finding("empty keying groups", Severity.WARNING, row.name, "nothing is keyed to it")
        for row in found
    ]


def uncut_symbols(session: Session, today: date) -> list[Finding]:
    """Symbols drawn on a schedule that nobody has cut."""
    statement = (
        select(ScheduleEntryRow, SystemRow)
        .join(SystemRow, ScheduleEntryRow.system_id == SystemRow.id)
        .where(ScheduleEntryRow.bitting.is_(None), SystemRow.archived.is_(False))
        .order_by(ScheduleEntryRow.symbol)
    )
    found = session.execute(statement).all()
    return [
        Finding("uncut symbols", Severity.NOTE, entry.symbol.render(), f"drawn on {system.name}")
        for entry, system in found
    ]


def openings_with_no_cylinder(session: Session, today: date) -> list[Finding]:
    """Keyed openings with nothing in them, which is a door standing open."""
    held = (
        select(CylinderRow.installed_at)
        .where(CylinderRow.installed_at.is_not(None))
        .distinct()
        .subquery()
    )
    statement = (
        select(OpeningRow)
        .outerjoin(held, OpeningRow.number == held.c.installed_at)
        .where(
            OpeningRow.function.in_([item.value for item in KEYED_FUNCTIONS]),
            held.c.installed_at.is_(None),
        )
        .order_by(OpeningRow.number)
    )
    found = session.execute(statement).scalars().all()
    return [
        Finding(
            "keyed openings with nothing in them",
            Severity.WARNING,
            row.number,
            "on the schedule as keyed and holds no cylinder",
        )
        for row in found
    ]


def keys_that_wandered(session: Session, today: date) -> list[Finding]:
    """Keys whose ledger holder is not the person who has them.

    Every line here is a key the shop would have asked the wrong person to
    produce, which is the single most embarrassing thing an audit turns up.
    """
    from hasplib.persistence.mappers.transfer import strays

    keys = session.execute(select(IssuedKeyRow).where(IssuedKeyRow.state == "out")).scalars().all()
    rows = session.execute(select(TransferRow)).scalars().all()
    if not rows:
        return []
    return [
        Finding(
            "keys that changed hands",
            Severity.WARNING,
            tag,
            f"ledger says {ledger}, actually with {now}",
        )
        for tag, ledger, now in strays(keys, rows, today)
    ]


def handovers_nobody_confirmed(session: Session, today: date) -> list[Finding]:
    """Claims sitting open past the window, which means a key is nowhere."""
    from hasplib.control.transfer import TransferPolicy
    from hasplib.persistence.mappers.transfer import book_from_rows

    rows = session.execute(select(TransferRow)).scalars().all()
    if not rows:
        return []
    book = book_from_rows(rows, TransferPolicy())
    return [
        Finding(
            "handovers nobody confirmed",
            Severity.FAULT,
            item.number,
            f"{item.tag} claimed {item.claimed_on.isoformat()}, nobody signed",
        )
        for item in book.late(today)
    ]


Check = Callable[[Session, date], list[Finding]]

CHECKS: tuple[Check, ...] = (
    unkeyed_openings,
    keys_held_by_lapsed_people,
    quarantined_cuts_in_use,
    handovers_nobody_confirmed,
    groups_with_no_cylinders,
    keys_that_wandered,
    openings_with_no_cylinder,
    uninstalled_cylinders,
    uncut_symbols,
)


def run_checks(session: Session, today: date, checks: Sequence[Check] = CHECKS) -> list[Finding]:
    """Every check, worst first, then by what it is about."""
    found: list[Finding] = []
    for check in checks:
        found.extend(check(session, today))
    found.sort(key=lambda item: (-int(item.severity), item.check, item.subject))
    return found


def worst(findings: Sequence[Finding]) -> Severity | None:
    """The worst severity in a set, or nothing when it is clean."""
    if not findings:
        return None
    return max(item.severity for item in findings)


def counts_by_severity(findings: Sequence[Finding]) -> dict[str, int]:
    counted = {item.label: 0 for item in Severity}
    for finding in findings:
        counted[finding.severity.label] += 1
    return counted


def health_report(session: Session, today: date) -> tuple[str, ...]:
    """The whole check, as the page somebody reads on a monday."""
    findings = run_checks(session, today)
    head = f"self check at {today.isoformat()}"
    if not findings:
        return (head, "nothing to answer for")
    counted = counts_by_severity(findings)
    lines = [
        head,
        f"{counted['fault']} faults, {counted['warning']} warnings, {counted['note']} notes",
    ]
    seen = ""
    for finding in findings:
        if finding.check != seen:
            lines.append(finding.check)
            seen = finding.check
        lines.append(f"  {finding.describe()}")
    return tuple(lines)


def exit_code(findings: Sequence[Finding]) -> int:
    """Nought unless something is actually wrong, for a scheduled run."""
    return 1 if worst(findings) is Severity.FAULT else 0

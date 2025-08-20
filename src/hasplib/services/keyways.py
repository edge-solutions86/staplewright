"""Keyway families out of the database, and the patent clock over them.

The interesting method is ``at_risk``. The patent module can tell you a
section has gone open, but it has no idea whether the shop keys anything on
that section, and a list of expired sections nobody uses is noise. Crossing
it with the live systems is the whole value, and the cross has to happen
here because it is the only layer that can see both.

``check_section`` is deliberately a separate call rather than something
buried in opening a system. A shop that has decided to key on a section that
goes open in three years has usually decided that on purpose, and a service
that refuses outright would just get worked around. So the service refuses by
default and takes a flag that says somebody knows.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.loaders import load_family
from hasplib.persistence.mappers.reference import family_to_rows, parents_of
from hasplib.persistence.models.keying import SystemRow
from hasplib.persistence.models.reference import KeywaySectionRow
from hasplib.persistence.repositories import Repository, SystemRepository
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.patents import (
    Exposure,
    closing_sections,
    exposure_of,
    exposures,
    migration_order,
    next_expiry,
    open_sections,
    patent_report,
    section_is_safe,
)

PLANNING_YEARS = 5


class SectionRepository(Repository[KeywaySectionRow]):
    model = KeywaySectionRow
    noun = "keyway section"
    order = (KeywaySectionRow.code,)

    def in_family(self, family_name: str) -> Sequence[KeywaySectionRow]:
        return self.rows(KeywaySectionRow.family_name == family_name)

    def families(self) -> tuple[str, ...]:
        names = {row.family_name for row in self.all()}
        return tuple(sorted(names))


@dataclass(frozen=True)
class RiskLine:
    """One section the shop keys on, and what has happened to it."""

    family: str
    section: str
    systems: tuple[str, ...]
    exposure: Exposure

    def describe(self, today: date) -> str:
        names = ", ".join(self.systems)
        return f"{self.exposure.describe(today)} carrying {names}"


class KeywayService:
    """Families, the sections under them, and where the patents stand."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.sections = SectionRepository(session)
        self.systems = SystemRepository(session)

    def family(self, name: str) -> KeywayFamily:
        if not self.sections.in_family(name):
            raise NotFoundError("no such keyway family", family=name)
        return load_family(self.session, name)

    def families(self) -> tuple[str, ...]:
        return self.sections.families()

    def store(self, family: KeywayFamily) -> Sequence[KeywaySectionRow]:
        """Write a whole family down, parent before child, refusing a repeat.

        The rows come out of the mapper without their parent ids, because a
        mapper that filled those in would need a session. So the walk happens
        here, root first, flushing each row so the next one has something to
        point at.
        """
        if self.sections.in_family(family.name):
            raise ConflictError("that family is already stored", family=family.name)
        above = parents_of(family)
        ids: dict[str, int] = {}
        rows = []
        for row in family_to_rows(family):
            parent = above[row.code]
            row.parent_id = None if parent is None else ids[parent]
            self.session.add(row)
            self.session.flush()
            ids[row.code] = row.id
            rows.append(row)
        return rows

    def exposures(self, family_name: str) -> tuple[Exposure, ...]:
        return exposures(self.family(family_name))

    def open_today(self, family_name: str, today: date) -> tuple[str, ...]:
        return open_sections(self.family(family_name), today)

    def closing(
        self, family_name: str, today: date, within_years: int = PLANNING_YEARS
    ) -> tuple[str, ...]:
        return closing_sections(self.family(family_name), today, within_years)

    def next_to_go(self, family_name: str, today: date) -> Exposure | None:
        return next_expiry(self.family(family_name), today)

    def report(self, family_name: str, today: date) -> tuple[str, ...]:
        return patent_report(self.family(family_name), today)

    def systems_on(self, family_name: str, section: str) -> tuple[str, ...]:
        """Live systems whose top section is this one, in name order."""
        statement = (
            select(SystemRow)
            .where(
                SystemRow.family_name == family_name,
                SystemRow.top_section == section,
                SystemRow.archived.is_(False),
            )
            .order_by(SystemRow.name)
        )
        return tuple(row.name for row in self.session.execute(statement).scalars().all())

    def sections_in_use(self, family_name: str) -> tuple[str, ...]:
        statement = (
            select(SystemRow.top_section)
            .where(SystemRow.family_name == family_name, SystemRow.archived.is_(False))
            .distinct()
        )
        return tuple(sorted(self.session.execute(statement).scalars().all()))

    def at_risk(self, family_name: str, today: date) -> tuple[RiskLine, ...]:
        """Sections the shop keys on that have already gone open.

        Widest first, because a root section going open exposes everything
        under it and that is the one to move off before the leaves.
        """
        family = self.family(family_name)
        in_use = set(self.sections_in_use(family_name))
        found = []
        for code in migration_order(family, today):
            if code not in in_use:
                continue
            found.append(
                RiskLine(
                    family_name,
                    code,
                    self.systems_on(family_name, code),
                    exposure_of(family.section(code)),
                )
            )
        return tuple(found)

    def check_section(
        self,
        family_name: str,
        section: str,
        today: date,
        within_years: int = PLANNING_YEARS,
        accepted: bool = False,
    ) -> None:
        """Refuse a section that will not stay restricted, unless told not to."""
        family = self.family(family_name)
        family.section(section)
        if section_is_safe(family, section, today, within_years):
            return
        if accepted:
            return
        item = exposure_of(family.section(section))
        raise ConflictError(
            "that section will not stay restricted long enough to key on",
            family=family_name,
            section=section,
            expires=item.expires_on.isoformat() if item.expires_on else "never restricted",
            horizon=within_years,
        )

    def risk_report(self, today: date) -> tuple[str, ...]:
        """Every family the shop holds, worst first, on one page."""
        lines = [f"keyway patent position at {today.isoformat()}"]
        found: list[RiskLine] = []
        for name in self.families():
            found.extend(self.at_risk(name, today))
        if not found:
            lines.append("nothing the shop keys on has gone open")
            return tuple(lines)
        lines.append(f"{len(found)} sections in use have gone open")
        lines.extend(f"  {line.describe(today)}" for line in found)
        return tuple(lines)

"""Pinning a cylinder off the schedule, which is the bench half of the work.

Everything the bench needs is already in the database and none of it is in
one place. The keying group says which symbols the cylinder answers to, the
schedule says what each of those is cut to, the chart says how deep each cut
is, and the pinning spec turns that into brass. This is the method that walks
those four and hands back a chart the bench can work from.

It refuses on an uncut symbol rather than skipping it. A cylinder pinned for
three of its four keys looks finished and is not, and the fourth key turns up
in somebody's hand a week later.
"""

from __future__ import annotations

from collections.abc import Sequence

from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.loaders import load_groups, load_schedule
from hasplib.persistence.models.campus import CylinderRow
from hasplib.persistence.models.keying import GroupRow, SystemRow
from hasplib.persistence.repositories import SystemRepository
from hasplib.pinning.cylinder import CylinderPinning, pin_cylinder
from hasplib.pinning.segments import DriverProfile, PinKit
from hasplib.pinning.stack import PinningSpec
from hasplib.progression.groups import KeyingGroup
from hasplib.reports.pinning import cylinder_chart, parts_summary


class PinningService:
    """Turn a keying group into a chart the bench can work from."""

    def __init__(self, session: Session, spec: PinningSpec) -> None:
        self.session = session
        self.spec = spec
        self.systems = SystemRepository(session)

    def system(self, name: str) -> SystemRow:
        row = self.systems.by_name(name)
        if row is None:
            raise NotFoundError("no such keying system", system=name)
        return row

    def group(self, name: str) -> KeyingGroup:
        found = load_groups(self.session).get(name)
        if found is None:
            raise NotFoundError("no such keying group", group=name)
        return found

    def bittings_for(self, system_name: str, group_name: str) -> tuple[Bitting, ...]:
        """The cuts behind a group, refusing any symbol nobody has cut."""
        schedule = load_schedule(self.session, self.system(system_name))
        group = self.group(group_name)
        found: list[Bitting] = []
        for symbol in group.ordered():
            bitting = schedule.bitting_of(symbol)
            if bitting is None:
                raise ConflictError(
                    "that key has not been cut yet",
                    group=group_name,
                    symbol=symbol.render(),
                )
            found.append(bitting)
        return tuple(found)

    def pin(
        self,
        system_name: str,
        group_name: str,
        profile: DriverProfile = DriverProfile.STANDARD,
    ) -> CylinderPinning:
        """The stacks for one keying group, ready to draw brass against."""
        return pin_cylinder(self.spec, self.bittings_for(system_name, group_name), profile)

    def chart_for(self, system_name: str, group_name: str, serial: str = "") -> tuple[str, ...]:
        """The sheet, with the cylinder serial on it when there is one."""
        pinning = self.pin(system_name, group_name)
        title = f"{group_name}  {serial}".strip()
        return cylinder_chart(pinning, title)

    def draw_from(
        self, kit: PinKit, system_name: str, group_name: str
    ) -> tuple[CylinderPinning, tuple[str, ...]]:
        """Pin it and take the brass, or refuse and take nothing."""
        pinning = self.pin(system_name, group_name)
        kit.draw(pinning.parts())
        return pinning, parts_summary(pinning.parts())

    def cylinders_for(self, group_name: str) -> Sequence[CylinderRow]:
        """Every cylinder the register says belongs to this group."""
        group = self._group_row(group_name)
        from sqlalchemy import select

        statement = select(CylinderRow).where(CylinderRow.group_id == group.id)
        return self.session.execute(statement.order_by(CylinderRow.serial)).scalars().all()

    def symbols_for(self, group_name: str) -> tuple[KeySymbol, ...]:
        return self.group(group_name).ordered()

    def _group_row(self, name: str) -> GroupRow:
        from sqlalchemy import select

        statement = select(GroupRow).where(GroupRow.name == name)
        row = self.session.execute(statement).scalar_one_or_none()
        if row is None:
            raise NotFoundError("no such keying group", group=name)
        return row

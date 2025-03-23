"""Repositories, which are the only place a query is allowed to live.

Every one of them takes a session and hands back rows. They do not commit,
they do not open transactions, and they do not know about the domain objects
either: the mappers do that, and keeping the two apart is what lets a query
be tested without a keying system and a mapper be tested without a database.

The base class is small on purpose. The interesting methods are the ones
underneath it, because those are the questions the shop actually asks, and a
repository whose only methods are get and all is a table with extra steps.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date
from typing import Any, Generic, TypeVar

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from hasplib.errors import NotFoundError
from hasplib.persistence.base import Base
from hasplib.persistence.models.campus import BuildingRow, CylinderRow, OpeningRow
from hasplib.persistence.models.control import (
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)
from hasplib.persistence.models.keying import GroupRow, ScheduleEntryRow, SystemRow
from hasplib.persistence.models.reference import ChartRow
from hasplib.persistence.models.rekey import IncidentRow, RetiredBittingRow, WorkOrderRow

RowType = TypeVar("RowType", bound=Base)


class Repository(Generic[RowType]):
    """Rows of one table, out of one session."""

    model: type[RowType]
    noun: str = "row"

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, row: RowType) -> RowType:
        self.session.add(row)
        return row

    def add_all(self, rows: Sequence[RowType]) -> Sequence[RowType]:
        self.session.add_all(list(rows))
        return rows

    def find(self, key: Any) -> RowType | None:
        return self.session.get(self.model, key)

    def get(self, key: Any) -> RowType:
        found = self.find(key)
        if found is None:
            raise NotFoundError(f"no such {self.noun}", key=str(key))
        return found

    def delete(self, row: RowType) -> None:
        self.session.delete(row)

    def all(self) -> Sequence[RowType]:
        return self.session.execute(select(self.model)).scalars().all()

    def count(self) -> int:
        return int(self.session.execute(select(func.count()).select_from(self.model)).scalar_one())

    def exists(self, key: Any) -> bool:
        return self.find(key) is not None


class ChartRepository(Repository[ChartRow]):
    model = ChartRow
    noun = "chart"

    def by_family(self, manufacturer: str, family: str) -> ChartRow | None:
        statement = select(ChartRow).where(
            ChartRow.manufacturer == manufacturer, ChartRow.family == family
        )
        return self.session.execute(statement).scalar_one_or_none()

    def for_manufacturer(self, manufacturer: str) -> Sequence[ChartRow]:
        statement = select(ChartRow).where(ChartRow.manufacturer == manufacturer)
        return self.session.execute(statement.order_by(ChartRow.family)).scalars().all()


class SystemRepository(Repository[SystemRow]):
    model = SystemRow
    noun = "keying system"

    def by_name(self, name: str) -> SystemRow | None:
        statement = select(SystemRow).where(SystemRow.name == name)
        return self.session.execute(statement).scalar_one_or_none()

    def live(self) -> Sequence[SystemRow]:
        statement = select(SystemRow).where(SystemRow.archived.is_(False))
        return self.session.execute(statement.order_by(SystemRow.name)).scalars().all()

    def entries(self, system_id: int) -> Sequence[ScheduleEntryRow]:
        statement = select(ScheduleEntryRow).where(ScheduleEntryRow.system_id == system_id)
        return self.session.execute(statement).scalars().all()

    def groups(self, system_id: int) -> Sequence[GroupRow]:
        statement = select(GroupRow).where(GroupRow.system_id == system_id)
        return self.session.execute(statement.order_by(GroupRow.name)).scalars().all()

    def uncut_symbols(self, system_id: int) -> Sequence[ScheduleEntryRow]:
        statement = select(ScheduleEntryRow).where(
            ScheduleEntryRow.system_id == system_id, ScheduleEntryRow.bitting.is_(None)
        )
        return self.session.execute(statement).scalars().all()


class OpeningRepository(Repository[OpeningRow]):
    model = OpeningRow
    noun = "opening"

    def in_building(self, code: str) -> Sequence[OpeningRow]:
        statement = select(OpeningRow).where(OpeningRow.building_code == code)
        return self.session.execute(statement.order_by(OpeningRow.number)).scalars().all()

    def exterior(self) -> Sequence[OpeningRow]:
        statement = select(OpeningRow).where(OpeningRow.exterior.is_(True))
        return self.session.execute(statement.order_by(OpeningRow.number)).scalars().all()

    def buildings(self) -> Sequence[BuildingRow]:
        statement = select(BuildingRow).order_by(BuildingRow.code)
        return self.session.execute(statement).scalars().all()


class CylinderRepository(Repository[CylinderRow]):
    model = CylinderRow
    noun = "cylinder"

    def on_opening(self, number: str) -> Sequence[CylinderRow]:
        statement = select(CylinderRow).where(CylinderRow.installed_at == number)
        return self.session.execute(statement.order_by(CylinderRow.serial)).scalars().all()

    def on_the_shelf(self) -> Sequence[CylinderRow]:
        statement = select(CylinderRow).where(CylinderRow.installed_at.is_(None))
        return self.session.execute(statement.order_by(CylinderRow.serial)).scalars().all()

    def in_group(self, group_id: int) -> Sequence[CylinderRow]:
        statement = select(CylinderRow).where(CylinderRow.group_id == group_id)
        return self.session.execute(statement.order_by(CylinderRow.serial)).scalars().all()

    def unkeyed(self) -> Sequence[CylinderRow]:
        statement = select(CylinderRow).where(
            CylinderRow.installed_at.is_not(None), CylinderRow.group_id.is_(None)
        )
        return self.session.execute(statement.order_by(CylinderRow.serial)).scalars().all()


class KeyholderRepository(Repository[KeyholderRow]):
    model = KeyholderRow
    noun = "keyholder"

    def in_department(self, department: str) -> Sequence[KeyholderRow]:
        statement = select(KeyholderRow).where(KeyholderRow.department == department)
        return self.session.execute(statement.order_by(KeyholderRow.identifier)).scalars().all()

    def lapsed(self, today: date) -> Sequence[KeyholderRow]:
        statement = select(KeyholderRow).where(
            KeyholderRow.standing == "active", KeyholderRow.ends_on < today
        )
        return self.session.execute(statement.order_by(KeyholderRow.ends_on)).scalars().all()


class KeyRepository(Repository[IssuedKeyRow]):
    model = IssuedKeyRow
    noun = "key"

    def out(self) -> Sequence[IssuedKeyRow]:
        statement = select(IssuedKeyRow).where(IssuedKeyRow.state == "out")
        return self.session.execute(statement.order_by(IssuedKeyRow.tag)).scalars().all()

    def held_by(self, identifier: str) -> Sequence[IssuedKeyRow]:
        statement = select(IssuedKeyRow).where(
            IssuedKeyRow.keyholder == identifier, IssuedKeyRow.state == "out"
        )
        return self.session.execute(statement.order_by(IssuedKeyRow.tag)).scalars().all()

    def overdue(self, today: date) -> Sequence[IssuedKeyRow]:
        statement = select(IssuedKeyRow).where(
            IssuedKeyRow.state == "out", IssuedKeyRow.due_on < today
        )
        return self.session.execute(statement.order_by(IssuedKeyRow.due_on)).scalars().all()

    def cut_to(self, symbol: str) -> Sequence[IssuedKeyRow]:
        statement = select(IssuedKeyRow).where(
            IssuedKeyRow.symbol == symbol, IssuedKeyRow.state == "out"
        )
        return self.session.execute(statement.order_by(IssuedKeyRow.tag)).scalars().all()

    def deposits_held(self) -> int:
        statement = select(func.coalesce(func.sum(IssuedKeyRow.deposit_cents), 0)).where(
            IssuedKeyRow.state == "out"
        )
        return int(self.session.execute(statement).scalar_one())


class RequestRepository(Repository[KeyRequestRow]):
    model = KeyRequestRow
    noun = "request"

    def in_state(self, state: str) -> Sequence[KeyRequestRow]:
        statement = select(KeyRequestRow).where(KeyRequestRow.state == state)
        return self.session.execute(statement.order_by(KeyRequestRow.number)).scalars().all()

    def for_keyholder(self, identifier: str) -> Sequence[KeyRequestRow]:
        statement = select(KeyRequestRow).where(KeyRequestRow.keyholder == identifier)
        return self.session.execute(statement.order_by(KeyRequestRow.raised_on)).scalars().all()


class IncidentRepository(Repository[IncidentRow]):
    model = IncidentRow
    noun = "incident"

    def since(self, when: date) -> Sequence[IncidentRow]:
        statement = select(IncidentRow).where(IncidentRow.reported_on >= when)
        return self.session.execute(statement.order_by(IncidentRow.reported_on)).scalars().all()

    def by_severity(self, severity: str) -> Sequence[IncidentRow]:
        statement = select(IncidentRow).where(IncidentRow.severity == severity)
        return self.session.execute(statement.order_by(IncidentRow.number)).scalars().all()


class WorkOrderRepository(Repository[WorkOrderRow]):
    model = WorkOrderRow
    noun = "work order"

    def open_orders(self) -> Sequence[WorkOrderRow]:
        statement = select(WorkOrderRow).where(
            WorkOrderRow.state.in_(("drafted", "scheduled", "started"))
        )
        return self.session.execute(statement.order_by(WorkOrderRow.number)).scalars().all()

    def for_technician(self, technician: str) -> Sequence[WorkOrderRow]:
        statement = select(WorkOrderRow).where(WorkOrderRow.technician == technician)
        return self.session.execute(statement.order_by(WorkOrderRow.number)).scalars().all()

    def due_on(self, when: date) -> Sequence[WorkOrderRow]:
        statement = select(WorkOrderRow).where(WorkOrderRow.scheduled_for == when)
        return self.session.execute(statement.order_by(WorkOrderRow.number)).scalars().all()


class QuarantineRepository(Repository[RetiredBittingRow]):
    model = RetiredBittingRow
    noun = "retired cut"

    def by_bitting(self, rendered: str) -> RetiredBittingRow | None:
        statement = select(RetiredBittingRow).where(RetiredBittingRow.bitting == rendered)
        return self.session.execute(statement).scalar_one_or_none()

    def permanent(self) -> Sequence[RetiredBittingRow]:
        statement = select(RetiredBittingRow).where(RetiredBittingRow.reason == "compromised")
        return self.session.execute(statement.order_by(RetiredBittingRow.id)).scalars().all()

"""Repositories, which are the only place a query is allowed to live.

Every one of them takes a session and hands back rows. They do not commit,
they do not open transactions, and they do not know about the domain objects
either: the mappers do that, and keeping the two apart is what lets a query
be tested without a keying system and a mapper be tested without a database.

The base class is small on purpose. The interesting methods are the ones
underneath it, because those are the questions the shop actually asks, and a
repository whose only methods are get and all is a table with extra steps.

What the base does carry is the shape every one of those questions has: pick
a model, narrow it, sort it, hand back a sequence. That used to be written
out three lines at a time in about thirty places, and the sort order was
quietly missing from four of them, which is the sort of thing nobody notices
until a report prints in insertion order on one machine and not another.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date
from typing import Any, Generic, TypeVar

from sqlalchemy import func, select
from sqlalchemy.orm import InstrumentedAttribute, Session
from sqlalchemy.sql.elements import ColumnElement

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
OtherRow = TypeVar("OtherRow", bound=Base)
Clause = ColumnElement[bool]
Order = InstrumentedAttribute[Any] | ColumnElement[Any]


class Repository(Generic[RowType]):
    """Rows of one table, out of one session."""

    model: type[RowType]
    noun: str = "row"
    order: tuple[Order, ...] = ()

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
        return self.require(key)

    def require(self, key: Any, message: str = "", **facts: Any) -> RowType:
        """The row, or a refusal that names it the way the caller would.

        Nine services had each written this out, and the facts on the error
        were all spelled differently: one said ``key``, one said ``number``,
        and the box service said ``box``. A caller catching one of them and
        reading a fact off it got a key error against the others.
        """
        found = self.find(key)
        if found is not None:
            return found
        raise NotFoundError(message or f"no such {self.noun}", **(facts or {"key": str(key)}))

    def delete(self, row: RowType) -> None:
        self.session.delete(row)

    def all(self) -> Sequence[RowType]:
        return self.rows()

    def count(self) -> int:
        return int(self.session.execute(select(func.count()).select_from(self.model)).scalar_one())

    def exists(self, key: Any) -> bool:
        return self.find(key) is not None

    def rows(self, *narrow: Clause, order: Order | None = None) -> Sequence[RowType]:
        """This repository's own rows, narrowed and in its usual order."""
        sorting = self.order if order is None else (order,)
        return self.rows_of(self.model, *narrow, order=sorting)

    def one(self, *narrow: Clause) -> RowType | None:
        """The single row that matches, or nothing, refusing on two."""
        return self.session.execute(select(self.model).where(*narrow)).scalar_one_or_none()

    def rows_of(
        self, model: type[OtherRow], *narrow: Clause, order: Sequence[Order] = ()
    ) -> Sequence[OtherRow]:
        """Somebody else's rows, for the questions that cross a table.

        The sort columns arrive as a tuple rather than one column because
        a mapped column on a class body is a descriptor, and reading one
        back off ``self`` hands you the value it would have on a row
        rather than the column you wrote down.
        """
        statement = select(model).where(*narrow)
        if order:
            statement = statement.order_by(*order)
        return self.session.execute(statement).scalars().all()


class ChartRepository(Repository[ChartRow]):
    model = ChartRow
    noun = "chart"
    order = (ChartRow.family,)

    def by_family(self, manufacturer: str, family: str) -> ChartRow | None:
        return self.one(ChartRow.manufacturer == manufacturer, ChartRow.family == family)

    def for_manufacturer(self, manufacturer: str) -> Sequence[ChartRow]:
        return self.rows(ChartRow.manufacturer == manufacturer)


class SystemRepository(Repository[SystemRow]):
    model = SystemRow
    noun = "keying system"
    order = (SystemRow.name,)

    def by_name(self, name: str) -> SystemRow | None:
        return self.one(SystemRow.name == name)

    def live(self) -> Sequence[SystemRow]:
        return self.rows(SystemRow.archived.is_(False))

    def entries(self, system_id: int) -> Sequence[ScheduleEntryRow]:
        return self.rows_of(ScheduleEntryRow, ScheduleEntryRow.system_id == system_id)

    def groups(self, system_id: int) -> Sequence[GroupRow]:
        return self.rows_of(GroupRow, GroupRow.system_id == system_id, order=(GroupRow.name,))

    def uncut_symbols(self, system_id: int) -> Sequence[ScheduleEntryRow]:
        return self.rows_of(
            ScheduleEntryRow,
            ScheduleEntryRow.system_id == system_id,
            ScheduleEntryRow.bitting.is_(None),
        )


class OpeningRepository(Repository[OpeningRow]):
    model = OpeningRow
    noun = "opening"
    order = (OpeningRow.number,)

    def in_building(self, code: str) -> Sequence[OpeningRow]:
        return self.rows(OpeningRow.building_code == code)

    def exterior(self) -> Sequence[OpeningRow]:
        return self.rows(OpeningRow.exterior.is_(True))

    def buildings(self) -> Sequence[BuildingRow]:
        return self.rows_of(BuildingRow, order=(BuildingRow.code,))


class CylinderRepository(Repository[CylinderRow]):
    model = CylinderRow
    noun = "cylinder"
    order = (CylinderRow.serial,)

    def on_opening(self, number: str) -> Sequence[CylinderRow]:
        return self.rows(CylinderRow.installed_at == number)

    def on_the_shelf(self) -> Sequence[CylinderRow]:
        return self.rows(CylinderRow.installed_at.is_(None))

    def in_group(self, group_id: int) -> Sequence[CylinderRow]:
        return self.rows(CylinderRow.group_id == group_id)

    def unkeyed(self) -> Sequence[CylinderRow]:
        """Installed, and nobody wrote down what it answers to."""
        return self.rows(CylinderRow.installed_at.is_not(None), CylinderRow.group_id.is_(None))


class KeyholderRepository(Repository[KeyholderRow]):
    model = KeyholderRow
    noun = "keyholder"
    order = (KeyholderRow.identifier,)

    def in_department(self, department: str) -> Sequence[KeyholderRow]:
        return self.rows(KeyholderRow.department == department)

    def lapsed(self, today: date) -> Sequence[KeyholderRow]:
        return self.rows(
            KeyholderRow.standing == "active",
            KeyholderRow.ends_on < today,
            order=KeyholderRow.ends_on,
        )


class KeyRepository(Repository[IssuedKeyRow]):
    model = IssuedKeyRow
    noun = "key"
    order = (IssuedKeyRow.tag,)

    def out(self) -> Sequence[IssuedKeyRow]:
        return self.rows(IssuedKeyRow.state == "out")

    def held_by(self, identifier: str) -> Sequence[IssuedKeyRow]:
        return self.rows(IssuedKeyRow.keyholder == identifier, IssuedKeyRow.state == "out")

    def overdue(self, today: date) -> Sequence[IssuedKeyRow]:
        return self.rows(
            IssuedKeyRow.state == "out", IssuedKeyRow.due_on < today, order=IssuedKeyRow.due_on
        )

    def cut_to(self, symbol: str) -> Sequence[IssuedKeyRow]:
        return self.rows(IssuedKeyRow.symbol == symbol, IssuedKeyRow.state == "out")

    def deposits_held(self) -> int:
        statement = select(func.coalesce(func.sum(IssuedKeyRow.deposit_cents), 0)).where(
            IssuedKeyRow.state == "out"
        )
        return int(self.session.execute(statement).scalar_one())


class RequestRepository(Repository[KeyRequestRow]):
    model = KeyRequestRow
    noun = "request"
    order = (KeyRequestRow.number,)

    def in_state(self, state: str) -> Sequence[KeyRequestRow]:
        return self.rows(KeyRequestRow.state == state)

    def for_keyholder(self, identifier: str) -> Sequence[KeyRequestRow]:
        return self.rows(KeyRequestRow.keyholder == identifier, order=KeyRequestRow.raised_on)


class IncidentRepository(Repository[IncidentRow]):
    model = IncidentRow
    noun = "incident"
    order = (IncidentRow.number,)

    def since(self, when: date) -> Sequence[IncidentRow]:
        return self.rows(IncidentRow.reported_on >= when, order=IncidentRow.reported_on)

    def by_severity(self, severity: str) -> Sequence[IncidentRow]:
        return self.rows(IncidentRow.severity == severity)


class WorkOrderRepository(Repository[WorkOrderRow]):
    model = WorkOrderRow
    noun = "work order"
    order = (WorkOrderRow.number,)

    def open_orders(self) -> Sequence[WorkOrderRow]:
        return self.rows(WorkOrderRow.state.in_(("drafted", "scheduled", "started")))

    def for_technician(self, technician: str) -> Sequence[WorkOrderRow]:
        return self.rows(WorkOrderRow.technician == technician)

    def due_on(self, when: date) -> Sequence[WorkOrderRow]:
        return self.rows(WorkOrderRow.scheduled_for == when)


class QuarantineRepository(Repository[RetiredBittingRow]):
    model = RetiredBittingRow
    noun = "retired cut"
    order = (RetiredBittingRow.id,)

    def by_bitting(self, rendered: str) -> RetiredBittingRow | None:
        return self.one(RetiredBittingRow.bitting == rendered)

    def permanent(self) -> Sequence[RetiredBittingRow]:
        return self.rows(RetiredBittingRow.reason == "compromised")

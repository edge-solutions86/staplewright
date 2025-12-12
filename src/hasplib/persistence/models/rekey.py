"""Incidents, the work they cause, and the cuts that never come back.

The retired bittings table is the one that outlives everything else. A system
gets replaced, a building comes down, the cylinders go in a skip, and the
cuts that were compromised while all that was happening are still not
available, because the keys are still out there in somebody's drawer.

So it has no foreign key to the system. It is a list of cut sequences with a
reason and a date, deliberately standing on its own, and the only thing that
ever removes a row is a person deciding a superseded cut has sat out long
enough.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.keys.bitting import Bitting
from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.types import BittingColumn


class IncidentRow(Base):
    """One missing key, written down the day it is reported."""

    __tablename__ = "incidents"
    __table_args__ = (
        CheckConstraint("cause <> 'stolen' or length(note) > 0", name="theft_is_written_up"),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    tag: Mapped[Code]
    symbol: Mapped[Code]
    cause: Mapped[Code]
    reported_on: Mapped[date]
    keyholder: Mapped[Code] = mapped_column(default="")
    severity: Mapped[Code] = mapped_column(default="routine")
    note: Mapped[Note]

    orders: Mapped[list[WorkOrderRow]] = relationship(back_populates="incident")

    def __repr__(self) -> str:
        return f"<IncidentRow {self.number}>"


class WorkOrderRow(Base):
    """One trip out with a bag of pins."""

    __tablename__ = "work_orders"
    __table_args__ = (
        CheckConstraint(
            "state <> 'scheduled' or scheduled_for is not null", name="scheduled_has_a_day"
        ),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    incident_number: Mapped[str | None] = mapped_column(
        ForeignKey("incidents.number", ondelete="SET NULL"), default=None
    )
    state: Mapped[Code] = mapped_column(default="drafted")
    technician: Mapped[Name] = mapped_column(default="")
    scheduled_for: Mapped[date | None] = mapped_column(default=None)
    closed_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    incident: Mapped[IncidentRow | None] = relationship(back_populates="orders")
    lines: Mapped[list[WorkOrderLineRow]] = relationship(
        back_populates="order", cascade="all, delete-orphan"
    )

    @property
    def pending(self) -> tuple[WorkOrderLineRow, ...]:
        return tuple(line for line in self.lines if line.state not in {"installed", "skipped"})

    def __repr__(self) -> str:
        return f"<WorkOrderRow {self.number}>"


class WorkOrderLineRow(Base):
    """One cylinder on one work order."""

    __tablename__ = "work_order_lines"
    __table_args__ = (
        UniqueConstraint("order_number", "serial"),
        Index("ix_work_order_lines_opening", "opening_number"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    order_number: Mapped[str] = mapped_column(ForeignKey("work_orders.number", ondelete="CASCADE"))
    serial: Mapped[Code]
    opening_number: Mapped[Code]
    state: Mapped[Code] = mapped_column(default="pending")
    note: Mapped[Note]

    order: Mapped[WorkOrderRow] = relationship(back_populates="lines")

    def __repr__(self) -> str:
        return f"<WorkOrderLineRow {self.order_number} {self.serial}>"


class RetiredBittingRow(Base):
    """A cut that is out of service, standing on its own on purpose."""

    __tablename__ = "retired_bittings"
    __table_args__ = (UniqueConstraint("bitting"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    bitting: Mapped[Bitting] = mapped_column(BittingColumn())
    reason: Mapped[Code]
    retired_on: Mapped[date]
    symbol: Mapped[Code] = mapped_column(default="")
    note: Mapped[Note]

    @property
    def is_permanent(self) -> bool:
        return self.reason == "compromised"

    def __repr__(self) -> str:
        return f"<RetiredBittingRow {self.bitting}>"

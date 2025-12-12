"""Systems, schedules and keying groups, which is the heart of the schema.

A campus runs several keying systems at once. The 1971 dormitories are on
cores and the 1953 academic buildings are on mortise cylinders, and the two
share nothing but the shop that maintains them, so the system is the boundary
everything else sits inside.

Two uniqueness rules carry most of the weight. A symbol appears once in a
system, which is what makes AB3 mean something. And a bitting appears once in
a system, which is what stops two rooms quietly sharing a key. The second one
is written as a plain unique constraint across the system and the cut, and it
lets a schedule hold any number of symbols that have not been cut yet,
because a null is not equal to another null in either database the shop runs.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.models.reference import ChartRow
from hasplib.persistence.types import BittingColumn, SymbolColumn


class SystemRow(Base):
    """One keying system, which is the boundary for symbols and cuts."""

    __tablename__ = "keying_systems"
    __table_args__ = (
        UniqueConstraint("name"),
        CheckConstraint("progression_step > 0", name="step_positive"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[Name]
    chart_id: Mapped[int] = mapped_column(ForeignKey("charts.id", ondelete="RESTRICT"))
    family_name: Mapped[Name]
    top_section: Mapped[Code]
    progression_step: Mapped[int] = mapped_column(default=2)
    archived: Mapped[bool] = mapped_column(default=False)
    opened_on: Mapped[date | None] = mapped_column(default=None)
    notes: Mapped[Note]

    chart: Mapped[ChartRow] = relationship()
    entries: Mapped[list[ScheduleEntryRow]] = relationship(
        back_populates="system", cascade="all, delete-orphan"
    )
    groups: Mapped[list[GroupRow]] = relationship(
        back_populates="system", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<SystemRow {self.name}>"


class ScheduleEntryRow(Base):
    """One line of a keying schedule."""

    __tablename__ = "schedule_entries"
    __table_args__ = (
        UniqueConstraint("system_id", "symbol"),
        UniqueConstraint("system_id", "bitting"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    system_id: Mapped[int] = mapped_column(ForeignKey("keying_systems.id", ondelete="CASCADE"))
    symbol: Mapped[KeySymbol] = mapped_column(SymbolColumn())
    section: Mapped[Code]
    bitting: Mapped[Bitting | None] = mapped_column(BittingColumn(), default=None)
    note: Mapped[Note]

    system: Mapped[SystemRow] = relationship(back_populates="entries")

    @property
    def is_cut(self) -> bool:
        return self.bitting is not None

    def __repr__(self) -> str:
        return f"<ScheduleEntryRow {self.symbol} {self.bitting or 'open'}>"


class GroupRow(Base):
    """A set of symbols one cylinder answers to."""

    __tablename__ = "keying_groups"
    __table_args__ = (
        UniqueConstraint("system_id", "name"),
        Index("ix_keying_groups_name", "name"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    system_id: Mapped[int] = mapped_column(ForeignKey("keying_systems.id", ondelete="CASCADE"))
    name: Mapped[Name]
    note: Mapped[Note]

    system: Mapped[SystemRow] = relationship(back_populates="groups")
    members: Mapped[list[GroupMemberRow]] = relationship(
        back_populates="group", cascade="all, delete-orphan"
    )

    @property
    def symbols(self) -> tuple[KeySymbol, ...]:
        return tuple(member.symbol for member in self.members)

    def __repr__(self) -> str:
        return f"<GroupRow {self.name}>"


class GroupMemberRow(Base):
    """One symbol in one keying group."""

    __tablename__ = "keying_group_members"
    __table_args__ = (UniqueConstraint("group_id", "symbol"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("keying_groups.id", ondelete="CASCADE"))
    symbol: Mapped[KeySymbol] = mapped_column(SymbolColumn())

    group: Mapped[GroupRow] = relationship(back_populates="members")

    def __repr__(self) -> str:
        return f"<GroupMemberRow {self.symbol}>"

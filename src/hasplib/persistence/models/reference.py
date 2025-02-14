"""The tables nobody edits twice a year: charts, keyway sections, blanks.

These are the manufacturer facts. They change when the shop changes supplier
or when a factory withdraws a depth, which happens about as often as a new
building goes up, so they are ordinary tables with ordinary constraints and
no history behind them.

The keyway sections are the one interesting shape. A section has a parent
section in the same table, which is how the multiplex family nests, and the
root of a family is the row whose parent is null. A check constraint keeps a
section from being its own parent, which is the only cycle a single row can
make on its own.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, ForeignKey, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.types import DepthListColumn


class ChartRow(Base):
    """One manufacturer depth and space chart."""

    __tablename__ = "charts"
    __table_args__ = (
        UniqueConstraint("manufacturer", "family"),
        CheckConstraint("chambers > 0", name="chambers_positive"),
        CheckConstraint("increment_mils > 0", name="increment_positive"),
        CheckConstraint("macs > 0", name="macs_positive"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    manufacturer: Mapped[Name]
    family: Mapped[Name]
    chambers: Mapped[int]
    depths: Mapped[tuple[int, ...]] = mapped_column(DepthListColumn())
    root_mils: Mapped[int]
    increment_mils: Mapped[int]
    first_space_mils: Mapped[int]
    space_increment_mils: Mapped[int]
    macs: Mapped[int]
    gauged_from_tip: Mapped[bool] = mapped_column(Boolean, default=False)
    depth_tolerance_mils: Mapped[int] = mapped_column(default=2)
    notes: Mapped[Note]

    def __repr__(self) -> str:
        return f"<ChartRow {self.manufacturer} {self.family}>"


class KeywaySectionRow(Base):
    """One warding section, hanging under the section above it."""

    __tablename__ = "keyway_sections"
    __table_args__ = (
        UniqueConstraint("family_name", "code"),
        CheckConstraint("parent_id is null or parent_id <> id", name="not_its_own_parent"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    family_name: Mapped[Name]
    code: Mapped[Code]
    manufacturer: Mapped[Name]
    restricted: Mapped[bool] = mapped_column(Boolean, default=False)
    patent_expires: Mapped[date | None] = mapped_column(default=None)
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("keyway_sections.id", ondelete="RESTRICT"), default=None
    )

    parent: Mapped[KeywaySectionRow | None] = relationship(
        remote_side="KeywaySectionRow.id", back_populates="children"
    )
    children: Mapped[list[KeywaySectionRow]] = relationship(back_populates="parent")
    blanks: Mapped[list[BlankRow]] = relationship(back_populates="section")

    @property
    def is_root(self) -> bool:
        return self.parent_id is None

    def __repr__(self) -> str:
        return f"<KeywaySectionRow {self.family_name} {self.code}>"


class BlankRow(Base):
    """One part number in the blank drawer."""

    __tablename__ = "key_blanks"
    __table_args__ = (
        CheckConstraint("chambers > 0", name="chambers_positive"),
        CheckConstraint("stock >= 0", name="stock_not_negative"),
        CheckConstraint("reorder_at >= 0", name="reorder_not_negative"),
    )

    part_number: Mapped[Code] = mapped_column(primary_key=True)
    section_id: Mapped[int] = mapped_column(ForeignKey("keyway_sections.id", ondelete="RESTRICT"))
    manufacturer: Mapped[Name]
    chambers: Mapped[int]
    stock: Mapped[int] = mapped_column(default=0)
    restricted: Mapped[bool] = mapped_column(Boolean, default=False)
    supplier: Mapped[Name] = mapped_column(default="")
    reorder_at: Mapped[int] = mapped_column(default=0)

    section: Mapped[KeywaySectionRow] = relationship(back_populates="blanks")

    def __repr__(self) -> str:
        return f"<BlankRow {self.part_number}>"

"""Buildings, openings, and the cylinders sitting in them today.

The one thing worth arguing about in this file is where the installation
lives. A cylinder points at an opening rather than an opening holding a list
of cylinders, because a cylinder is the thing that moves and an opening is
the thing that stays. That also makes the rule enforceable in the database: a
cylinder has at most one opening because it has one column.

The other rule, that two cylinders cannot sit in the same position on one
door, is a unique constraint across the opening and the position. Both
columns are null while the cylinder is on the shelf, and a null is not equal
to another null, so any number of cylinders can sit in the drawer at once.
"""

from __future__ import annotations

from sqlalchemy import Boolean, CheckConstraint, ForeignKey, Index, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note


class BuildingRow(Base):
    """One building on the campus."""

    __tablename__ = "buildings"
    __table_args__ = (
        CheckConstraint("year_built is null or year_built > 1850", name="year_plausible"),
    )

    code: Mapped[Code] = mapped_column(primary_key=True)
    name: Mapped[Name]
    year_built: Mapped[int | None] = mapped_column(default=None)

    openings: Mapped[list[OpeningRow]] = relationship(back_populates="building")

    def __repr__(self) -> str:
        return f"<BuildingRow {self.code}>"


class OpeningRow(Base):
    """One door, hatch, gate or cabinet."""

    __tablename__ = "openings"
    __table_args__ = (
        UniqueConstraint("building_code", "room", "floor"),
        Index("ix_openings_building", "building_code"),
        Index("ix_openings_function", "function"),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    building_code: Mapped[str] = mapped_column(ForeignKey("buildings.code", ondelete="RESTRICT"))
    floor: Mapped[Code]
    room: Mapped[Code]
    function: Mapped[Code]
    exterior: Mapped[bool] = mapped_column(Boolean, default=False)
    description: Mapped[Note]

    building: Mapped[BuildingRow] = relationship(back_populates="openings")
    cylinders: Mapped[list[CylinderRow]] = relationship(back_populates="opening")

    def __repr__(self) -> str:
        return f"<OpeningRow {self.number}>"


class CylinderRow(Base):
    """One serial numbered cylinder or core, wherever it is."""

    __tablename__ = "cylinders"
    __table_args__ = (
        UniqueConstraint("installed_at", "position"),
        CheckConstraint("chambers > 0", name="chambers_positive"),
        CheckConstraint(
            "(installed_at is null) = (position is null)", name="installed_has_a_position"
        ),
        # The coverage map is built from these two joins on every report.
        Index("ix_cylinders_installed_at", "installed_at"),
        Index("ix_cylinders_group_id", "group_id"),
    )

    serial: Mapped[Code] = mapped_column(primary_key=True)
    format: Mapped[Code]
    section: Mapped[Code]
    chambers: Mapped[int]
    manufacturer: Mapped[Name] = mapped_column(default="")
    spooled: Mapped[bool] = mapped_column(Boolean, default=False)
    installed_at: Mapped[str | None] = mapped_column(
        ForeignKey("openings.number", ondelete="RESTRICT"), default=None
    )
    position: Mapped[Code | None] = mapped_column(default=None)
    group_id: Mapped[int | None] = mapped_column(
        ForeignKey("keying_groups.id", ondelete="SET NULL"), default=None
    )
    note: Mapped[Note]

    opening: Mapped[OpeningRow | None] = relationship(back_populates="cylinders")

    @property
    def is_installed(self) -> bool:
        return self.installed_at is not None

    def __repr__(self) -> str:
        return f"<CylinderRow {self.serial}>"


class AccessGroupRow(Base):
    """A set of people who need the same doors."""

    __tablename__ = "access_groups"
    __table_args__ = (UniqueConstraint("name"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[Name]
    department: Mapped[Name] = mapped_column(default="")
    description: Mapped[Note]

    requirements: Mapped[list[AccessRequirementRow]] = relationship(
        back_populates="group", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<AccessGroupRow {self.name}>"


class AccessRequirementRow(Base):
    """One group needing one door, which is a cell of the matrix."""

    __tablename__ = "access_requirements"
    __table_args__ = (UniqueConstraint("group_id", "opening_number"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("access_groups.id", ondelete="CASCADE"))
    opening_number: Mapped[str] = mapped_column(ForeignKey("openings.number", ondelete="CASCADE"))

    group: Mapped[AccessGroupRow] = relationship(back_populates="requirements")

    def __repr__(self) -> str:
        return f"<AccessRequirementRow {self.opening_number}>"

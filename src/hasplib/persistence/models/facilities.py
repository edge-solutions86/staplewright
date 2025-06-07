"""Lockout calls and hardware surveys, which both hang off an opening.

Neither table points at a cylinder. A lockout is about a door and a person,
and a survey line is about a door and a day, and in both cases the cylinder
that happened to be in the door at the time is not what anybody looks up
afterwards. Tying them to the cylinder would also mean the record moved when
the cylinder did, which is exactly wrong.

The survey line carries its deficiencies as a comma separated list rather
than a join table. They are read whole, never queried into, and there are at
most six of them, so a table with two columns and a foreign key would be
three joins to answer a question a substring already answers.
"""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import CheckConstraint, ForeignKey, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note


class LockoutRow(Base):
    """One call, from the telephone ringing to the door opening."""

    __tablename__ = "lockouts"
    __table_args__ = (
        CheckConstraint(
            "attended_at is null or attended_at >= called_at", name="attended_after_called"
        ),
        CheckConstraint(
            "state <> 'attended' or technician <> ''", name="attended_has_a_technician"
        ),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    opening_number: Mapped[str] = mapped_column(ForeignKey("openings.number", ondelete="RESTRICT"))
    keyholder: Mapped[Code]
    called_at: Mapped[datetime]
    state: Mapped[Code] = mapped_column(default="logged")
    attended_at: Mapped[datetime | None] = mapped_column(default=None)
    closed_at: Mapped[datetime | None] = mapped_column(default=None)
    technician: Mapped[Name] = mapped_column(default="")
    identified_by: Mapped[Note] = mapped_column(default="not identified")
    note: Mapped[Note]

    @property
    def is_open(self) -> bool:
        return self.state in {"logged", "attended"}

    def __repr__(self) -> str:
        return f"<LockoutRow {self.number}>"


class SurveyRow(Base):
    """One walk round a building."""

    __tablename__ = "surveys"
    __table_args__ = (UniqueConstraint("building_code", "walked_on"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    building_code: Mapped[str] = mapped_column(ForeignKey("buildings.code", ondelete="RESTRICT"))
    walked_on: Mapped[date]
    note: Mapped[Note]

    lines: Mapped[list[SurveyLineRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<SurveyRow {self.building_code} {self.walked_on}>"


class SurveyLineRow(Base):
    """One opening as it was found on the day."""

    __tablename__ = "survey_lines"
    __table_args__ = (
        UniqueConstraint("survey_id", "opening_number"),
        CheckConstraint("condition between 1 and 4", name="condition_in_range"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    survey_id: Mapped[int] = mapped_column(ForeignKey("surveys.id", ondelete="CASCADE"))
    opening_number: Mapped[str] = mapped_column(ForeignKey("openings.number", ondelete="RESTRICT"))
    surveyor: Mapped[Name]
    condition: Mapped[int]
    manufacturer: Mapped[Name] = mapped_column(default="")
    cylinder_serial: Mapped[Code] = mapped_column(default="")
    deficiencies: Mapped[Note] = mapped_column(default="")
    note: Mapped[Note]

    survey: Mapped[SurveyRow] = relationship(back_populates="lines")

    @property
    def deficiency_list(self) -> tuple[str, ...]:
        if not self.deficiencies:
            return ()
        return tuple(part for part in self.deficiencies.split(",") if part)

    def __repr__(self) -> str:
        return f"<SurveyLineRow {self.opening_number}>"

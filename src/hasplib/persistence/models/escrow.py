"""The sealed boxes and every seal that was ever on one of them.

Two tables rather than one, and the second is the point. A box row says where
the box stands now, which is what a walk round needs. The seal history says
which key was behind which seal number between which two days, which is what
somebody needs eighteen months later when a key turns up in a car park and
the only marking on it is a symbol.

The current seal is on the box row as well as at the end of the history. That
is a duplicate on purpose: the walk round reads one row per box and a walk
round that has to open a history per box is a walk round nobody finishes.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.types import SymbolColumn


class KeyBoxRow(Base):
    """One sealed box, as it stands today."""

    __tablename__ = "key_boxes"
    __table_args__ = (
        UniqueConstraint("opening_number"),
        CheckConstraint(
            "state in ('sealed', 'opened', 'empty', 'withdrawn')", name="box_state_is_known"
        ),
        CheckConstraint("state <> 'sealed' or seal <> ''", name="a_sealed_box_has_a_seal"),
        CheckConstraint("state <> 'opened' or opened_on is not null", name="an_open_box_says_when"),
        CheckConstraint(
            "checked_on is null or checked_on >= sealed_on", name="checked_after_sealed"
        ),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    building_code: Mapped[str] = mapped_column(ForeignKey("buildings.code", ondelete="RESTRICT"))
    opening_number: Mapped[str] = mapped_column(ForeignKey("openings.number", ondelete="RESTRICT"))
    symbol: Mapped[KeySymbol] = mapped_column(SymbolColumn())
    seal: Mapped[Code]
    sealed_on: Mapped[date]
    state: Mapped[Code] = mapped_column(default="sealed")
    checked_on: Mapped[date | None] = mapped_column(default=None)
    opened_on: Mapped[date | None] = mapped_column(default=None)
    opened_by: Mapped[Name] = mapped_column(default="")
    note: Mapped[Note]

    seals: Mapped[list[SealRow]] = relationship(back_populates="box", cascade="all, delete-orphan")

    @property
    def is_live(self) -> bool:
        return self.state != "withdrawn"

    def __repr__(self) -> str:
        return f"<KeyBoxRow {self.number} {self.state}>"


class SealRow(Base):
    """One seal on one box, from the day it went on to the day it came off."""

    __tablename__ = "key_box_seals"
    __table_args__ = (
        UniqueConstraint("box_number", "seal", "sealed_on"),
        CheckConstraint("broken_on is null or broken_on >= sealed_on", name="broken_after_sealed"),
        Index("ix_key_box_seals_box_day", "box_number", "sealed_on"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    box_number: Mapped[str] = mapped_column(ForeignKey("key_boxes.number", ondelete="CASCADE"))
    seal: Mapped[Code]
    symbol: Mapped[KeySymbol] = mapped_column(SymbolColumn())
    sealed_on: Mapped[date]
    broken_on: Mapped[date | None] = mapped_column(default=None)
    broken_by: Mapped[Name] = mapped_column(default="")
    note: Mapped[Note]

    box: Mapped[KeyBoxRow] = relationship(back_populates="seals")

    @property
    def is_current(self) -> bool:
        return self.broken_on is None

    def days_held(self, today: date) -> int:
        """How long this seal was on, or has been on so far."""
        return ((self.broken_on or today) - self.sealed_on).days

    def __repr__(self) -> str:
        return f"<SealRow {self.box_number} {self.seal}>"

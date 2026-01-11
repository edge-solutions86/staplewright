"""Control keys and cores in the database, kept away from the key ledger.

A control key is not an issued key and does not go in ``issued_keys``. It has
no symbol, it is not on any keying system, and a report that counted it among
the masters would be counting a key that opens no doors at all and takes
every lock off the wall instead.

So it has its own two tables and its own register, and the only thing that
joins them to the rest of the schema is the opening a core happens to be
sitting in. That join is nullable, because a core on a shelf is still a core
somebody could put in a housing tomorrow.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.keys.bitting import Bitting
from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.types import BittingColumn


class ControlKeyRow(Base):
    """One control key, which pulls cores rather than opening doors."""

    __tablename__ = "control_keys"
    __table_args__ = (
        CheckConstraint("section <> ''", name="a_control_key_has_a_section"),
        CheckConstraint(
            "withdrawn_on is null or withdrawn_on >= issued_on", name="withdrawn_after_issued"
        ),
        Index("ix_control_keys_holder", "holder"),
    )

    code: Mapped[Code] = mapped_column(primary_key=True)
    section: Mapped[Code]
    format: Mapped[Code]
    bitting: Mapped[Bitting] = mapped_column(BittingColumn())
    issued_on: Mapped[date]
    holder: Mapped[Name] = mapped_column(default="")
    withdrawn_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    cores: Mapped[list[CoreRow]] = relationship(back_populates="control")

    @property
    def is_live(self) -> bool:
        return self.withdrawn_on is None

    def __repr__(self) -> str:
        return f"<ControlKeyRow {self.code}>"


class CoreRow(Base):
    """One interchangeable core, and the control key that pulls it."""

    __tablename__ = "interchangeable_cores"
    __table_args__ = (
        UniqueConstraint("installed_at", "housing"),
        Index("ix_interchangeable_cores_control", "control_code"),
    )

    serial: Mapped[Code] = mapped_column(primary_key=True)
    format: Mapped[Code]
    control_code: Mapped[str] = mapped_column(ForeignKey("control_keys.code", ondelete="RESTRICT"))
    housing: Mapped[Code]
    installed_at: Mapped[str | None] = mapped_column(
        ForeignKey("openings.number", ondelete="RESTRICT"), default=None
    )
    note: Mapped[Note]

    control: Mapped[ControlKeyRow] = relationship(back_populates="cores")

    @property
    def is_installed(self) -> bool:
        return self.installed_at is not None

    def __repr__(self) -> str:
        return f"<CoreRow {self.serial}>"

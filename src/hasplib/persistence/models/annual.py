"""Filed returns, which is the only historical record this library keeps.

Everything else here answers questions about today. The ledger says what is
out now, the register says what is installed now, and a question about last
March gets a shrug. That is deliberate: a system that reconstructs the past
from the present is a system that will confidently tell you something wrong.

A return is the exception. It is four numbers, measured on a day, written
down and never touched again, and that is what makes a trend possible at all.
So the row is immutable by convention and by constraint: filing over an
existing year is refused, because the interesting case is always somebody
running the numbers again after a clean up and quietly improving last year.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from hasplib.persistence.base import Base, Name, Note


class AnnualReturnRow(Base):
    """One year of key control numbers, as they stood on the day filed."""

    __tablename__ = "annual_returns"
    __table_args__ = (
        UniqueConstraint("year"),
        CheckConstraint("year >= 1900", name="a_real_year"),
        CheckConstraint("keys_out >= 0", name="keys_out_not_negative"),
        CheckConstraint("masters_out >= 0", name="masters_out_not_negative"),
        CheckConstraint("unaccounted >= 0", name="unaccounted_not_negative"),
        CheckConstraint("keyholders >= 0", name="keyholders_not_negative"),
        CheckConstraint("masters_out <= keys_out", name="masters_are_keys_too"),
        CheckConstraint("unaccounted <= keys_out", name="unaccounted_are_keys_too"),
    )

    year: Mapped[int] = mapped_column(primary_key=True)
    measured_on: Mapped[date]
    keys_out: Mapped[int]
    masters_out: Mapped[int]
    unaccounted: Mapped[int]
    keyholders: Mapped[int]
    filed_by: Mapped[Name] = mapped_column(default="")
    note: Mapped[Note]

    @property
    def keys_per_holder(self) -> float:
        if not self.keyholders:
            return 0.0
        return self.keys_out / self.keyholders

    def __repr__(self) -> str:
        return f"<AnnualReturnRow {self.year}>"

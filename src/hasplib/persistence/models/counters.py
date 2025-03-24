"""The counters behind every number the shop hands out.

One row per series. Key tags, work order numbers, incident numbers and
request numbers all come out of here, and they come out gapless because the
row is locked inside the same transaction that writes whatever uses the
number. An auto increment column would be faster and would leave holes in the
sequence every time a transaction rolls back, and a hole in a key tag series
is an afternoon of somebody looking for a key that never existed.
"""

from __future__ import annotations

from sqlalchemy import CheckConstraint
from sqlalchemy.orm import Mapped, mapped_column

from hasplib.persistence.base import Base, Code


class CounterRow(Base):
    """One series and the next number in it."""

    __tablename__ = "counters"
    __table_args__ = (CheckConstraint("next_value > 0", name="next_value_positive"),)

    name: Mapped[Code] = mapped_column(primary_key=True)
    prefix: Mapped[Code] = mapped_column(default="")
    width: Mapped[int] = mapped_column(default=4)
    next_value: Mapped[int] = mapped_column(default=1)

    def render(self, value: int) -> str:
        return f"{self.prefix}{value:0{self.width}d}"

    def __repr__(self) -> str:
        return f"<CounterRow {self.name} at {self.next_value}>"

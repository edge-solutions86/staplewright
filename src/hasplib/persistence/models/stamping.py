"""The blind code table, which is the one table worth locking the room over.

Everything else in this schema is inconvenient to lose. This one is dangerous
to leak: it turns every number stamped on every master key on the campus back
into a symbol, and the symbols are what say which doors a key opens.

So it is a table of two columns and nothing else. No note field, because a
note is where somebody eventually writes what the key opens. No timestamps
that could be correlated with an issue register. Two columns, a unique index
each way, and an audit trail that lives somewhere else.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from hasplib.persistence.base import Base, Code, Name


class BlindCodeRow(Base):
    """One symbol and the number that stands for it on a key bow."""

    __tablename__ = "blind_codes"
    __table_args__ = (
        UniqueConstraint("code"),
        CheckConstraint("code <> ''", name="a_code_is_something"),
        CheckConstraint("symbol <> ''", name="a_code_stands_for_something"),
    )

    symbol: Mapped[Code] = mapped_column(primary_key=True)
    code: Mapped[Code]
    assigned_on: Mapped[date]
    assigned_by: Mapped[Name] = mapped_column(default="")

    def __repr__(self) -> str:
        return f"<BlindCodeRow {self.code}>"

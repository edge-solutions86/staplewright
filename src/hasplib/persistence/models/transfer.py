"""The handover table, which is deliberately not part of the key row.

The obvious design is a column on the issued key saying who has it now, moved
whenever a handover confirms. That is what the shop did in the spreadsheet and
it is exactly what lost the history: the column said rlaakso and nobody could
say when it stopped saying kmaki or who signed for the change.

So a handover is its own row and the key row is never touched by one. Working
out who has a key means reading the key row and then walking the confirmed
handovers on top of it, which is slower and is the only version that can be
audited.

The unique index is the load bearing part. One key can have exactly one claim
outstanding, because two people cannot each be about to receive it, and a
partial index is the only way to say that in a table.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Note
from hasplib.persistence.models.control import IssuedKeyRow


class TransferRow(Base):
    """One key moving from one pair of hands to another."""

    __tablename__ = "key_transfers"
    __table_args__ = (
        CheckConstraint(
            "state in ('claimed', 'confirmed', 'refused', 'lapsed', 'cancelled')",
            name="transfer_state_is_known",
        ),
        CheckConstraint("passed_by <> passed_to", name="not_to_the_same_person"),
        CheckConstraint(
            "state = 'claimed' or settled_on is not null", name="a_settled_transfer_has_a_day"
        ),
        CheckConstraint(
            "settled_on is null or settled_on >= claimed_on", name="settled_after_claimed"
        ),
        CheckConstraint("state <> 'refused' or note <> ''", name="a_refusal_says_why"),
        Index(
            "ux_key_transfers_one_open_claim",
            "tag",
            unique=True,
            sqlite_where=text("state = 'claimed'"),
            postgresql_where=text("state = 'claimed'"),
        ),
        Index("ix_key_transfers_passed_to", "passed_to", "state"),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    tag: Mapped[str] = mapped_column(ForeignKey("issued_keys.tag", ondelete="RESTRICT"))
    symbol: Mapped[Code]
    passed_by: Mapped[str] = mapped_column(ForeignKey("keyholders.identifier", ondelete="RESTRICT"))
    passed_to: Mapped[str] = mapped_column(ForeignKey("keyholders.identifier", ondelete="RESTRICT"))
    claimed_on: Mapped[date]
    state: Mapped[Code] = mapped_column(default="claimed")
    settled_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    key: Mapped[IssuedKeyRow] = relationship()

    @property
    def is_open(self) -> bool:
        return self.state == "claimed"

    @property
    def moved(self) -> bool:
        return self.state == "confirmed"

    def __repr__(self) -> str:
        return f"<TransferRow {self.number} {self.state}>"

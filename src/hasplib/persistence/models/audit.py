"""The audit trail, which is the only record of who decided something.

Every other table in this schema holds the current state. The schedule says
what a symbol is cut to now, the ledger says who holds a key now, and none of
them say who changed it or when. That is fine until an auditor asks why a
grand master was issued to a contractor in March, and the answer has to be
better than the row that exists today.

So the trail is append only and it is written by the services rather than by
the database. A trigger would catch more, and it would also record the
rollback of a transaction nobody meant to run, which is noise. What is
recorded here is a decision somebody made.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import CheckConstraint, Index, Integer
from sqlalchemy.orm import Mapped, mapped_column

from hasplib.persistence.base import Base, Code, Name, Note


class AuditEntryRow(Base):
    """One thing somebody did, written down when they did it."""

    __tablename__ = "audit_entries"
    __table_args__ = (
        CheckConstraint("actor <> ''", name="actor_named"),
        CheckConstraint("action <> ''", name="action_named"),
        Index("ix_audit_entries_subject", "subject_kind", "subject_id"),
        Index("ix_audit_entries_happened_at", "happened_at"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    happened_at: Mapped[datetime]
    actor: Mapped[Name]
    action: Mapped[Code]
    subject_kind: Mapped[Code]
    subject_id: Mapped[Code]
    detail: Mapped[Note] = mapped_column(default="")

    @property
    def subject(self) -> str:
        return f"{self.subject_kind}/{self.subject_id}"

    def describe(self) -> str:
        when = self.happened_at.isoformat(sep=" ", timespec="minutes")
        return f"{when}  {self.actor:<12}{self.action:<16}{self.subject}"

    def __repr__(self) -> str:
        return f"<AuditEntryRow {self.action} {self.subject}>"

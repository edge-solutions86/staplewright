"""Writing the audit trail, and reading it back the two ways it gets read.

Two questions, and they want different indexes. What happened to this key, in
order, which is what somebody asks when a key turns up somewhere it should
not be. And what did this person do, in order, which is what somebody asks
after they have left.

Nothing in here deletes. A correction is another entry saying a correction
was made, because a trail with a hole in it is worth nothing at all.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.errors import InputError
from hasplib.persistence.models.audit import AuditEntryRow

KEY = "key"
SYMBOL = "symbol"
CYLINDER = "cylinder"
INCIDENT = "incident"
SYSTEM = "system"
REQUEST = "request"


class AuditTrail:
    """Append only, and read back by subject or by actor."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def record(
        self,
        actor: str,
        action: str,
        subject_kind: str,
        subject_id: str,
        when: datetime,
        detail: str = "",
    ) -> AuditEntryRow:
        """Write one decision down. Nothing here is optional but the detail."""
        if not actor.strip():
            raise InputError("an audit entry names who did it", action=action)
        if not action.strip():
            raise InputError("an audit entry names what was done", actor=actor)
        if not subject_id.strip():
            raise InputError("an audit entry names what it was done to", action=action)
        row = AuditEntryRow(
            happened_at=when,
            actor=actor,
            action=action,
            subject_kind=subject_kind,
            subject_id=subject_id,
            detail=detail,
        )
        self.session.add(row)
        self.session.flush()
        return row

    def about(self, subject_kind: str, subject_id: str) -> Sequence[AuditEntryRow]:
        """Everything that happened to one thing, oldest first."""
        statement = (
            select(AuditEntryRow)
            .where(
                AuditEntryRow.subject_kind == subject_kind,
                AuditEntryRow.subject_id == subject_id,
            )
            .order_by(AuditEntryRow.happened_at, AuditEntryRow.id)
        )
        return self.session.execute(statement).scalars().all()

    def by_actor(self, actor: str) -> Sequence[AuditEntryRow]:
        """Everything one person did, oldest first."""
        statement = (
            select(AuditEntryRow)
            .where(AuditEntryRow.actor == actor)
            .order_by(AuditEntryRow.happened_at, AuditEntryRow.id)
        )
        return self.session.execute(statement).scalars().all()

    def between(self, start: datetime, end: datetime) -> Sequence[AuditEntryRow]:
        if end < start:
            raise InputError("a window runs forward")
        statement = (
            select(AuditEntryRow)
            .where(AuditEntryRow.happened_at >= start, AuditEntryRow.happened_at <= end)
            .order_by(AuditEntryRow.happened_at, AuditEntryRow.id)
        )
        return self.session.execute(statement).scalars().all()

    def of_action(self, action: str) -> Sequence[AuditEntryRow]:
        statement = (
            select(AuditEntryRow)
            .where(AuditEntryRow.action == action)
            .order_by(AuditEntryRow.happened_at, AuditEntryRow.id)
        )
        return self.session.execute(statement).scalars().all()

    def actors(self) -> tuple[str, ...]:
        statement = select(AuditEntryRow.actor).distinct().order_by(AuditEntryRow.actor)
        return tuple(self.session.execute(statement).scalars().all())

    def count(self) -> int:
        return len(self.session.execute(select(AuditEntryRow)).scalars().all())

    def lines(self, rows: Sequence[AuditEntryRow]) -> tuple[str, ...]:
        """The trail as a person reads it."""
        return tuple(row.describe() for row in rows)

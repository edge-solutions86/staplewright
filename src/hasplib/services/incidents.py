"""Reporting a missing key and turning it into work.

Four things happen the morning a key is reported gone and they happen
together. The key is closed in the ledger, the incident is written down, the
cut is retired so nobody ever issues it again, and the assessment is graded
so the meeting at ten has a number in front of it.

Retiring the cut is the one that has to be inside the transaction. A rolled
back incident that left the cut quarantined would take a perfectly good
bitting out of the array for no recorded reason, and nobody would ever work
out why.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.campus.coverage import CoverageMap
from hasplib.control.issue import KeyLedger
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.loaders import load_ledger
from hasplib.persistence.models.control import IssuedKeyRow
from hasplib.persistence.models.rekey import (
    IncidentRow,
    RetiredBittingRow,
    WorkOrderLineRow,
    WorkOrderRow,
)
from hasplib.persistence.numbering import Numbering
from hasplib.persistence.repositories import IncidentRepository, WorkOrderRepository
from hasplib.persistence.workspace import Workspace
from hasplib.rekey.compromise import Assessment, Cause, Incident, assess
from hasplib.rekey.quarantine import RetirementReason
from hasplib.rekey.scope import DEFAULT_POLICY, ScopeDecision, ScopePolicy, decide

INCIDENT_SERIES = "incidents"
ORDER_SERIES = "work orders"


class IncidentService:
    """A missing key, from the phone call to the work order."""

    def __init__(self, session: Session, policy: ScopePolicy = DEFAULT_POLICY) -> None:
        self.session = session
        self.numbering = Numbering(session)
        self.incidents = IncidentRepository(session)
        self.orders = WorkOrderRepository(session)
        self.policy = policy
        self.space = Workspace(session)

    def coverage(self) -> CoverageMap:
        """The map from cylinders to symbols, built once per service.

        Assessing an incident asks for this three times, and rebuilding the
        whole campus each time made a lost grand master take four seconds to
        report on a campus of four hundred doors.
        """
        return self.space.coverage()

    def ledger(self) -> KeyLedger:
        """The ledger as it stands now, which is not something to hold on to.

        Reporting an incident closes a key, so a cached ledger read after one
        would still show it out. Only the coverage is held, because nothing
        this service does moves a cylinder.
        """
        return load_ledger(self.session)

    def report(
        self,
        tag: str,
        cause: Cause,
        when: date,
        note: str = "",
        retire_cut: bool = True,
    ) -> IncidentRow:
        """Write the incident down, close the key, and retire the cut."""
        key = self.session.get(IssuedKeyRow, tag)
        if key is None:
            raise NotFoundError("no such key in the ledger", tag=tag)
        if not key.is_out:
            raise ConflictError("that key is not out", tag=tag, state=key.state)
        if cause is Cause.STOLEN and not note.strip():
            raise ConflictError("a theft is written up", tag=tag)

        number = self.numbering.next(INCIDENT_SERIES)
        found = assess(
            Incident(number, tag, key.symbol, cause, when, key.keyholder, note),
            self.coverage(),
            self.ledger(),
        )
        row = IncidentRow(
            number=number,
            tag=tag,
            symbol=key.symbol,
            cause=cause.value,
            reported_on=when,
            keyholder=key.keyholder,
            severity=found.severity.label,
            note=note,
        )
        self.incidents.add(row)
        key.state = "stolen" if cause is Cause.STOLEN else "lost"
        key.closed_on = when
        if retire_cut:
            self._retire(key.symbol, when, number)
        self.session.flush()
        return row

    def assessment(self, number: str) -> Assessment:
        row = self.incidents.find(number)
        if row is None:
            raise NotFoundError("no such incident", number=number)
        incident = Incident(
            number=row.number,
            tag=row.tag,
            symbol=row.symbol,
            cause=Cause(row.cause),
            reported_on=row.reported_on,
            keyholder=row.keyholder,
            note=row.note,
        )
        return assess(incident, self.coverage(), self.ledger())

    def plan(self, number: str) -> ScopeDecision:
        return decide(self.assessment(number), self.coverage(), self.policy)

    def raise_order(self, number: str, when: date | None = None) -> WorkOrderRow:
        """Turn a scope decision into one work order with a line a cylinder."""
        decision = self.plan(number)
        if not decision.cylinders:
            raise ConflictError("that incident calls for no work", number=number)
        coverage = self.coverage()
        order_number = self.numbering.next(ORDER_SERIES)
        order = WorkOrderRow(number=order_number, incident_number=number)
        self.orders.add(order)
        self.session.flush()
        for serial in decision.cylinders:
            cylinder = coverage.register.cylinder(serial)
            self.session.add(
                WorkOrderLineRow(
                    order_number=order_number,
                    serial=serial,
                    opening_number=cylinder.installed_at or "",
                )
            )
        if when is not None:
            order.scheduled_for = when
        self.session.flush()
        return order

    def _retire(self, symbol: str, when: date, number: str) -> None:
        statement = select(RetiredBittingRow)
        held = {row.bitting.render() for row in self.session.execute(statement).scalars().all()}
        cut = self._cut_for(symbol)
        if cut is None or cut.render() in held:
            return
        self.session.add(
            RetiredBittingRow(
                bitting=cut,
                reason=RetirementReason.COMPROMISED.value,
                retired_on=when,
                symbol=symbol,
                note=f"retired by {number}",
            )
        )

    def _cut_for(self, symbol: str):
        from hasplib.persistence.models.keying import ScheduleEntryRow

        statement = select(ScheduleEntryRow).where(ScheduleEntryRow.symbol == symbol)
        rows = self.session.execute(statement).scalars().all()
        for row in rows:
            if row.bitting is not None:
                return row.bitting
        return None

    def open_series(self) -> None:
        self.numbering.open_series(INCIDENT_SERIES, prefix="I-", width=4, start=1)
        self.numbering.open_series(ORDER_SERIES, prefix="W-", width=4, start=1041)

    def symbols_recut(self, number: str) -> tuple[KeySymbol, ...]:
        return self.plan(number).symbols

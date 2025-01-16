"""Work orders, which is the rekey decision turned into a day of work.

An order is a header and a line per cylinder. The header carries who is doing
it and when. The lines carry what actually happened at each door, because a
rekey is never finished in one visit: two rooms are occupied, one cylinder
turns out to be a different format than the survey said, and one door has a
lock nobody has a key for at all.

So an order closes only when every line has been dealt with, and a line can
be dealt with by being done or by being skipped with a reason. What is not
allowed is closing an order with a line still pending, because that is how a
door stays on the old key for three years.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.rekey.scope import ScopeDecision
from hasplib.store import Registry


class OrderState(StrEnum):
    """Where the order is."""

    DRAFTED = "drafted"
    SCHEDULED = "scheduled"
    STARTED = "started"
    DONE = "done"
    CANCELLED = "cancelled"


class LineState(StrEnum):
    """Where one cylinder in the order is."""

    PENDING = "pending"
    PINNED = "pinned"
    INSTALLED = "installed"
    SKIPPED = "skipped"


SETTLED_LINES = frozenset({LineState.INSTALLED, LineState.SKIPPED})


@dataclass(frozen=True)
class OrderLine:
    """One cylinder on one work order."""

    serial: str
    opening: str
    state: LineState = LineState.PENDING
    note: str = ""

    @property
    def is_settled(self) -> bool:
        return self.state in SETTLED_LINES


@dataclass(frozen=True)
class WorkOrder:
    """One trip out with a bag of pins."""

    number: str
    incident: str
    lines: tuple[OrderLine, ...]
    state: OrderState = OrderState.DRAFTED
    technician: str = ""
    scheduled_for: date | None = None
    closed_on: date | None = None
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("a work order needs a number")
        if not self.lines:
            raise ConflictError("a work order with no cylinders is not work", number=self.number)
        serials = [line.serial for line in self.lines]
        if len(set(serials)) != len(serials):
            raise ConflictError("a cylinder is on the order twice", number=self.number)

    def line(self, serial: str) -> OrderLine:
        for line in self.lines:
            if line.serial == serial:
                return line
        raise ConflictError("that cylinder is not on this order", number=self.number, serial=serial)

    @property
    def pending(self) -> tuple[OrderLine, ...]:
        return tuple(line for line in self.lines if not line.is_settled)

    @property
    def is_open(self) -> bool:
        return self.state in {OrderState.DRAFTED, OrderState.SCHEDULED, OrderState.STARTED}

    @property
    def openings(self) -> tuple[str, ...]:
        return tuple(sorted({line.opening for line in self.lines}))

    def summary(self) -> str:
        return (
            f"{self.number} {self.state.value}: {len(self.lines)} cylinders, "
            f"{len(self.pending)} still pending"
        )


def lines_from(decision: ScopeDecision, openings: Sequence[str]) -> tuple[OrderLine, ...]:
    """Turn a scope decision into lines, one a cylinder."""
    if len(openings) != len(decision.cylinders):
        raise InputError(
            "every cylinder on the order needs the door it sits in",
            cylinders=len(decision.cylinders),
            openings=len(openings),
        )
    return tuple(
        OrderLine(serial, opening)
        for serial, opening in zip(decision.cylinders, openings, strict=True)
    )


class WorkOrderBook:
    """Every rekey order, open and closed."""

    def __init__(self) -> None:
        self._orders: Registry[WorkOrder] = Registry("work order", "number")

    def raise_order(self, order: WorkOrder) -> WorkOrder:
        if order.state is not OrderState.DRAFTED:
            raise ConflictError("an order starts drafted", number=order.number)
        return self._orders.add(order.number, order)

    def order(self, number: str) -> WorkOrder:
        return self._orders.get(number)

    def schedule(self, number: str, technician: str, when: date) -> WorkOrder:
        order = self._must_be(number, {OrderState.DRAFTED, OrderState.SCHEDULED})
        if not technician.strip():
            raise InputError("somebody has to do the work", number=number)
        updated = replace(
            order, state=OrderState.SCHEDULED, technician=technician, scheduled_for=when
        )
        return self._orders.put(number, updated)

    def start(self, number: str) -> WorkOrder:
        order = self._must_be(number, {OrderState.SCHEDULED})
        return self._orders.put(number, replace(order, state=OrderState.STARTED))

    def mark_line(self, number: str, serial: str, state: LineState, note: str = "") -> WorkOrder:
        """Move one cylinder along. A skip has to say why."""
        order = self._must_be(number, {OrderState.SCHEDULED, OrderState.STARTED})
        line = order.line(serial)
        if state is LineState.SKIPPED and not note.strip():
            raise InputError("a skipped cylinder says why", number=number, serial=serial)
        if line.state is state:
            raise ConflictError("that line is already there", number=number, serial=serial)
        updated_lines = tuple(
            replace(item, state=state, note=note or item.note) if item.serial == serial else item
            for item in order.lines
        )
        return self._orders.put(number, replace(order, lines=updated_lines))

    def close(self, number: str, when: date) -> WorkOrder:
        """Finish an order, refusing one with a cylinder still pending."""
        order = self._must_be(number, {OrderState.STARTED})
        if order.pending:
            raise ConflictError(
                "that order still has cylinders on it",
                number=number,
                pending=tuple(line.serial for line in order.pending),
            )
        updated = replace(order, state=OrderState.DONE, closed_on=when)
        return self._orders.put(number, updated)

    def cancel(self, number: str, when: date, note: str) -> WorkOrder:
        order = self._orders.get(number)
        if not order.is_open:
            raise ConflictError("that order is closed", number=number, state=str(order.state))
        if not note.strip():
            raise InputError("a cancelled order says why", number=number)
        updated = replace(order, state=OrderState.CANCELLED, closed_on=when, note=note)
        return self._orders.put(number, updated)

    def _must_be(self, number: str, states: Iterable[OrderState]) -> WorkOrder:
        order = self._orders.get(number)
        if order.state not in set(states):
            raise ConflictError(
                "the order is not in a state for that",
                number=number,
                state=str(order.state),
            )
        return order

    def open_orders(self) -> tuple[WorkOrder, ...]:
        return self._orders.where(lambda order: order.is_open)

    def for_technician(self, technician: str) -> tuple[WorkOrder, ...]:
        return self._orders.where(lambda order: order.technician == technician)

    def for_incident(self, incident: str) -> tuple[WorkOrder, ...]:
        return self._orders.where(lambda order: order.incident == incident)

    def due_on(self, when: date) -> tuple[WorkOrder, ...]:
        return self._orders.where(lambda order: order.is_open and order.scheduled_for == when)

    def workload(self) -> dict[str, int]:
        """Cylinders outstanding per technician, busiest first."""
        counts: dict[str, int] = {}
        for order in self.open_orders():
            if not order.technician:
                continue
            counts[order.technician] = counts.get(order.technician, 0) + len(order.pending)
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def __contains__(self, number: object) -> bool:
        return number in self._orders

    def __iter__(self) -> Iterator[WorkOrder]:
        return iter(self._orders.values())

    def __len__(self) -> int:
        return len(self._orders)

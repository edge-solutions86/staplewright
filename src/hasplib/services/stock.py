"""Blank stock as the shop moves it, with the total and the line together.

The rule this exists to hold is that ``key_blanks.stock`` is never written
by anybody except this service, and never without a line to explain it. A
total somebody adjusted by hand is a total nobody can reconcile, and the
whole reason the ledger went in was that the drawer and the column had been
disagreeing for a year with no way to find out where.

Restricted blanks get the extra rule: a cut against a restricted part needs a
reference, because that is the number somebody follows back when a key turns
up that should not exist. Unrestricted brass is two dollars and nobody cares.

A count is the interesting movement. It does not say how many moved, it says
what was in the drawer, and the service works out the difference and writes
that as the line. The difference is the finding.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from hasplib.errors import CapacityError, ConflictError, InputError, NotFoundError
from hasplib.persistence.models.reference import BlankRow
from hasplib.persistence.models.stock import (
    PurchaseLineRow,
    PurchaseOrderRow,
    StockLineRow,
)
from hasplib.persistence.repositories import Repository

TAKES_STOCK_OUT = frozenset({"cut", "scrapped"})


class BlankRepository(Repository[BlankRow]):
    model = BlankRow
    noun = "blank"
    order = (BlankRow.part_number,)

    def on_section(self, section_id: int) -> Sequence[BlankRow]:
        return self.rows(BlankRow.section_id == section_id)

    def restricted(self) -> Sequence[BlankRow]:
        return self.rows(BlankRow.restricted.is_(True))

    def below_reorder(self) -> Sequence[BlankRow]:
        return self.rows(BlankRow.stock < BlankRow.reorder_at)


class StockLineRepository(Repository[StockLineRow]):
    model = StockLineRow
    noun = "stock line"
    order = (StockLineRow.id,)

    def for_part(self, part_number: str) -> Sequence[StockLineRow]:
        return self.rows(StockLineRow.part_number == part_number)

    def since(self, when: date) -> Sequence[StockLineRow]:
        return self.rows(StockLineRow.moved_on >= when, order=StockLineRow.moved_on)

    def against(self, reference: str) -> Sequence[StockLineRow]:
        return self.rows(StockLineRow.reference == reference)


class OrderRepository(Repository[PurchaseOrderRow]):
    model = PurchaseOrderRow
    noun = "purchase order"
    order = (PurchaseOrderRow.number,)

    def outstanding(self) -> Sequence[PurchaseOrderRow]:
        return self.rows(PurchaseOrderRow.state.in_(("placed", "part received")))

    def from_supplier(self, supplier: str) -> Sequence[PurchaseOrderRow]:
        return self.rows(PurchaseOrderRow.supplier == supplier)


@dataclass(frozen=True)
class CountResult:
    """What a physical count found, against what the ledger thought."""

    part_number: str
    expected: int
    counted: int

    @property
    def difference(self) -> int:
        return self.counted - self.expected

    @property
    def agrees(self) -> bool:
        return self.difference == 0

    def describe(self) -> str:
        if self.agrees:
            return f"{self.part_number} counted {self.counted}, agrees"
        way = "over" if self.difference > 0 else "short"
        return f"{self.part_number} counted {self.counted}, {abs(self.difference)} {way}"


class StockService:
    """Every movement of blank stock, with the running total kept honest."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.blanks = BlankRepository(session)
        self.lines = StockLineRepository(session)
        self.orders = OrderRepository(session)

    def blank(self, part_number: str) -> BlankRow:
        found = self.blanks.find(part_number)
        if found is None:
            raise NotFoundError("no such blank", part=part_number)
        return found

    def on_hand(self, part_number: str) -> int:
        return self.blank(part_number).stock

    def ledger_total(self, part_number: str) -> int:
        """The total worked out from the lines, which should match the column.

        Kept separate from ``on_hand`` on purpose. Two ways of arriving at the
        same number is the only way to notice when one of them is wrong.
        """
        self.blank(part_number)
        rows = self.lines.for_part(part_number)
        return sum(row.signed for row in rows if row.movement != "counted")

    def receive(
        self, part_number: str, quantity: int, when: date, reference: str = ""
    ) -> StockLineRow:
        """Brass arrives, usually against a purchase order."""
        return self._move(part_number, "received", quantity, when, reference)

    def cut(self, part_number: str, quantity: int, when: date, reference: str = "") -> StockLineRow:
        """Brass leaves the drawer to be cut. A restricted part needs a reference."""
        blank = self.blank(part_number)
        if blank.restricted and not reference.strip():
            raise ConflictError(
                "a restricted blank does not leave the drawer without a reference",
                part=part_number,
            )
        return self._move(part_number, "cut", quantity, when, reference)

    def scrap(self, part_number: str, quantity: int, when: date, note: str) -> StockLineRow:
        """Brass that went in the bin. The reason is not optional."""
        if not note.strip():
            raise InputError("a scrapping says why", part=part_number)
        return self._move(part_number, "scrapped", quantity, when, note=note)

    def put_back(
        self, part_number: str, quantity: int, when: date, reference: str = ""
    ) -> StockLineRow:
        """A blank came back uncut, which happens more than anybody admits."""
        return self._move(part_number, "returned", quantity, when, reference)

    def count(self, part_number: str, counted: int, when: date, note: str = "") -> CountResult:
        """Write down what was in the drawer and move the total to match."""
        if counted < 0:
            raise InputError("a drawer cannot hold less than nothing", part=part_number)
        blank = self.blank(part_number)
        result = CountResult(part_number, blank.stock, counted)
        line = StockLineRow(
            part_number=part_number,
            movement="counted",
            quantity=counted,
            moved_on=when,
            note=note or result.describe(),
        )
        self.session.add(line)
        blank.stock = counted
        self.session.flush()
        return result

    def _move(
        self,
        part_number: str,
        movement: str,
        quantity: int,
        when: date,
        reference: str = "",
        note: str = "",
    ) -> StockLineRow:
        if quantity < 1:
            raise InputError("a movement moves at least one", part=part_number)
        blank = self.blank(part_number)
        change = -quantity if movement in TAKES_STOCK_OUT else quantity
        if blank.stock + change < 0:
            raise CapacityError(
                "the drawer does not hold that many",
                part=part_number,
                held=blank.stock,
                wanted=quantity,
            )
        line = StockLineRow(
            part_number=part_number,
            movement=movement,
            quantity=quantity,
            moved_on=when,
            reference=reference,
            note=note,
        )
        self.session.add(line)
        blank.stock += change
        self.session.flush()
        return line

    def place_order(
        self,
        number: str,
        supplier: str,
        when: date,
        wanted: Sequence[tuple[str, int]],
    ) -> PurchaseOrderRow:
        """An order, with a line per part number, refusing an empty one."""
        if not wanted:
            raise InputError("an order has something on it", order=number)
        if self.orders.find(number) is not None:
            raise ConflictError("that order number is already used", order=number)
        order = PurchaseOrderRow(number=number, supplier=supplier, placed_on=when)
        self.session.add(order)
        self.session.flush()
        for part_number, quantity in wanted:
            self.blank(part_number)
            self.session.add(
                PurchaseLineRow(order_number=number, part_number=part_number, ordered=quantity)
            )
        self.session.flush()
        return order

    def order(self, number: str) -> PurchaseOrderRow:
        found = self.orders.find(number)
        if found is None:
            raise NotFoundError("no such purchase order", order=number)
        return found

    def book_in(self, number: str, part_number: str, quantity: int, when: date) -> PurchaseLineRow:
        """Brass arrives against an order, which moves stock and the line."""
        order = self.order(number)
        if order.state == "cancelled":
            raise ConflictError("that order was cancelled", order=number)
        line = self._order_line(order, part_number)
        if quantity > line.outstanding:
            raise CapacityError(
                "more arrived than was ordered",
                order=number,
                part=part_number,
                outstanding=line.outstanding,
            )
        self.receive(part_number, quantity, when, reference=number)
        line.received += quantity
        order.state = "received" if self._is_complete(order) else "part received"
        self.session.flush()
        return line

    def cancel_order(self, number: str, note: str) -> PurchaseOrderRow:
        """Stop expecting an order, which is not the same as it never existing."""
        order = self.order(number)
        if order.state == "received":
            raise ConflictError("that order already arrived", order=number)
        order.state = "cancelled"
        order.note = note
        self.session.flush()
        return order

    def _order_line(self, order: PurchaseOrderRow, part_number: str) -> PurchaseLineRow:
        for line in order.lines:
            if line.part_number == part_number:
                return line
        raise NotFoundError("that part is not on that order", order=order.number, part=part_number)

    def _is_complete(self, order: PurchaseOrderRow) -> bool:
        return all(line.outstanding == 0 for line in order.lines)

    def below_reorder(self) -> tuple[tuple[str, int, int], ...]:
        """Parts under their reorder level, shortest first."""
        found = [
            (row.part_number, row.stock, row.reorder_at) for row in self.blanks.below_reorder()
        ]
        found.sort(key=lambda item: (item[1] - item[2], item[0]))
        return tuple(found)

    def movements(self, part_number: str) -> tuple[StockLineRow, ...]:
        self.blank(part_number)
        return tuple(self.lines.for_part(part_number))

    def cut_against(self, reference: str) -> tuple[StockLineRow, ...]:
        """Every blank that left the drawer against one number."""
        return tuple(row for row in self.lines.against(reference) if row.movement == "cut")

    def totals_since(self, when: date) -> dict[str, int]:
        """How much of each part moved out since a day, most first."""
        statement = (
            select(StockLineRow.part_number, func.sum(StockLineRow.quantity))
            .where(StockLineRow.moved_on >= when, StockLineRow.movement == "cut")
            .group_by(StockLineRow.part_number)
        )
        rows = self.session.execute(statement).all()
        found = sorted(rows, key=lambda pair: (-int(pair[1]), pair[0]))
        return {part: int(total) for part, total in found}

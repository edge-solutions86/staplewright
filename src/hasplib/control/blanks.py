"""Blank stock, which is the part of key control that is actually inventory.

A blank is a piece of brass with a keyway milled in it and no cuts. On an
unrestricted keyway it is worth two dollars and nobody counts them. On a
restricted keyway it is the whole security model: the factory will only sell
them to this shop, so every blank that leaves the drawer has to be accounted
for, and one that goes missing is a bigger problem than a cut key going
missing, because a blank can be cut to anything.

So the ledger refuses to hand out a restricted blank without something to
point at, and the count of what should be in the drawer is a running total
rather than something anybody trusts a physical count for.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import CapacityError, ConflictError, InputError
from hasplib.store import Registry


class StockMove(StrEnum):
    """What one line of the blank ledger did."""

    RECEIVED = "received"
    CUT = "cut"
    SCRAPPED = "scrapped"
    RETURNED = "returned"
    COUNTED = "counted"


@dataclass(frozen=True)
class BlankKind:
    """One part number in the drawer."""

    part_number: str
    section: str
    chambers: int
    restricted: bool = False
    supplier: str = ""
    reorder_at: int = 0

    def __post_init__(self) -> None:
        if not self.part_number.strip():
            raise InputError("a blank needs a part number")
        if self.chambers < 1:
            raise InputError("a blank has chambers", part=self.part_number)
        if self.reorder_at < 0:
            raise InputError("a reorder level is not negative", part=self.part_number)


@dataclass(frozen=True)
class StockLine:
    """One movement, which is the only way a count ever changes."""

    part_number: str
    move: StockMove
    quantity: int
    when: date
    reference: str = ""
    note: str = ""

    def __post_init__(self) -> None:
        if self.quantity <= 0:
            raise InputError("a movement moves blanks", part=self.part_number)

    @property
    def signed(self) -> int:
        if self.move in {StockMove.RECEIVED, StockMove.RETURNED}:
            return self.quantity
        if self.move is StockMove.COUNTED:
            return 0
        return -self.quantity


class BlankLedger:
    """The drawer, as a running total rather than a number somebody edits."""

    def __init__(self) -> None:
        self._kinds: Registry[BlankKind] = Registry("blank", "part")
        self._lines: list[StockLine] = []

    def add_kind(self, kind: BlankKind) -> BlankKind:
        return self._kinds.add(kind.part_number, kind)

    def kind(self, part_number: str) -> BlankKind:
        return self._kinds.get(part_number)

    def receive(
        self, part_number: str, quantity: int, when: date, reference: str = ""
    ) -> StockLine:
        self.kind(part_number)
        return self._add(StockLine(part_number, StockMove.RECEIVED, quantity, when, reference))

    def cut(self, part_number: str, quantity: int, when: date, reference: str = "") -> StockLine:
        """Take blanks out to cut keys, refusing an overdraw."""
        kind = self.kind(part_number)
        if kind.restricted and not reference.strip():
            raise ConflictError(
                "a restricted blank needs something to point at",
                part=part_number,
            )
        held = self.on_hand(part_number)
        if quantity > held:
            raise CapacityError(
                "the drawer does not hold that many",
                part=part_number,
                wanted=quantity,
                held=held,
            )
        return self._add(StockLine(part_number, StockMove.CUT, quantity, when, reference))

    def scrap(self, part_number: str, quantity: int, when: date, note: str) -> StockLine:
        if not note.strip():
            raise InputError("a scrapped blank says why", part=part_number)
        held = self.on_hand(part_number)
        if quantity > held:
            raise CapacityError("the drawer does not hold that many", part=part_number)
        return self._add(StockLine(part_number, StockMove.SCRAPPED, quantity, when, note=note))

    def put_back(self, part_number: str, quantity: int, when: date) -> StockLine:
        self.kind(part_number)
        return self._add(StockLine(part_number, StockMove.RETURNED, quantity, when))

    def count(self, part_number: str, counted: int, when: date) -> int:
        """Record a physical count and report the difference from the book."""
        self.kind(part_number)
        if counted < 0:
            raise InputError("a count is not negative", part=part_number)
        book = self.on_hand(part_number)
        self._add(
            StockLine(
                part_number,
                StockMove.COUNTED,
                max(counted, 1),
                when,
                note=f"counted {counted} against {book}",
            )
        )
        return counted - book

    def _add(self, line: StockLine) -> StockLine:
        self._lines.append(line)
        return line

    def on_hand(self, part_number: str) -> int:
        self.kind(part_number)
        return sum(line.signed for line in self._lines if line.part_number == part_number)

    def lines_for(self, part_number: str) -> tuple[StockLine, ...]:
        return tuple(line for line in self._lines if line.part_number == part_number)

    def kinds(self) -> tuple[BlankKind, ...]:
        return self._kinds.by_key()

    def restricted_kinds(self) -> tuple[BlankKind, ...]:
        return tuple(kind for kind in self.kinds() if kind.restricted)

    def below_reorder(self) -> tuple[tuple[str, int, int], ...]:
        """Parts at or under their reorder level, shortest first."""
        found = [
            (kind.part_number, self.on_hand(kind.part_number), kind.reorder_at)
            for kind in self.kinds()
            if self.on_hand(kind.part_number) <= kind.reorder_at
        ]
        found.sort(key=lambda item: (item[1] - item[2], item[0]))
        return tuple(found)

    def totals(self) -> dict[str, int]:
        return {kind.part_number: self.on_hand(kind.part_number) for kind in self.kinds()}

    def __contains__(self, part_number: object) -> bool:
        return part_number in self._kinds

    def __iter__(self) -> Iterator[StockLine]:
        return iter(self._lines)

    def __len__(self) -> int:
        return len(self._lines)


@dataclass(frozen=True)
class PurchaseLine:
    """One part on one order."""

    part_number: str
    quantity: int
    received: int = 0

    def __post_init__(self) -> None:
        if self.quantity < 1:
            raise InputError("an order line orders something", part=self.part_number)
        if self.received < 0 or self.received > self.quantity:
            raise ConflictError("more arrived than was ordered", part=self.part_number)

    @property
    def outstanding(self) -> int:
        return self.quantity - self.received


class PurchaseOrder:
    """One order to a supplier, receipted line by line."""

    def __init__(self, number: str, supplier: str, lines: Sequence[PurchaseLine]) -> None:
        if not number.strip():
            raise InputError("a purchase order needs a number")
        if not lines:
            raise ConflictError("an order with no lines is not an order", number=number)
        parts = [line.part_number for line in lines]
        if len(set(parts)) != len(parts):
            raise ConflictError("a part is on the order twice", number=number)
        self.number = number
        self.supplier = supplier
        self._lines = {line.part_number: line for line in lines}

    def line(self, part_number: str) -> PurchaseLine:
        try:
            return self._lines[part_number]
        except KeyError:
            raise ConflictError(
                "that part is not on this order", number=self.number, part=part_number
            ) from None

    def receive(self, part_number: str, quantity: int) -> PurchaseLine:
        line = self.line(part_number)
        updated = replace(line, received=line.received + quantity)
        self._lines[part_number] = updated
        return updated

    @property
    def is_complete(self) -> bool:
        return all(line.outstanding == 0 for line in self._lines.values())

    def outstanding(self) -> tuple[PurchaseLine, ...]:
        found = [line for line in self._lines.values() if line.outstanding]
        found.sort(key=lambda line: line.part_number)
        return tuple(found)

    def lines(self) -> tuple[PurchaseLine, ...]:
        return tuple(self._lines[part] for part in sorted(self._lines))

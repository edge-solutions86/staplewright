"""Stock reports, which is the paperwork a restricted keyway contract needs.

The factory that sells this shop restricted blanks does so on a signed
undertaking to account for every one of them. So the reconciliation is not a
management report, it is a contractual obligation, and it has to show the
opening total, everything that moved, and the closing total in a way that
adds up on paper.

The shrinkage report is the one people argue about. A blank that cannot be
accounted for is not the same as a blank that was scrapped, and putting them
in one column is how a shop convinces itself its losses are fine.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.persistence.models.reference import BlankRow
from hasplib.persistence.models.stock import PurchaseOrderRow, StockLineRow
from hasplib.reports.page import columns

PART_WIDTH = 12
MOVEMENT_WIDTH = 10
IN_MOVEMENTS = frozenset({"received", "returned"})
OUT_MOVEMENTS = frozenset({"cut", "scrapped"})


@dataclass(frozen=True)
class Reconciliation:
    """One part number over a period, opening to closing."""

    part_number: str
    opening: int
    received: int
    returned: int
    cut: int
    scrapped: int
    counted: int
    closing: int

    @property
    def expected(self) -> int:
        """What the movements say should be there, before any count."""
        return self.opening + self.received + self.returned - self.cut - self.scrapped

    @property
    def unaccounted(self) -> int:
        """What went missing, which is the number the factory asks about.

        This is the sum of what the stocktakes moved. Every other movement is
        something somebody chose to do and can point at a reason for, so the
        only brass that is genuinely unaccounted for is the brass a count had
        to correct.
        """
        return self.closing - self.expected

    @property
    def agrees(self) -> bool:
        return self.unaccounted == 0

    def describe(self) -> str:
        if self.agrees:
            return f"{self.part_number} accounts for every blank"
        way = "over" if self.unaccounted > 0 else "unaccounted"
        return f"{self.part_number} {abs(self.unaccounted)} {way}"


def reconcile(
    blank: BlankRow, lines: Sequence[StockLineRow], since: date, until: date
) -> Reconciliation:
    """Opening total, the movements between two days, and the closing total.

    The opening total is worked backwards from where the drawer is now,
    because the drawer is the only thing anybody can point at. Working
    forwards from a total somebody wrote down years ago compounds every
    mistake since.
    """
    inside = [line for line in lines if since <= line.moved_on <= until]
    after = [line for line in lines if line.moved_on > until]
    closing = blank.stock - sum(_delta(line) for line in after)
    totals = {kind: 0 for kind in ("received", "returned", "cut", "scrapped", "counted")}
    for line in inside:
        totals[line.movement] += line.quantity
    opening = closing - sum(_delta(line) for line in inside)
    return Reconciliation(
        blank.part_number,
        opening,
        totals["received"],
        totals["returned"],
        totals["cut"],
        totals["scrapped"],
        totals["counted"],
        closing,
    )


def _delta(line: StockLineRow) -> int:
    """What one line did to the drawer, a stocktake included."""
    return line.signed


def reconciliation_sheet(found: Reconciliation) -> tuple[str, ...]:
    """One part, as the columns the undertaking asks for."""
    return (
        f"{found.part_number} from {found.opening} to {found.closing}",
        f"  received   {found.received:>6}",
        f"  returned   {found.returned:>6}",
        f"  cut        {found.cut:>6}",
        f"  scrapped   {found.scrapped:>6}",
        f"  expected   {found.expected:>6}",
        f"  {found.describe()}",
    )


def movement_log(lines: Sequence[StockLineRow]) -> tuple[str, ...]:
    """Every line, oldest first, which is the back of the pack."""
    head = columns(("day", "movement", "quantity", "against"), (12, MOVEMENT_WIDTH, 10))
    rows = [head]
    for line in sorted(lines, key=lambda item: (item.moved_on, item.id or 0)):
        rows.append(
            columns(
                (
                    line.moved_on.isoformat(),
                    line.movement,
                    str(line.signed),
                    line.reference or line.note,
                ),
                (12, MOVEMENT_WIDTH, 10),
            )
        )
    return tuple(rows)


def shrinkage(found: Sequence[Reconciliation]) -> tuple[str, ...]:
    """Parts that do not account for themselves, worst first."""
    short = [item for item in found if not item.agrees]
    if not short:
        return ("every part accounts for itself",)
    short = sorted(short, key=lambda item: (item.unaccounted, item.part_number))
    lines = [f"{len(short)} parts do not account for themselves"]
    lines.extend(f"  {item.describe()}" for item in short)
    return tuple(lines)


def reorder_sheet(blanks: Sequence[BlankRow]) -> tuple[str, ...]:
    """What to put on the next order, shortest against its level first."""
    short = [row for row in blanks if row.stock < row.reorder_at]
    if not short:
        return ("nothing is below its reorder level",)
    short.sort(key=lambda row: (row.stock - row.reorder_at, row.part_number))
    lines = [columns(("part", "held", "level", "order"), (PART_WIDTH, 8, 8))]
    for row in short:
        wanted = max(row.reorder_at * 2 - row.stock, 1)
        lines.append(
            columns(
                (row.part_number, str(row.stock), str(row.reorder_at), str(wanted)),
                (PART_WIDTH, 8, 8),
            )
        )
    return tuple(lines)


def outstanding_orders(orders: Sequence[PurchaseOrderRow]) -> tuple[str, ...]:
    """What is still coming, oldest order first, with what is left on it."""
    open_orders = [row for row in orders if row.state in ("placed", "part received")]
    if not open_orders:
        return ("nothing is on order",)
    open_orders.sort(key=lambda row: (row.placed_on, row.number))
    lines = [f"{len(open_orders)} orders outstanding"]
    for order in open_orders:
        lines.append(f"{order.number} {order.supplier} placed {order.placed_on.isoformat()}")
        for line in sorted(order.lines, key=lambda item: item.part_number):
            if line.outstanding:
                lines.append(f"  {line.part_number:<{PART_WIDTH}}{line.outstanding} to come")
    return tuple(lines)


def restricted_position(blanks: Sequence[BlankRow]) -> tuple[str, ...]:
    """How much restricted brass is in the building, which is the exposure."""
    held = [row for row in blanks if row.restricted]
    if not held:
        return ("no restricted blanks are stocked",)
    held.sort(key=lambda row: (-row.stock, row.part_number))
    total = sum(row.stock for row in held)
    lines = [f"{total} restricted blanks across {len(held)} part numbers"]
    lines.extend(f"  {row.part_number:<{PART_WIDTH}}{row.stock:>6}" for row in held)
    return tuple(lines)


def stock_report(
    blanks: Sequence[BlankRow],
    found: Sequence[Reconciliation],
    orders: Sequence[PurchaseOrderRow],
    since: date,
    until: date,
) -> tuple[str, ...]:
    """The whole pack, in the order somebody signs it."""
    return (
        f"blank stock {since.isoformat()} to {until.isoformat()}",
        "",
        *shrinkage(found),
        "",
        *restricted_position(blanks),
        "",
        *reorder_sheet(blanks),
        "",
        *outstanding_orders(orders),
    )

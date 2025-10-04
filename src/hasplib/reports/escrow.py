"""The box paperwork, which two different people want two different halves of.

The fire service wants the coverage list: which buildings have a box, where
it is, and nothing else. They do not want to know what is in it and the shop
should not be telling them.

The shop wants the exposure list: which boxes are open, how long they have
been open, and which symbols are loose because of it. That half never leaves
the shop, because a list of buildings whose emergency key is missing is a
shopping list for anybody who reads it.

So they are two functions and they are meant to stay two functions. The
temptation to write one report with a flag is exactly how the wrong half ends
up in an email to a fire officer.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.control.escrow import BoxPolicy, BoxRegister, BoxState, KeyBox
from hasplib.persistence.models.escrow import SealRow
from hasplib.reports.page import columns

BOX_WIDTH = 10
OPENING_WIDTH = 12


def coverage_sheet(register: BoxRegister, buildings: Sequence[str]) -> tuple[str, ...]:
    """Where the boxes are. Safe to hand over, and says nothing about keys."""
    lines = [columns(("building", "box", "door"), (10, BOX_WIDTH))]
    held = {box.building: box for box in register if box.is_live}
    for code in sorted(buildings):
        box = held.get(code)
        if box is None:
            lines.append(columns((code, "none", ""), (10, BOX_WIDTH)))
        else:
            lines.append(columns((code, box.number, box.opening), (10, BOX_WIDTH)))
    return tuple(lines)


def exposure_sheet(register: BoxRegister, today: date) -> tuple[str, ...]:
    """What is open and for how long. Never leaves the shop."""
    found = register.open_boxes()
    if not found:
        return ("every box is sealed",)
    lines = [f"{len(found)} boxes are not sealed"]
    for box in found:
        days = (today - (box.opened_on or box.last_seen())).days
        held = box.symbol.render() if box.holds_a_key else "nothing in it"
        lines.append(f"  {box.number:<{BOX_WIDTH}}{box.building} {held}, {days} days")
    at_risk = register.symbols_at_risk()
    if at_risk:
        lines.append("keys loose: " + ", ".join(symbol.render() for symbol in at_risk))
    return tuple(lines)


def walk_sheet(register: BoxRegister, today: date) -> tuple[str, ...]:
    """The list somebody carries round the campus, in the order to walk it."""
    due = register.due_for_check(today)
    open_boxes = register.open_boxes()
    if not due and not open_boxes:
        return ("nothing to walk this week",)
    lines: list[str] = []
    if open_boxes:
        lines.append(f"{len(open_boxes)} to reseal")
        lines.extend(
            f"  {box.number:<{BOX_WIDTH}}{box.building} {box.opening}" for box in open_boxes
        )
    if due:
        lines.append(f"{len(due)} to check")
        for box in due:
            days = (today - box.last_seen()).days
            lines.append(f"  {box.number:<{BOX_WIDTH}}{box.building} last seen {days} days ago")
    return tuple(lines)


def seal_sheet(rows: Sequence[SealRow], today: date) -> tuple[str, ...]:
    """One box's whole history, which is the answer to a found key."""
    if not rows:
        return ("no seal was ever recorded on that box",)
    lines = [columns(("seal", "key", "on", "off", "days"), (10, 8, 12, 12))]
    for row in sorted(rows, key=lambda item: (item.sealed_on, item.id or 0)):
        lines.append(
            columns(
                (
                    row.seal,
                    row.symbol.render(),
                    row.sealed_on.isoformat(),
                    row.broken_on.isoformat() if row.broken_on else "on now",
                    str(row.days_held(today)),
                ),
                (10, 8, 12, 12),
            )
        )
    return tuple(lines)


def overdue_sheet(register: BoxRegister, today: date, policy: BoxPolicy) -> tuple[str, ...]:
    """Boxes open longer than the shop said it would take to get back out."""
    found = register.overdue_reseal(today)
    if not found:
        return ("no box is overdue a reseal",)
    lines = [f"{len(found)} boxes are past the {policy.reseal_days} day reseal window"]
    for box in found:
        days = (today - (box.opened_on or today)).days
        lines.append(f"  {box.number:<{BOX_WIDTH}}{box.building} open {days} days")
    return tuple(lines)


def state_counts(register: BoxRegister) -> dict[str, int]:
    """How many boxes are in each state, in the order they read."""
    return {state.value: len(register.in_state(state)) for state in BoxState}


def shop_report(register: BoxRegister, buildings: Sequence[str], today: date) -> tuple[str, ...]:
    """The shop's half, top to bottom. This one stays in the building."""
    counts = state_counts(register)
    head = f"key boxes at {today.isoformat()}, {len(register)} on record"
    tally = ", ".join(f"{count} {name}" for name, count in counts.items() if count)
    return (
        head,
        tally or "none on any wall",
        "",
        *exposure_sheet(register, today),
        "",
        *overdue_sheet(register, today, register.policy),
        "",
        *walk_sheet(register, today),
        "",
        *_uncovered(register, buildings),
    )


def _uncovered(register: BoxRegister, buildings: Sequence[str]) -> tuple[str, ...]:
    held = {box.building for box in register if box.is_live}
    missing = sorted(code for code in buildings if code not in held)
    if not missing:
        return ("every building has a box",)
    return (f"{len(missing)} buildings have no box", *(f"  {code}" for code in missing))


def key_found(register: BoxRegister, symbol: str) -> tuple[str, ...]:
    """Which boxes hold this symbol, for the key somebody handed in."""
    matching: list[KeyBox] = [box for box in register if box.symbol.render() == symbol]
    if not matching:
        return (f"no box holds {symbol}",)
    lines = [f"{len(matching)} boxes hold {symbol}"]
    lines.extend(f"  {box.describe()}" for box in matching)
    return tuple(lines)

"""Sealed boxes to rows and back, with the seal history alongside.

The awkward part is that the domain has one object and the database has two
tables. A box in the register knows its current seal and nothing about the
ones before it, because the register is what a walk round holds and a walk
round does not care what the seal was in 2019.

So the history is built here rather than in the register. Reading is the easy
direction: the current box row is enough. Writing needs both, and the caller
gets the seal row it has to close or open, because only the caller knows
which day it is doing that on.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.control.escrow import BoxPolicy, BoxRegister, BoxState, KeyBox, OpenedBy
from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.models.escrow import KeyBoxRow, SealRow


def box_to_row(box: KeyBox) -> KeyBoxRow:
    return KeyBoxRow(
        number=box.number,
        building_code=box.building,
        opening_number=box.opening,
        symbol=box.symbol,
        seal=box.seal,
        sealed_on=box.sealed_on,
        state=str(box.state),
        checked_on=box.checked_on,
        opened_on=box.opened_on,
        opened_by=str(box.opened_by) if box.opened_by is not None else "",
        note=box.note,
    )


def box_from_row(row: KeyBoxRow) -> KeyBox:
    return KeyBox(
        number=row.number,
        building=row.building_code,
        opening=row.opening_number,
        symbol=row.symbol,
        seal=row.seal,
        sealed_on=row.sealed_on,
        state=_state(row.state),
        checked_on=row.checked_on,
        opened_on=row.opened_on,
        opened_by=_opener(row.opened_by),
        note=row.note,
    )


def _state(value: str) -> BoxState:
    try:
        return BoxState(value)
    except ValueError as bad:
        raise InputError("that is not a state a box can be in", state=value) from bad


def _opener(value: str) -> OpenedBy | None:
    if not value:
        return None
    try:
        return OpenedBy(value)
    except ValueError as bad:
        raise InputError("nobody opens a box that way", opened_by=value) from bad


def apply_to_row(row: KeyBoxRow, box: KeyBox) -> KeyBoxRow:
    """Move an existing row to match a box the register has just changed."""
    row.symbol = box.symbol
    row.seal = box.seal
    row.sealed_on = box.sealed_on
    row.state = str(box.state)
    row.checked_on = box.checked_on
    row.opened_on = box.opened_on
    row.opened_by = str(box.opened_by) if box.opened_by is not None else ""
    row.note = box.note
    return row


def seal_to_row(box: KeyBox) -> SealRow:
    """The seal that is on the box now, as the open end of its history."""
    return SealRow(
        box_number=box.number,
        seal=box.seal,
        symbol=box.symbol,
        sealed_on=box.sealed_on,
        note=box.note,
    )


def close_seal(row: SealRow, when: date, by: str = "", note: str = "") -> SealRow:
    """Write the day a seal came off, refusing to close one twice."""
    if row.broken_on is not None:
        raise InputError("that seal was already closed", seal=row.seal)
    if when < row.sealed_on:
        raise InputError("a seal cannot come off before it went on", seal=row.seal)
    row.broken_on = when
    row.broken_by = by
    if note:
        row.note = note
    return row


def current_seal(rows: Sequence[SealRow]) -> SealRow | None:
    """The one seal with no end date, or nothing on an empty box."""
    open_rows = [row for row in rows if row.is_current]
    if not open_rows:
        return None
    return max(open_rows, key=lambda row: (row.sealed_on, row.id or 0))


def seal_history(rows: Sequence[SealRow]) -> tuple[SealRow, ...]:
    """Every seal a box has carried, oldest first."""
    return tuple(sorted(rows, key=lambda row: (row.sealed_on, row.id or 0)))


def seal_on(rows: Sequence[SealRow], when: date) -> SealRow | None:
    """Which seal was on the box on a given day, for the question that matters."""
    for row in seal_history(rows):
        if row.sealed_on > when:
            continue
        if row.broken_on is None or row.broken_on >= when:
            return row
    return None


def symbol_on(rows: Sequence[SealRow], when: date) -> KeySymbol | None:
    """Which key was in the box on a day. This is the whole reason for the table."""
    found = seal_on(rows, when)
    return None if found is None else found.symbol


def register_from_rows(rows: Sequence[KeyBoxRow], policy: BoxPolicy | None = None) -> BoxRegister:
    """The whole register, put back in whatever state each box reached."""
    register = BoxRegister(policy)
    for row in rows:
        register.load(box_from_row(row))
    return register

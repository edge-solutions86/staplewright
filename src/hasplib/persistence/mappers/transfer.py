"""Handovers to rows and back, and the one join that matters.

``holder_now`` is the reason this file is not two functions. Who has a key is
the key row plus every confirmed handover on top of it, and every caller that
worked that out for itself got it slightly different: one of them took the
last handover by claim date rather than by settle date, which put a key back
with somebody who had already passed it on.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.control.transfer import Transfer, TransferBook, TransferPolicy, TransferState
from hasplib.errors import InputError
from hasplib.persistence.models.control import IssuedKeyRow
from hasplib.persistence.models.transfer import TransferRow


def transfer_to_row(transfer: Transfer) -> TransferRow:
    return TransferRow(
        number=transfer.number,
        tag=transfer.tag,
        symbol=transfer.symbol,
        passed_by=transfer.passed_by,
        passed_to=transfer.passed_to,
        claimed_on=transfer.claimed_on,
        state=str(transfer.state),
        settled_on=transfer.settled_on,
        note=transfer.note,
    )


def transfer_from_row(row: TransferRow) -> Transfer:
    return Transfer(
        number=row.number,
        tag=row.tag,
        symbol=row.symbol,
        passed_by=row.passed_by,
        passed_to=row.passed_to,
        claimed_on=row.claimed_on,
        state=_state(row.state),
        settled_on=row.settled_on,
        note=row.note,
    )


def _state(value: str) -> TransferState:
    try:
        return TransferState(value)
    except ValueError as bad:
        raise InputError("that is not a state a handover reaches", state=value) from bad


def apply_to_row(row: TransferRow, transfer: Transfer) -> TransferRow:
    """Move a stored row to match a handover the book has just settled."""
    row.state = str(transfer.state)
    row.settled_on = transfer.settled_on
    row.note = transfer.note
    return row


def book_from_rows(
    rows: Sequence[TransferRow], policy: TransferPolicy | None = None
) -> TransferBook:
    book = TransferBook(policy)
    for row in rows:
        book.load(transfer_from_row(row))
    return book


def confirmed_moves(rows: Sequence[TransferRow], tag: str) -> list[TransferRow]:
    """Every confirmed handover of one key, in the order they settled."""
    moves = [row for row in rows if row.tag == tag and row.moved and row.settled_on]
    moves.sort(key=lambda row: (row.settled_on, row.number))
    return moves


def holder_now(key: IssuedKeyRow, rows: Sequence[TransferRow], today: date) -> str:
    """Who has this key today, which is the key row plus the handovers on it.

    Sorted by the day the handover settled and not the day it was claimed.
    Two handovers claimed in one week can settle in the other order, and the
    one that settled last is the one that decides where the key is.
    """
    holder = key.keyholder
    for row in confirmed_moves(rows, key.tag):
        if row.settled_on is not None and row.settled_on <= today:
            holder = row.passed_to
    return holder


def holdings_of(
    keys: Sequence[IssuedKeyRow], rows: Sequence[TransferRow], identifier: str, today: date
) -> tuple[str, ...]:
    """Every tag this person actually has today, handovers included."""
    found = [key.tag for key in keys if holder_now(key, rows, today) == identifier]
    return tuple(sorted(found))


def strays(
    keys: Sequence[IssuedKeyRow], rows: Sequence[TransferRow], today: date
) -> tuple[tuple[str, str, str], ...]:
    """Keys whose ledger holder is not the person who has them.

    Tag, who the ledger says, who actually has it. This is the list that gets
    read out at an audit, and every line on it is a key the shop would have
    asked the wrong person to produce.
    """
    found = []
    for key in keys:
        now = holder_now(key, rows, today)
        if now != key.keyholder:
            found.append((key.tag, key.keyholder, now))
    found.sort()
    return tuple(found)

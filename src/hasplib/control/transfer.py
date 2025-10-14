"""Handing a key from one person to another without it coming back to us.

This is the transaction the shop pretends does not happen. A technician
leaves, hands their building key to whoever is covering, and nobody tells the
lock shop for eight months. The ledger says the key is with somebody who left
in March, and the first anybody finds out is when the audit asks the person
in March to produce it.

So the handover gets a record of its own, with two signatures on it and a
window it has to be confirmed inside. An unconfirmed handover is worse than
no handover, because the key is now with somebody who did not sign for it and
the record says it moved. That state is called ``claimed`` and it is the
whole reason this module exists.

The rule that makes it work is that a transfer moves the key only when the
person receiving it confirms. Until then the key is still out to the person
who had it, and the report the audit reads says so.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.lifecycle import require_state
from hasplib.store import Registry

CONFIRM_DAYS = 14


class TransferState(StrEnum):
    """Where a handover has got to."""

    CLAIMED = "claimed"
    CONFIRMED = "confirmed"
    REFUSED = "refused"
    LAPSED = "lapsed"
    CANCELLED = "cancelled"


OPEN_STATES = frozenset({TransferState.CLAIMED})
CLOSED_STATES = frozenset(
    {
        TransferState.CONFIRMED,
        TransferState.REFUSED,
        TransferState.LAPSED,
        TransferState.CANCELLED,
    }
)


@dataclass(frozen=True)
class TransferPolicy:
    """How long a claim stands before the shop stops believing it."""

    confirm_days: int = CONFIRM_DAYS
    allow_master_transfer: bool = False

    def __post_init__(self) -> None:
        if self.confirm_days < 1:
            raise InputError("a claim stands for at least a day")


@dataclass(frozen=True)
class Transfer:
    """One key moving from one pair of hands to another."""

    number: str
    tag: str
    symbol: str
    passed_by: str
    passed_to: str
    claimed_on: date
    state: TransferState = TransferState.CLAIMED
    settled_on: date | None = None
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("a transfer needs a number")
        if not self.tag.strip():
            raise InputError("a transfer needs a key tag", transfer=self.number)
        if self.passed_by == self.passed_to:
            raise ConflictError(
                "a key cannot be handed to the person who has it",
                transfer=self.number,
                keyholder=self.passed_by,
            )

    @property
    def is_open(self) -> bool:
        return self.state in OPEN_STATES

    @property
    def moved(self) -> bool:
        """Whether the ledger should show the key with the new person."""
        return self.state is TransferState.CONFIRMED

    def holder_on(self, day: date) -> str:
        """Who the shop believes has the key on a given day."""
        if self.state is TransferState.CONFIRMED and self.settled_on is not None:
            return self.passed_to if day >= self.settled_on else self.passed_by
        return self.passed_by

    def due_on(self, policy: TransferPolicy) -> date:
        from datetime import timedelta

        return self.claimed_on + timedelta(days=policy.confirm_days)

    def is_late(self, today: date, policy: TransferPolicy) -> bool:
        return self.is_open and today > self.due_on(policy)

    def describe(self) -> str:
        return (
            f"{self.number} {self.tag} {self.symbol} "
            f"{self.passed_by} to {self.passed_to} {self.state}"
        )


class TransferBook:
    """Every handover anybody has claimed."""

    def __init__(self, policy: TransferPolicy | None = None) -> None:
        self.policy = policy or TransferPolicy()
        self._transfers: Registry[Transfer] = Registry("transfer", "number")

    def claim(self, transfer: Transfer, is_master: bool = False) -> Transfer:
        """Somebody says they handed a key over."""
        if transfer.state is not TransferState.CLAIMED:
            raise ConflictError("a transfer starts claimed", transfer=transfer.number)
        if is_master and not self.policy.allow_master_transfer:
            raise ConflictError(
                "a master does not move between people without the counter",
                transfer=transfer.number,
                symbol=transfer.symbol,
            )
        held = self.open_on(transfer.tag)
        if held is not None:
            raise ConflictError(
                "that key is already claimed by somebody",
                tag=transfer.tag,
                transfer=held.number,
            )
        return self._transfers.add(transfer.number, transfer)

    def load(self, transfer: Transfer) -> Transfer:
        return self._transfers.put(transfer.number, transfer)

    def transfer(self, number: str) -> Transfer:
        return self._transfers.get(number)

    def confirm(self, number: str, when: date, note: str = "") -> Transfer:
        """The person receiving it signs, and only now does the key move."""
        found = self._for_decision(number)
        if when < found.claimed_on:
            raise InputError("a handover is confirmed after it was claimed", transfer=number)
        return self._store(
            replace(found, state=TransferState.CONFIRMED, settled_on=when, note=note)
        )

    def refuse(self, number: str, when: date, note: str) -> Transfer:
        """The person receiving it says they never got it, which is serious."""
        found = self._for_decision(number)
        if not note.strip():
            raise InputError("a refusal says why", transfer=number)
        return self._store(replace(found, state=TransferState.REFUSED, settled_on=when, note=note))

    def cancel(self, number: str, when: date, note: str = "") -> Transfer:
        """The person who claimed it takes the claim back."""
        found = self._for_decision(number)
        return self._store(
            replace(found, state=TransferState.CANCELLED, settled_on=when, note=note)
        )

    def lapse(self, number: str, today: date) -> Transfer:
        """The confirmation window ran out, so the key never moved."""
        found = self._for_decision(number)
        if not found.is_late(today, self.policy):
            raise ConflictError(
                "that claim is still inside its window",
                transfer=number,
                due=found.due_on(self.policy).isoformat(),
            )
        return self._store(
            replace(
                found,
                state=TransferState.LAPSED,
                settled_on=today,
                note="nobody confirmed inside the window",
            )
        )

    def _for_decision(self, number: str) -> Transfer:
        found = self.transfer(number)
        require_state(
            found.state,
            {TransferState.CLAIMED},
            "that handover was already settled",
            transfer=number,
        )
        return found

    def _store(self, transfer: Transfer) -> Transfer:
        return self._transfers.put(transfer.number, transfer)

    def open_on(self, tag: str) -> Transfer | None:
        return self._transfers.first(lambda item: item.tag == tag and item.is_open)

    def for_tag(self, tag: str) -> tuple[Transfer, ...]:
        return self._transfers.where(lambda item: item.tag == tag)

    def in_state(self, state: TransferState) -> tuple[Transfer, ...]:
        return self._transfers.where(lambda item: item.state is state)

    def claimed_by(self, identifier: str) -> tuple[Transfer, ...]:
        return self._transfers.where(lambda item: item.passed_by == identifier)

    def waiting_on(self, identifier: str) -> tuple[Transfer, ...]:
        return self._transfers.where(lambda item: item.passed_to == identifier and item.is_open)

    def late(self, today: date) -> tuple[Transfer, ...]:
        found = [item for item in self._transfers.values() if item.is_late(today, self.policy)]
        found.sort(key=lambda item: (item.claimed_on, item.number))
        return tuple(found)

    def holder_of(self, tag: str, day: date) -> str | None:
        """Who the shop believes has a key, following the confirmed moves."""
        moves = [
            item
            for item in self.for_tag(tag)
            if item.moved and item.settled_on is not None and item.settled_on <= day
        ]
        if not moves:
            return None
        return max(moves, key=lambda item: item.settled_on or day).passed_to

    def chain_for(self, tag: str) -> tuple[Transfer, ...]:
        """Every confirmed move of one key, oldest first."""
        moves = [item for item in self.for_tag(tag) if item.moved]
        moves.sort(key=lambda item: (item.settled_on or item.claimed_on, item.number))
        return tuple(moves)

    def __contains__(self, number: object) -> bool:
        return number in self._transfers

    def __iter__(self) -> Iterator[Transfer]:
        return iter(self._transfers.values())

    def __len__(self) -> int:
        return len(self._transfers)


def unconfirmed_exposure(book: TransferBook, today: date) -> dict[str, int]:
    """How many keys each person is sitting on unconfirmed, most first."""
    counts: dict[str, int] = {}
    for item in book.late(today):
        counts[item.passed_to] = counts.get(item.passed_to, 0) + 1
    return dict(sorted(counts.items(), key=lambda pair: (-pair[1], pair[0])))


def keys_that_moved(book: TransferBook, people: Sequence[str]) -> tuple[str, ...]:
    """Tags that ended up with somebody outside a list, which is the finding."""
    allowed = set(people)
    found = {item.tag for item in book if item.moved and item.passed_to not in allowed}
    return tuple(sorted(found))

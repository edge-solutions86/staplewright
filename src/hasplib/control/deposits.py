"""Deposits, which are the only part of key control that involves money.

Twenty five dollars a key, fifty for anything that masters, refunded when the
key comes back. The amounts are unimportant and the arithmetic is not: a
refund has to match a charge on the same tag, a deposit can be forfeited
exactly once, and the total the shop is holding has to reconcile against the
ledger of keys that are out.

Everything is in whole cents. A float in here eventually pays somebody back
twenty four dollars and ninety nine point nine nine cents, and then the
business office asks about it for a week.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError, NotFoundError


class EntryKind(StrEnum):
    """What one line of the deposit book does."""

    CHARGE = "charge"
    REFUND = "refund"
    FORFEIT = "forfeit"
    WAIVER = "waiver"


@dataclass(frozen=True)
class DepositEntry:
    """One line. Charges add, everything else takes the money away again."""

    tag: str
    keyholder: str
    kind: EntryKind
    cents: int
    when: date
    note: str = ""

    def __post_init__(self) -> None:
        if self.cents <= 0:
            raise InputError("a deposit line moves money", tag=self.tag)
        if self.kind is EntryKind.FORFEIT and not self.note.strip():
            raise InputError("a forfeit says why", tag=self.tag)

    @property
    def signed_cents(self) -> int:
        return self.cents if self.kind is EntryKind.CHARGE else -self.cents


class DepositBook:
    """Every deposit line the shop has written, in order."""

    def __init__(self) -> None:
        self._entries: list[DepositEntry] = []

    def charge(self, tag: str, keyholder: str, cents: int, when: date) -> DepositEntry:
        """Take a deposit when a key goes out."""
        if self._charge_for(tag) is not None:
            raise ConflictError("a deposit was already taken on that tag", tag=tag)
        return self._add(DepositEntry(tag, keyholder, EntryKind.CHARGE, cents, when))

    def refund(self, tag: str, when: date, cents: int | None = None) -> DepositEntry:
        """Give it back when the key comes back."""
        charge = self._must_be_open(tag)
        amount = charge.cents if cents is None else cents
        if amount > charge.cents:
            raise ConflictError(
                "a refund cannot be more than the deposit",
                tag=tag,
                charged=charge.cents,
                asked=amount,
            )
        return self._add(DepositEntry(tag, charge.keyholder, EntryKind.REFUND, amount, when))

    def forfeit(self, tag: str, when: date, note: str) -> DepositEntry:
        """The key is gone. The money stays with the shop."""
        charge = self._must_be_open(tag)
        return self._add(
            DepositEntry(tag, charge.keyholder, EntryKind.FORFEIT, charge.cents, when, note)
        )

    def waive(self, tag: str, when: date, note: str = "") -> DepositEntry:
        """A department paid centrally, so the person owes nothing."""
        charge = self._must_be_open(tag)
        return self._add(
            DepositEntry(tag, charge.keyholder, EntryKind.WAIVER, charge.cents, when, note)
        )

    def load(self, entry: DepositEntry) -> DepositEntry:
        """Put a line back as it was stored, in the order it was written."""
        return self._add(entry)

    def _add(self, entry: DepositEntry) -> DepositEntry:
        self._entries.append(entry)
        return entry

    def _charge_for(self, tag: str) -> DepositEntry | None:
        for entry in self._entries:
            if entry.tag == tag and entry.kind is EntryKind.CHARGE:
                return entry
        return None

    def _must_be_open(self, tag: str) -> DepositEntry:
        charge = self._charge_for(tag)
        if charge is None:
            raise NotFoundError("no deposit was ever taken on that tag", tag=tag)
        if self.is_closed(tag):
            raise ConflictError("that deposit is already settled", tag=tag)
        return charge

    def is_closed(self, tag: str) -> bool:
        return any(
            entry.tag == tag and entry.kind is not EntryKind.CHARGE for entry in self._entries
        )

    def held_on(self, tag: str) -> int:
        """What the shop still holds against one tag."""
        return sum(entry.signed_cents for entry in self._entries if entry.tag == tag)

    def balance_of(self, keyholder: str) -> int:
        """What the shop holds for one person across every tag."""
        return sum(entry.signed_cents for entry in self._entries if entry.keyholder == keyholder)

    def total_held(self) -> int:
        return sum(entry.signed_cents for entry in self._entries)

    def forfeited(self) -> int:
        return sum(entry.cents for entry in self._entries if entry.kind is EntryKind.FORFEIT)

    def entries_for(self, tag: str) -> tuple[DepositEntry, ...]:
        return tuple(entry for entry in self._entries if entry.tag == tag)

    def entries_of(self, keyholder: str) -> tuple[DepositEntry, ...]:
        return tuple(entry for entry in self._entries if entry.keyholder == keyholder)

    def open_tags(self) -> tuple[str, ...]:
        charged = [entry.tag for entry in self._entries if entry.kind is EntryKind.CHARGE]
        return tuple(sorted(tag for tag in charged if not self.is_closed(tag)))

    def by_kind(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for entry in self._entries:
            counts[str(entry.kind)] = counts.get(str(entry.kind), 0) + entry.cents
        return dict(sorted(counts.items()))

    def __iter__(self) -> Iterator[DepositEntry]:
        return iter(self._entries)

    def __len__(self) -> int:
        return len(self._entries)


def dollars(cents: int) -> str:
    """Money the way a receipt prints it."""
    sign = "-" if cents < 0 else ""
    whole, part = divmod(abs(cents), 100)
    return f"{sign}${whole}.{part:02d}"

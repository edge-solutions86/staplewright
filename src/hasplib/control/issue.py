"""The key ledger, which is the only record of where the metal is.

Every key that leaves the counter is stamped with a tag number and written
down against a person. The tag is the identity, not the symbol, because five
people hold a key cut AA1 and only one of them has tag 41207.

A key is out, returned, lost or stolen, and lost and stolen are different on
purpose. A key left in a coat pocket at home comes back. A key taken out of a
desk drawer means somebody chose to take it, and the shop treats the two
differently when it decides how far to rekey.

Deposits are held in whole cents because they are money and because the
refund has to come back to the exact figure that was taken.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.control.people import Directory
from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry


class KeyState(StrEnum):
    """Where one stamped key is."""

    OUT = "out"
    RETURNED = "returned"
    LOST = "lost"
    STOLEN = "stolen"


GONE_STATES = frozenset({KeyState.LOST, KeyState.STOLEN})


@dataclass(frozen=True)
class IssuedKey:
    """One stamped key and whoever signed for it."""

    tag: str
    symbol: str
    keyholder: str
    issued_on: date
    due_on: date | None = None
    deposit_cents: int = 0
    state: KeyState = KeyState.OUT
    closed_on: date | None = None
    note: str = ""

    def __post_init__(self) -> None:
        if not self.tag.strip():
            raise InputError("a key needs a tag number")
        if self.deposit_cents < 0:
            raise InputError("a deposit is not negative", tag=self.tag)
        if self.due_on is not None and self.due_on < self.issued_on:
            raise ConflictError("a key is not due back before it goes out", tag=self.tag)

    @property
    def is_out(self) -> bool:
        return self.state is KeyState.OUT

    @property
    def is_gone(self) -> bool:
        return self.state in GONE_STATES

    def overdue_on(self, today: date) -> bool:
        return self.is_out and self.due_on is not None and today > self.due_on

    def days_late(self, today: date) -> int:
        if not self.overdue_on(today) or self.due_on is None:
            return 0
        return (today - self.due_on).days

    def describe(self) -> str:
        return f"tag {self.tag} cut {self.symbol}, {self.keyholder}, {self.state.value}"


class KeyLedger:
    """Every key ever issued, open and closed."""

    def __init__(self, directory: Directory) -> None:
        self.directory = directory
        self._keys: Registry[IssuedKey] = Registry("key in the ledger", "tag")

    def issue(
        self,
        tag: str,
        symbol: str,
        keyholder: str,
        when: date,
        due_on: date | None = None,
        deposit_cents: int = 0,
    ) -> IssuedKey:
        """Hand a key over, refusing anybody who may not hold one."""
        if self._keys.has(tag):
            raise ConflictError("that tag is already in the ledger", tag=tag)
        person = self.directory.person(keyholder)
        if not person.may_hold_keys(when):
            raise ConflictError(
                "that keyholder may not hold keys",
                tag=tag,
                keyholder=keyholder,
                standing=str(person.standing),
            )
        if person.ends_on is not None and due_on is not None and due_on > person.ends_on:
            raise ConflictError(
                "a key cannot be due back after the contract ends",
                tag=tag,
                due_on=due_on.isoformat(),
                ends_on=person.ends_on.isoformat(),
            )
        key = IssuedKey(tag, symbol, keyholder, when, due_on, deposit_cents)
        return self._keys.add(tag, key)

    def key(self, tag: str) -> IssuedKey:
        return self._keys.get(tag)

    def take_back(self, tag: str, when: date, note: str = "") -> IssuedKey:
        """A key comes back over the counter."""
        key = self._must_be_out(tag)
        return self._store(replace(key, state=KeyState.RETURNED, closed_on=when, note=note))

    def report_lost(self, tag: str, when: date, note: str = "") -> IssuedKey:
        key = self._must_be_out(tag)
        return self._store(replace(key, state=KeyState.LOST, closed_on=when, note=note))

    def report_stolen(self, tag: str, when: date, note: str) -> IssuedKey:
        key = self._must_be_out(tag)
        if not note.strip():
            raise InputError("a theft is written up", tag=tag)
        return self._store(replace(key, state=KeyState.STOLEN, closed_on=when, note=note))

    def found(self, tag: str, when: date) -> IssuedKey:
        """A key reported lost turned up. It comes back, it does not go out."""
        key = self.key(tag)
        if not key.is_gone:
            raise ConflictError("that key was not reported gone", tag=tag, state=str(key.state))
        return self._store(replace(key, state=KeyState.RETURNED, closed_on=when))

    def transfer(self, tag: str, to_keyholder: str, when: date) -> IssuedKey:
        """Move a key between people without it touching the counter.

        The shop dislikes this and does it anyway, so it is recorded rather
        than pretended away. The new holder has to be able to hold keys.
        """
        key = self._must_be_out(tag)
        person = self.directory.person(to_keyholder)
        if not person.may_hold_keys(when):
            raise ConflictError("that keyholder may not hold keys", tag=tag, keyholder=to_keyholder)
        if key.keyholder == to_keyholder:
            raise ConflictError("that key is already theirs", tag=tag)
        return self._store(replace(key, keyholder=to_keyholder, issued_on=when))

    def extend(self, tag: str, until: date) -> IssuedKey:
        key = self._must_be_out(tag)
        if key.due_on is None:
            raise ConflictError("that key has no due date", tag=tag)
        if until <= key.due_on:
            raise ConflictError("an extension moves the date forward", tag=tag)
        return self._store(replace(key, due_on=until))

    def _must_be_out(self, tag: str) -> IssuedKey:
        key = self.key(tag)
        if not key.is_out:
            raise ConflictError("that key is not out", tag=tag, state=str(key.state))
        return key

    def _store(self, key: IssuedKey) -> IssuedKey:
        return self._keys.put(key.tag, key)

    def held_by(self, keyholder: str) -> tuple[IssuedKey, ...]:
        self.directory.person(keyholder)
        return self._keys.where(lambda key: key.keyholder == keyholder and key.is_out)

    def history_of(self, keyholder: str) -> tuple[IssuedKey, ...]:
        self.directory.person(keyholder)
        return self._keys.where(lambda key: key.keyholder == keyholder)

    def out(self) -> tuple[IssuedKey, ...]:
        return self._keys.where(lambda key: key.is_out)

    def gone(self) -> tuple[IssuedKey, ...]:
        return self._keys.where(lambda key: key.is_gone)

    def overdue(self, today: date) -> tuple[IssuedKey, ...]:
        found = [key for key in self.out() if key.overdue_on(today)]
        found.sort(key=lambda key: (-key.days_late(today), key.tag))
        return tuple(found)

    def cut_to(self, symbol: str) -> tuple[IssuedKey, ...]:
        return self._keys.where(lambda key: key.symbol == symbol and key.is_out)

    def deposits_held(self) -> int:
        return sum(key.deposit_cents for key in self.out())

    def count_by_symbol(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for key in self.out():
            counts[key.symbol] = counts.get(key.symbol, 0) + 1
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def __contains__(self, tag: object) -> bool:
        return tag in self._keys

    def __iter__(self) -> Iterator[IssuedKey]:
        return iter(self._keys.values())

    def __len__(self) -> int:
        return len(self._keys)

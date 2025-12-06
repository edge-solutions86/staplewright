"""Stamping over a database, with the code book kept behind one door.

The book comes off the table on every call rather than being held on the
service, and that is not an oversight. A held book is a copy of the mapping
sitting in a process that might be a web worker, and the whole design of a
blind code is that the mapping lives in one place somebody controls.

``identify`` is the method that matters and it is deliberately narrow: one
code in, one symbol out, nothing else. Anything that hands back the table in
bulk is called ``the_whole_book`` so that it is obvious in a log and obvious
in a review.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.stamping import (
    BlindCodeBook,
    Marking,
    Stamp,
    StampPolicy,
    stamp_run,
    stamp_sheet,
    unstamped,
)
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.models.control import IssuedKeyRow
from hasplib.persistence.models.stamping import BlindCodeRow
from hasplib.persistence.repositories import KeyRepository, Repository


class BlindCodeRepository(Repository[BlindCodeRow]):
    model = BlindCodeRow
    noun = "blind code"
    order = (BlindCodeRow.symbol,)

    def by_code(self, code: str) -> BlindCodeRow | None:
        return self.one(BlindCodeRow.code == code.strip())


class StampingService:
    """What goes on the brass, and the table that reads it back."""

    def __init__(
        self,
        session: Session,
        policy: StampPolicy | None = None,
        start: int = 4000,
        step: int = 7,
    ) -> None:
        self.session = session
        self.policy = policy or StampPolicy()
        self.start = start
        self.step = step
        self.codes = BlindCodeRepository(session)
        self.keys = KeyRepository(session)

    def book(self) -> BlindCodeBook:
        """The code book as it stands, rebuilt off the table every time."""
        found = BlindCodeBook(self.start, self.step)
        for row in self.codes.all():
            found.load(row.symbol, row.code)
        return found

    def assign(self, symbol: KeySymbol, when: date, by: str = "") -> BlindCodeRow:
        """Give a symbol a code, or hand back the row it already has."""
        held = self.codes.find(symbol.render())
        if held is not None:
            return held
        book = self.book()
        code = book.assign(symbol)
        row = BlindCodeRow(symbol=symbol.render(), code=code, assigned_on=when, assigned_by=by)
        self.session.add(row)
        self.session.flush()
        return row

    def code_for(self, symbol: KeySymbol) -> str:
        row = self.codes.find(symbol.render())
        if row is None:
            raise NotFoundError("that symbol has no blind code", symbol=symbol.render())
        return row.code

    def identify(self, code: str) -> str:
        """Somebody handed in a key with a number on it. One answer, one call."""
        row = self.codes.by_code(code)
        if row is None:
            raise NotFoundError("no key was ever stamped with that", code=code)
        return row.symbol

    def the_whole_book(self) -> tuple[tuple[str, str], ...]:
        """Every pair. Named so it stands out in a log and in a review."""
        return tuple((row.symbol, row.code) for row in self.codes.all())

    def stamp_key(self, tag: str, when: date, by: str = "") -> Stamp:
        """What goes on one issued key, assigning a code if it needs one."""
        key = self.keys.find(tag)
        if key is None:
            raise NotFoundError("no such key", tag=tag)
        symbol = KeySymbol.parse(key.symbol)
        if self.policy.marking_for(symbol) is Marking.BLIND:
            self.assign(symbol, when, by)
        return stamp_run([(tag, symbol)], self.policy, self.book())[0]

    def stamp_batch(self, tags: Sequence[str], when: date, by: str = "") -> tuple[Stamp, ...]:
        """A run for the machine, assigning every code it needs first."""
        wanted: list[tuple[str, KeySymbol]] = []
        for tag in tags:
            key = self.keys.find(tag)
            if key is None:
                raise NotFoundError("no such key", tag=tag)
            symbol = KeySymbol.parse(key.symbol)
            if self.policy.marking_for(symbol) is Marking.BLIND:
                self.assign(symbol, when, by)
            wanted.append((tag, symbol))
        return stamp_run(wanted, self.policy, self.book())

    def sheet(self, tags: Sequence[str], when: date, by: str = "") -> tuple[str, ...]:
        return stamp_sheet(self.stamp_batch(tags, when, by), self.policy)

    def everything_out(self, when: date, by: str = "") -> tuple[Stamp, ...]:
        """The stamping for every key currently out, for a re-marking run."""
        return self.stamp_batch([row.tag for row in self.keys.out()], when, by)

    def blank_keys(self, when: date) -> tuple[str, ...]:
        """Keys that would carry nothing, which the shop cannot identify."""
        return unstamped(self.everything_out(when))

    def retire_code(self, symbol: KeySymbol) -> BlindCodeRow:
        """Take a symbol out of the book, refusing while keys still carry it.

        A code whose symbol is still on a key in somebody's pocket is a code
        the shop will be handed back one day, and a book that cannot answer
        for it is a key nobody can identify.
        """
        row = self.codes.find(symbol.render())
        if row is None:
            raise NotFoundError("that symbol has no blind code", symbol=symbol.render())
        still_out = [key for key in self.keys.out() if key.symbol == symbol.render()]
        if still_out:
            raise ConflictError(
                "keys stamped with that code are still out",
                symbol=symbol.render(),
                keys=len(still_out),
            )
        self.session.delete(row)
        self.session.flush()
        return row


def keys_for_symbol(session: Session, symbol: KeySymbol) -> tuple[str, ...]:
    """Tags cut to one symbol that are still out, for the retirement check."""
    rows = KeyRepository(session).cut_to(symbol.render())
    return tuple(row.tag for row in rows)


def unstamped_out(session: Session, policy: StampPolicy) -> tuple[str, ...]:
    """Keys out that this policy would leave blank, without touching the book."""
    found = []
    for row in KeyRepository(session).out():
        if policy.marking_for(KeySymbol.parse(row.symbol)) is Marking.NOTHING:
            found.append(row.tag)
    return tuple(sorted(found))


def issued_symbols(session: Session) -> tuple[str, ...]:
    """Every distinct symbol currently out, which is what needs a code."""
    found = {row.symbol for row in session.query(IssuedKeyRow).filter_by(state="out")}
    return tuple(sorted(found))

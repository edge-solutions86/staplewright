"""Handovers over a database, and the check nobody thinks to write.

Two things here are not in the domain book because the book cannot see them.
The first is that the person receiving the key has to be somebody who may
hold keys at all, and a handover to a keyholder whose appointment ended is a
handover to nobody. The second is that the key has to actually be out to the
person claiming to have handed it over, which sounds obvious and is the check
that catches the real mistake: somebody handing on a key they returned to the
counter last year and have not had since.

The sweep is the other half. Claims lapse on their own only if something
looks at them, so ``lapse_overdue`` is meant to be run on a clock, and the
count it returns is the number worth putting in front of somebody.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.control.transfer import (
    Transfer,
    TransferBook,
    TransferPolicy,
    TransferState,
    unconfirmed_exposure,
)
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.transfer import (
    apply_to_row,
    book_from_rows,
    holder_now,
    holdings_of,
    strays,
    transfer_to_row,
)
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.persistence.models.transfer import TransferRow
from hasplib.persistence.numbering import Numbering
from hasplib.persistence.repositories import KeyRepository, Repository

TRANSFER_SERIES = "key transfers"


class TransferRepository(Repository[TransferRow]):
    model = TransferRow
    noun = "transfer"
    order = (TransferRow.number,)

    def open_claims(self) -> Sequence[TransferRow]:
        return self.rows(TransferRow.state == "claimed")

    def for_tag(self, tag: str) -> Sequence[TransferRow]:
        return self.rows(TransferRow.tag == tag)

    def waiting_on(self, identifier: str) -> Sequence[TransferRow]:
        return self.rows(TransferRow.passed_to == identifier, TransferRow.state == "claimed")

    def claimed_by(self, identifier: str) -> Sequence[TransferRow]:
        return self.rows(TransferRow.passed_by == identifier)


class TransferService:
    """Handovers, with the checks the domain book cannot make on its own."""

    def __init__(self, session: Session, policy: TransferPolicy | None = None) -> None:
        self.session = session
        self.policy = policy or TransferPolicy()
        self.transfers = TransferRepository(session)
        self.keys = KeyRepository(session)
        self.numbering = Numbering(session)

    def open_series(self) -> None:
        self.numbering.open_series(TRANSFER_SERIES, prefix="T-", width=5)

    def book(self) -> TransferBook:
        return book_from_rows(self.transfers.all(), self.policy)

    def row(self, number: str) -> TransferRow:
        return self.transfers.require(number, "no such handover", transfer=number)

    def claim(self, tag: str, passed_to: str, when: date) -> TransferRow:
        """Somebody says they handed a key on. Nothing moves yet."""
        key = self._must_be_out(tag)
        self._must_be_able_to_hold(passed_to, when)
        symbol = KeySymbol.parse(key.symbol)
        number = self.numbering.next(TRANSFER_SERIES)
        transfer = Transfer(
            number=number,
            tag=tag,
            symbol=key.symbol,
            passed_by=key.keyholder,
            passed_to=passed_to,
            claimed_on=when,
        )
        self.book().claim(transfer, is_master=not symbol.is_change_key)
        row = transfer_to_row(transfer)
        self.session.add(row)
        self.session.flush()
        return row

    def confirm(self, number: str, when: date, note: str = "") -> TransferRow:
        row = self.row(number)
        book = self.book()
        self._must_be_able_to_hold(row.passed_to, when)
        return apply_to_row(row, book.confirm(number, when, note))

    def refuse(self, number: str, when: date, note: str) -> TransferRow:
        row = self.row(number)
        return apply_to_row(row, self.book().refuse(number, when, note))

    def cancel(self, number: str, when: date, note: str = "") -> TransferRow:
        row = self.row(number)
        return apply_to_row(row, self.book().cancel(number, when, note))

    def lapse(self, number: str, today: date) -> TransferRow:
        row = self.row(number)
        return apply_to_row(row, self.book().lapse(number, today))

    def lapse_overdue(self, today: date) -> tuple[TransferRow, ...]:
        """Close every claim nobody confirmed inside the window."""
        book = self.book()
        found = []
        for late in book.late(today):
            row = self.row(late.number)
            found.append(apply_to_row(row, book.lapse(late.number, today)))
        self.session.flush()
        return tuple(found)

    def holder_of(self, tag: str, today: date) -> str:
        """Who has this key today, which is the ledger plus the handovers."""
        key = self.keys.find(tag)
        if key is None:
            raise NotFoundError("no such key", tag=tag)
        return holder_now(key, self.transfers.for_tag(tag), today)

    def holdings(self, identifier: str, today: date) -> tuple[str, ...]:
        return holdings_of(self.keys.out(), self.transfers.all(), identifier, today)

    def strays(self, today: date) -> tuple[tuple[str, str, str], ...]:
        return strays(self.keys.out(), self.transfers.all(), today)

    def exposure(self, today: date) -> dict[str, int]:
        return unconfirmed_exposure(self.book(), today)

    def late(self, today: date) -> tuple[Transfer, ...]:
        return self.book().late(today)

    def chain_for(self, tag: str) -> tuple[Transfer, ...]:
        return self.book().chain_for(tag)

    def waiting_on(self, identifier: str) -> Sequence[TransferRow]:
        return self.transfers.waiting_on(identifier)

    def in_state(self, state: TransferState) -> Sequence[TransferRow]:
        return self.transfers.rows(TransferRow.state == str(state))

    def _must_be_out(self, tag: str) -> IssuedKeyRow:
        key = self.keys.require(tag, "no such key", tag=tag)
        if key.state != "out":
            raise ConflictError("that key is not out to anybody", tag=tag, state=key.state)
        return key

    def _must_be_able_to_hold(self, identifier: str, when: date) -> KeyholderRow:
        person = self.session.get(KeyholderRow, identifier)
        if person is None:
            raise NotFoundError("no such keyholder", keyholder=identifier)
        if person.standing != "active":
            raise ConflictError(
                "that keyholder may not hold keys",
                keyholder=identifier,
                standing=person.standing,
            )
        if person.ends_on is not None and person.ends_on < when:
            raise ConflictError(
                "that appointment has already ended",
                keyholder=identifier,
                ended=person.ends_on.isoformat(),
            )
        return person


def keys_out_to(session: Session, identifier: str) -> tuple[str, ...]:
    """Tags the ledger has against a person, before any handover is applied."""
    statement = (
        select(IssuedKeyRow.tag)
        .where(IssuedKeyRow.keyholder == identifier, IssuedKeyRow.state == "out")
        .order_by(IssuedKeyRow.tag)
    )
    return tuple(session.execute(statement).scalars().all())

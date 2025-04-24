"""The counter: a request comes in, a signature happens, a key goes out.

Everything here is one transaction. Issuing a key writes the key row, takes
the deposit, marks the request filled and burns a tag number, and any of
those happening without the others is a mess somebody finds in an audit two
years later.

The tag number is the reason this cannot be split. It comes off a gapless
counter, so it has to be taken inside the same transaction as the key row it
goes on. Take it outside and a rolled back issue leaves a hole in the tag
series, which is the one thing the series exists to prevent.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.control.deposits import DepositRates, EntryKind
from hasplib.control.requests import RequestState
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.persistence.mappers.control import directory_from_rows
from hasplib.persistence.models.control import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)
from hasplib.persistence.numbering import Numbering
from hasplib.persistence.repositories import KeyRepository, RequestRepository

TAG_SERIES = "key tags"
REQUEST_SERIES = "key requests"


DEFAULT_RATES = DepositRates()


class CounterService:
    """Requests, approvals, issues and returns, each one a transaction."""

    def __init__(self, session: Session, rates: DepositRates = DEFAULT_RATES) -> None:
        self.session = session
        self.numbering = Numbering(session)
        self.requests = RequestRepository(session)
        self.keys = KeyRepository(session)
        self.rates = rates

    def directory(self):
        people = self.session.execute(select(KeyholderRow)).scalars().all()
        signers = self.session.execute(select(AuthorizerRow)).scalars().all()
        return directory_from_rows(people, signers)

    def raise_request(
        self,
        keyholder: str,
        symbol: str,
        building: str,
        reason: str,
        when: date,
        is_master: bool = False,
    ) -> KeyRequestRow:
        directory = self.directory()
        directory.person(keyholder)
        if not reason.strip():
            raise InputError("a request needs a reason", keyholder=keyholder)
        number = self.numbering.next(REQUEST_SERIES)
        row = KeyRequestRow(
            number=number,
            keyholder=keyholder,
            symbol=symbol,
            building=building,
            reason=reason,
            raised_on=when,
            state=RequestState.SUBMITTED.value,
            is_master=is_master,
        )
        self.requests.add(row)
        self.session.flush()
        return row

    def approve(self, number: str, signer: str, when: date, note: str = "") -> KeyRequestRow:
        row = self._submitted(number)
        directory = self.directory()
        person = directory.person(row.keyholder)
        if not person.may_hold_keys(when):
            raise ConflictError(
                "that keyholder may not hold keys",
                number=number,
                standing=str(person.standing),
            )
        if not directory.may_authorize(signer, person.department, row.building, when):
            raise ConflictError(
                "that signature does not reach this request", number=number, signer=signer
            )
        if row.is_master and not directory.authorizer(signer).may_approve_masters:
            raise ConflictError("that signature is not allowed to approve a master", number=number)
        row.state = RequestState.APPROVED.value
        row.decided_on = when
        row.decided_by = signer
        row.note = note
        self.session.flush()
        return row

    def deny(self, number: str, signer: str, when: date, note: str) -> KeyRequestRow:
        row = self._submitted(number)
        if not note.strip():
            raise InputError("a denial says why", number=number)
        row.state = RequestState.DENIED.value
        row.decided_on = when
        row.decided_by = signer
        row.note = note
        self.session.flush()
        return row

    def issue(
        self, number: str, when: date, due_on: date | None = None, deposit: int | None = None
    ) -> IssuedKeyRow:
        """Hand the key over, take the deposit and close the request."""
        row = self.requests.find(number)
        if row is None:
            raise NotFoundError("no such request", number=number)
        if row.state != RequestState.APPROVED.value:
            raise ConflictError(
                "only an approved request gets filled", number=number, state=row.state
            )
        directory = self.directory()
        person = directory.person(row.keyholder)
        if not person.may_hold_keys(when):
            raise ConflictError(
                "that keyholder may not hold keys", number=number, standing=str(person.standing)
            )
        cents = self.rates.for_key(row.is_master) if deposit is None else deposit
        tag = self.numbering.next(TAG_SERIES)
        key = IssuedKeyRow(
            tag=tag,
            symbol=row.symbol,
            keyholder=row.keyholder,
            issued_on=when,
            due_on=due_on,
            deposit_cents=cents,
        )
        self.keys.add(key)
        if cents:
            self.session.add(
                DepositLineRow(
                    tag=tag,
                    keyholder=row.keyholder,
                    kind=EntryKind.CHARGE.value,
                    cents=cents,
                    happened_on=when,
                    note=f"issued against {number}",
                )
            )
        row.state = RequestState.FILLED.value
        self.session.flush()
        return key

    def take_back(self, tag: str, when: date, note: str = "") -> IssuedKeyRow:
        """A key comes back, and the deposit goes with it."""
        key = self._out(tag)
        key.state = "returned"
        key.closed_on = when
        key.note = note
        self._settle(key, EntryKind.REFUND, when, note)
        self.session.flush()
        return key

    def report_lost(self, tag: str, when: date, note: str = "") -> IssuedKeyRow:
        """A key is gone. The deposit stays with the shop."""
        key = self._out(tag)
        key.state = "lost"
        key.closed_on = when
        key.note = note
        self._settle(key, EntryKind.FORFEIT, when, note or "key never came back")
        self.session.flush()
        return key

    def _settle(self, key: IssuedKeyRow, kind: EntryKind, when: date, note: str) -> None:
        if not key.deposit_cents:
            return
        statement = select(DepositLineRow).where(DepositLineRow.tag == key.tag)
        lines = self.session.execute(statement).scalars().all()
        if any(line.kind != EntryKind.CHARGE.value for line in lines):
            raise ConflictError("that deposit is already settled", tag=key.tag)
        self.session.add(
            DepositLineRow(
                tag=key.tag,
                keyholder=key.keyholder,
                kind=kind.value,
                cents=key.deposit_cents,
                happened_on=when,
                note=note,
            )
        )

    def _submitted(self, number: str) -> KeyRequestRow:
        row = self.requests.find(number)
        if row is None:
            raise NotFoundError("no such request", number=number)
        if row.state != RequestState.SUBMITTED.value:
            raise ConflictError(
                "only a submitted request can be decided", number=number, state=row.state
            )
        return row

    def _out(self, tag: str) -> IssuedKeyRow:
        key = self.keys.find(tag)
        if key is None:
            raise NotFoundError("no such key in the ledger", tag=tag)
        if not key.is_out:
            raise ConflictError("that key is not out", tag=tag, state=key.state)
        return key

    def open_series(self) -> None:
        """Open the counters this service walks. Called once, at setup."""
        self.numbering.open_series(TAG_SERIES, width=5, start=41000)
        self.numbering.open_series(REQUEST_SERIES, prefix="R-", width=4, start=1)

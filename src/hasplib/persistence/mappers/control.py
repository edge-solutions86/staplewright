"""People, requests, keys and deposits, between the tables and the objects.

The ledger is rebuilt the way it was written: keys go in one at a time and
then their later states are put back on. That is slower than assigning the
final state straight into the collection and it is worth it, because the
rebuild goes through the same store method the counter does, so a row that
could not have been produced by the counter cannot be produced by a reload
either.

The one deliberate exception is the standing check on issue. A separated
keyholder still has keys out that were issued while they were active, and a
reload that refused them would lose exactly the rows the shop most wants to
see.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.control.deposits import DepositBook, DepositEntry, EntryKind
from hasplib.control.issue import IssuedKey, KeyLedger, KeyState
from hasplib.control.people import (
    Affiliation,
    Authorizer,
    Directory,
    Keyholder,
    Standing,
)
from hasplib.control.requests import KeyRequest, RequestBook, RequestState
from hasplib.errors import InputError
from hasplib.persistence.models.control import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)


def _enum(kind: type, value: str, noun: str):
    try:
        return kind(value)
    except ValueError:
        raise InputError(f"no such {noun}", value=value) from None


def keyholder_to_row(person: Keyholder) -> KeyholderRow:
    return KeyholderRow(
        identifier=person.identifier,
        name=person.name,
        affiliation=person.affiliation.value,
        department=person.department,
        standing=person.standing.value,
        ends_on=person.ends_on,
        separated_on=person.separated_on,
    )


def keyholder_from_row(row: KeyholderRow) -> Keyholder:
    return Keyholder(
        identifier=row.identifier,
        name=row.name,
        affiliation=_enum(Affiliation, row.affiliation, "affiliation"),
        department=row.department,
        standing=_enum(Standing, row.standing, "standing"),
        ends_on=row.ends_on,
        separated_on=row.separated_on,
    )


def authorizer_to_row(signer: Authorizer) -> AuthorizerRow:
    return AuthorizerRow(
        identifier=signer.identifier,
        department=signer.department,
        buildings=",".join(signer.buildings),
        may_approve_masters=signer.may_approve_masters,
    )


def authorizer_from_row(row: AuthorizerRow) -> Authorizer:
    return Authorizer(
        identifier=row.identifier,
        department=row.department,
        buildings=row.building_codes,
        may_approve_masters=row.may_approve_masters,
    )


def directory_from_rows(
    people: Sequence[KeyholderRow], signers: Sequence[AuthorizerRow]
) -> Directory:
    directory = Directory()
    for row in people:
        directory.add(keyholder_from_row(row))
    for signer in signers:
        directory.add_authorizer(authorizer_from_row(signer))
    return directory


def request_to_row(request: KeyRequest) -> KeyRequestRow:
    return KeyRequestRow(
        number=request.number,
        keyholder=request.keyholder,
        symbol=request.symbol,
        building=request.building,
        reason=request.reason,
        raised_on=request.raised_on,
        state=request.state.value,
        is_master=request.is_master,
        decided_on=request.decided_on,
        decided_by=request.decided_by,
        note=request.note,
    )


def request_from_row(row: KeyRequestRow) -> KeyRequest:
    return KeyRequest(
        number=row.number,
        keyholder=row.keyholder,
        symbol=row.symbol,
        building=row.building,
        reason=row.reason,
        raised_on=row.raised_on,
        state=_enum(RequestState, row.state, "request state"),
        is_master=row.is_master,
        decided_on=row.decided_on,
        decided_by=row.decided_by,
        note=row.note,
    )


def request_book_from_rows(directory: Directory, rows: Sequence[KeyRequestRow]) -> RequestBook:
    """Requests go back in as they were, decided ones included."""
    book = RequestBook(directory)
    for row in sorted(rows, key=lambda item: (item.raised_on, item.number)):
        book.load(request_from_row(row))
    return book


def key_to_row(key: IssuedKey) -> IssuedKeyRow:
    return IssuedKeyRow(
        tag=key.tag,
        symbol=key.symbol,
        keyholder=key.keyholder,
        issued_on=key.issued_on,
        due_on=key.due_on,
        deposit_cents=key.deposit_cents,
        state=key.state.value,
        closed_on=key.closed_on,
        note=key.note,
    )


def key_from_row(row: IssuedKeyRow) -> IssuedKey:
    return IssuedKey(
        tag=row.tag,
        symbol=row.symbol,
        keyholder=row.keyholder,
        issued_on=row.issued_on,
        due_on=row.due_on,
        deposit_cents=row.deposit_cents,
        state=_enum(KeyState, row.state, "key state"),
        closed_on=row.closed_on,
        note=row.note,
    )


def ledger_from_rows(directory: Directory, rows: Sequence[IssuedKeyRow]) -> KeyLedger:
    """Every key ever issued, in the order it went out."""
    ledger = KeyLedger(directory)
    for row in sorted(rows, key=lambda item: (item.issued_on, item.tag)):
        ledger.load(key_from_row(row))
    return ledger


def deposit_to_row(entry: DepositEntry) -> DepositLineRow:
    return DepositLineRow(
        tag=entry.tag,
        keyholder=entry.keyholder,
        kind=entry.kind.value,
        cents=entry.cents,
        happened_on=entry.when,
        note=entry.note,
    )


def deposit_from_row(row: DepositLineRow) -> DepositEntry:
    return DepositEntry(
        tag=row.tag,
        keyholder=row.keyholder,
        kind=_enum(EntryKind, row.kind, "deposit kind"),
        cents=row.cents,
        when=row.happened_on,
        note=row.note,
    )


def deposit_book_from_rows(rows: Sequence[DepositLineRow]) -> DepositBook:
    book = DepositBook()
    for row in sorted(rows, key=lambda item: (item.happened_on, item.id or 0)):
        book.load(deposit_from_row(row))
    return book

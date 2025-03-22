from datetime import date

import pytest
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger, KeyState
from hasplib.control.people import Standing
from hasplib.control.requests import KeyRequest, RequestBook, RequestState
from hasplib.errors import InputError
from hasplib.persistence.mappers.control import (
    authorizer_from_row,
    authorizer_to_row,
    deposit_book_from_rows,
    deposit_from_row,
    deposit_to_row,
    directory_from_rows,
    key_from_row,
    key_to_row,
    keyholder_from_row,
    keyholder_to_row,
    ledger_from_rows,
    request_book_from_rows,
    request_from_row,
    request_to_row,
)
from hasplib.persistence.models import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)
from people import TODAY, a_directory

LATER = date(2025, 6, 2)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def store_directory(session):
    directory = a_directory()
    for person in directory:
        session.add(keyholder_to_row(person))
    session.flush()
    for signer in directory.authorizers():
        session.add(authorizer_to_row(signer))
    session.flush()
    return directory


def a_ledger(directory) -> KeyLedger:
    ledger = KeyLedger(directory)
    ledger.issue("41207", "AA1", "kmaki", TODAY, deposit_cents=2500)
    ledger.issue("41208", "AA1", "dsalo", TODAY, deposit_cents=2500)
    ledger.issue("41209", "AA", "rvirta", TODAY)
    ledger.take_back("41209", LATER)
    return ledger


@pytest.mark.parametrize("identifier", ["kmaki", "tkoski", "jwexler"])
def test_a_keyholder_survives_a_round_trip(identifier):
    person = a_directory().person(identifier)
    assert keyholder_from_row(keyholder_to_row(person)) == person


def test_a_separated_keyholder_survives():
    directory = a_directory()
    person = directory.separate("kmaki", TODAY)
    back = keyholder_from_row(keyholder_to_row(person))
    assert back.standing is Standing.SEPARATED
    assert back.separated_on == TODAY


def test_an_affiliation_nobody_has_heard_of_is_refused():
    row = keyholder_to_row(a_directory().person("kmaki"))
    row.affiliation = "visiting hedgehog"
    with pytest.raises(InputError):
        keyholder_from_row(row)


def test_an_authorizer_survives_a_round_trip():
    signer = a_directory().authorizer("rvirta")
    assert authorizer_from_row(authorizer_to_row(signer)) == signer


def test_an_authorizer_with_no_buildings_survives():
    signer = a_directory().authorizer("phamm")
    back = authorizer_from_row(authorizer_to_row(signer))
    assert back.buildings == ()
    assert back.may_approve_masters


def test_a_whole_directory_comes_back(session):
    store_directory(session)
    people = session.execute(select(KeyholderRow)).scalars().all()
    signers = session.execute(select(AuthorizerRow)).scalars().all()
    back = directory_from_rows(people, signers)
    assert len(back) == len(a_directory())
    assert back.may_authorize("rvirta", "geography", "MOO", TODAY)
    assert not back.person("jwexler").may_hold_keys(TODAY)


def test_a_request_survives_a_round_trip():
    request = KeyRequest("R-001", "kmaki", "AA1", "MOO", "moved in", TODAY)
    assert request_from_row(request_to_row(request)) == request


def test_a_decided_request_survives():
    directory = a_directory()
    book = RequestBook(directory)
    book.raise_request(KeyRequest("R-001", "kmaki", "AA1", "MOO", "moved in", TODAY))
    book.submit("R-001")
    approved = book.approve("R-001", "rvirta", TODAY, "fine")
    assert request_from_row(request_to_row(approved)) == approved


def test_a_state_nobody_has_heard_of_is_refused():
    row = request_to_row(KeyRequest("R-001", "kmaki", "AA1", "MOO", "moved in", TODAY))
    row.state = "pondered"
    with pytest.raises(InputError):
        request_from_row(row)


def test_a_whole_request_book_comes_back(session):
    directory = store_directory(session)
    book = RequestBook(directory)
    book.raise_request(KeyRequest("R-001", "kmaki", "AA1", "MOO", "moved in", TODAY))
    book.raise_request(KeyRequest("R-002", "dsalo", "AA1", "MOO", "shares 216", TODAY))
    book.submit("R-002")
    for request in book:
        session.add(request_to_row(request))
    session.flush()
    rows = session.execute(select(KeyRequestRow)).scalars().all()
    back = request_book_from_rows(directory, rows)
    assert len(back) == 2
    assert back.request("R-002").state is RequestState.SUBMITTED


def test_a_key_survives_a_round_trip():
    ledger = a_ledger(a_directory())
    key = ledger.key("41207")
    assert key_from_row(key_to_row(key)) == key


def test_a_closed_key_survives():
    ledger = a_ledger(a_directory())
    key = ledger.key("41209")
    back = key_from_row(key_to_row(key))
    assert back.state is KeyState.RETURNED
    assert back.closed_on == LATER


def test_a_key_state_nobody_has_heard_of_is_refused():
    row = key_to_row(a_ledger(a_directory()).key("41207"))
    row.state = "mislaid"
    with pytest.raises(InputError):
        key_from_row(row)


def test_a_whole_ledger_comes_back(session):
    directory = store_directory(session)
    ledger = a_ledger(directory)
    for key in ledger:
        session.add(key_to_row(key))
    session.flush()
    rows = session.execute(select(IssuedKeyRow)).scalars().all()
    back = ledger_from_rows(directory, rows)
    assert len(back) == len(ledger)
    assert [key.tag for key in back.out()] == ["41207", "41208"]
    assert back.deposits_held() == 5000


def test_a_key_held_by_somebody_who_has_since_separated_still_loads(session):
    directory = store_directory(session)
    ledger = a_ledger(directory)
    directory.separate("kmaki", LATER)
    rows = [key_to_row(key) for key in ledger]
    back = ledger_from_rows(directory, rows)
    assert [key.tag for key in back.held_by("kmaki")] == ["41207"]


def test_a_deposit_line_survives_a_round_trip():
    book = DepositBook()
    entry = book.charge("41207", "kmaki", 2500, TODAY)
    assert deposit_from_row(deposit_to_row(entry)) == entry


def test_a_forfeit_survives():
    book = DepositBook()
    book.charge("41207", "kmaki", 2500, TODAY)
    entry = book.forfeit("41207", LATER, "never came back")
    assert deposit_from_row(deposit_to_row(entry)).note == "never came back"


def test_a_deposit_kind_nobody_has_heard_of_is_refused():
    book = DepositBook()
    row = deposit_to_row(book.charge("41207", "kmaki", 2500, TODAY))
    row.kind = "donation"
    with pytest.raises(InputError):
        deposit_from_row(row)


def test_a_whole_deposit_book_comes_back(session):
    directory = store_directory(session)
    ledger = a_ledger(directory)
    for key in ledger:
        session.add(key_to_row(key))
    session.flush()
    book = DepositBook()
    book.charge("41207", "kmaki", 2500, TODAY)
    book.charge("41208", "dsalo", 2500, TODAY)
    book.refund("41208", LATER)
    for entry in book:
        session.add(deposit_to_row(entry))
    session.flush()
    rows = session.execute(select(DepositLineRow)).scalars().all()
    back = deposit_book_from_rows(rows)
    assert back.total_held() == 2500
    assert back.open_tags() == ("41207",)

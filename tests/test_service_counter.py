from datetime import date

import pytest
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.persistence.mappers.control import authorizer_to_row, keyholder_to_row
from hasplib.persistence.models import DepositLineRow, KeyholderRow
from hasplib.services.counter import CounterService, DepositRates
from people import TODAY, a_directory

LATER = date(2025, 6, 2)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_counter(session, **rates) -> CounterService:
    directory = a_directory()
    for person in directory:
        session.add(keyholder_to_row(person))
    session.flush()
    for signer in directory.authorizers():
        session.add(authorizer_to_row(signer))
    session.flush()
    service = CounterService(session, DepositRates(**rates) if rates else DepositRates())
    service.open_series()
    return service


def a_request(service, **overrides):
    settings = {
        "keyholder": "kmaki",
        "symbol": "AA1",
        "building": "MOO",
        "reason": "moved into 214",
        "when": TODAY,
    }
    settings.update(overrides)
    return service.raise_request(**settings)


def test_a_request_gets_the_next_number(session):
    service = a_counter(session)
    assert a_request(service).number == "R-0001"
    assert a_request(service).number == "R-0002"


def test_a_request_arrives_submitted(session):
    service = a_counter(session)
    assert a_request(service).state == "submitted"


def test_a_request_needs_a_reason(session):
    service = a_counter(session)
    with pytest.raises(InputError):
        a_request(service, reason="  ")


def test_a_request_for_somebody_who_is_not_there(session):
    service = a_counter(session)
    with pytest.raises(NotFoundError):
        a_request(service, keyholder="ghost")


def test_the_right_signature_approves(session):
    service = a_counter(session)
    row = a_request(service)
    approved = service.approve(row.number, "rvirta", TODAY)
    assert approved.state == "approved"
    assert approved.decided_by == "rvirta"


def test_a_signature_that_does_not_reach_is_refused(session):
    service = a_counter(session)
    row = a_request(service)
    with pytest.raises(ConflictError):
        service.approve(row.number, "phamm", TODAY)


def test_a_master_needs_a_signature_that_may_approve_one(session):
    service = a_counter(session)
    row = a_request(service, symbol="AA", is_master=True)
    with pytest.raises(ConflictError):
        service.approve(row.number, "rvirta", TODAY)


def test_a_separated_keyholder_cannot_be_approved(session):
    service = a_counter(session)
    row = a_request(service)
    person = session.get(KeyholderRow, "kmaki")
    person.standing = "separated"
    person.separated_on = TODAY
    session.flush()
    with pytest.raises(ConflictError) as caught:
        service.approve(row.number, "rvirta", TODAY)
    assert caught.value.facts["standing"] == "separated"


def test_a_keyholder_who_separates_after_approval_gets_no_key(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    person = session.get(KeyholderRow, "kmaki")
    person.standing = "separated"
    person.separated_on = TODAY
    session.flush()
    with pytest.raises(ConflictError):
        service.issue(row.number, LATER)


def test_a_denial_says_why(session):
    service = a_counter(session)
    row = a_request(service)
    denied = service.deny(row.number, "rvirta", TODAY, "the room is shared")
    assert denied.state == "denied"
    with pytest.raises(ConflictError):
        service.approve(row.number, "rvirta", TODAY)


def test_a_denial_without_a_reason_is_refused(session):
    service = a_counter(session)
    row = a_request(service)
    with pytest.raises(InputError):
        service.deny(row.number, "rvirta", TODAY, "   ")


def test_issuing_takes_a_tag_and_charges_the_deposit(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY)
    assert key.tag == "41000"
    assert key.deposit_cents == 2500
    lines = session.execute(select(DepositLineRow)).scalars().all()
    assert [line.kind for line in lines] == ["charge"]


def test_a_master_costs_more(session):
    service = a_counter(session)
    row = a_request(service, keyholder="phamm", building="HEI", symbol="B", is_master=True)
    service.approve(row.number, "phamm", TODAY)
    assert service.issue(row.number, TODAY).deposit_cents == 5000


def test_the_shop_can_name_its_own_rates(session):
    service = a_counter(session, change_key_cents=1000, master_cents=9000)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    assert service.issue(row.number, TODAY).deposit_cents == 1000


def test_a_deposit_can_be_waived_at_the_counter(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY, deposit=0)
    assert key.deposit_cents == 0
    assert session.execute(select(DepositLineRow)).scalars().all() == []


def test_issuing_closes_the_request(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    service.issue(row.number, TODAY)
    assert row.state == "filled"
    with pytest.raises(ConflictError):
        service.issue(row.number, TODAY)


def test_an_unapproved_request_is_not_filled(session):
    service = a_counter(session)
    row = a_request(service)
    with pytest.raises(ConflictError):
        service.issue(row.number, TODAY)


def test_a_request_that_does_not_exist(session):
    service = a_counter(session)
    with pytest.raises(NotFoundError):
        service.issue("R-9999", TODAY)


def test_a_returned_key_gets_its_deposit_back(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY)
    service.take_back(key.tag, LATER)
    lines = session.execute(select(DepositLineRow)).scalars().all()
    assert [line.kind for line in lines] == ["charge", "refund"]
    assert key.state == "returned"


def test_a_lost_key_forfeits_the_deposit(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY)
    service.report_lost(key.tag, LATER, "left on a bus")
    lines = session.execute(select(DepositLineRow)).scalars().all()
    assert [line.kind for line in lines] == ["charge", "forfeit"]
    assert key.state == "lost"


def test_a_key_cannot_come_back_twice(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY)
    service.take_back(key.tag, LATER)
    with pytest.raises(ConflictError):
        service.take_back(key.tag, LATER)


def test_a_tag_that_was_never_issued(session):
    service = a_counter(session)
    with pytest.raises(NotFoundError):
        service.take_back("99999", LATER)


def test_a_key_with_no_deposit_settles_quietly(session):
    service = a_counter(session)
    row = a_request(service)
    service.approve(row.number, "rvirta", TODAY)
    key = service.issue(row.number, TODAY, deposit=0)
    service.take_back(key.tag, LATER)
    assert session.execute(select(DepositLineRow)).scalars().all() == []


def test_tags_walk_in_order_across_several_issues(session):
    service = a_counter(session)
    tags = []
    for _ in range(3):
        row = a_request(service)
        service.approve(row.number, "rvirta", TODAY)
        tags.append(service.issue(row.number, TODAY).tag)
    assert tags == ["41000", "41001", "41002"]

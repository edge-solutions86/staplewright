from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)

WHEN = date(2025, 3, 4)
LATER = date(2025, 6, 2)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_person(session, identifier: str = "kmaki", **overrides) -> KeyholderRow:
    settings = {
        "identifier": identifier,
        "name": "Kirsi Maki",
        "affiliation": "faculty",
        "department": "geography",
    }
    settings.update(overrides)
    row = KeyholderRow(**settings)
    session.add(row)
    session.flush()
    return row


def a_key(session, tag: str = "41207", **overrides) -> IssuedKeyRow:
    settings = {
        "tag": tag,
        "symbol": "AA1",
        "keyholder": "kmaki",
        "issued_on": WHEN,
        "deposit_cents": 2500,
    }
    settings.update(overrides)
    row = IssuedKeyRow(**settings)
    session.add(row)
    session.flush()
    return row


def test_the_control_tables_are_on_the_metadata():
    names = table_names()
    for wanted in ("keyholders", "authorizers", "key_requests", "issued_keys", "deposit_lines"):
        assert wanted in names


def test_a_keyholder_round_trips(session):
    person = a_person(session)
    assert person.standing == "active"
    assert person.ends_on is None
    assert "kmaki" in repr(person)


def test_a_separated_record_needs_a_date(session):
    with pytest.raises(IntegrityError):
        a_person(session, standing="separated")


def test_a_separated_record_with_a_date_is_fine(session):
    a_person(session, standing="separated", separated_on=WHEN)
    assert session.get(KeyholderRow, "kmaki").separated_on == WHEN


def test_an_authorizer_hangs_off_a_keyholder(session):
    a_person(session)
    session.add(AuthorizerRow(identifier="kmaki", department="geography", buildings="MOO,VOS"))
    session.flush()
    found = session.get(AuthorizerRow, "kmaki")
    assert found.building_codes == ("MOO", "VOS")
    assert not found.may_approve_masters
    assert found.person.name == "Kirsi Maki"
    assert "kmaki" in repr(found)


def test_an_authorizer_with_no_buildings_signs_everywhere(session):
    a_person(session)
    session.add(AuthorizerRow(identifier="kmaki", department="geography"))
    session.flush()
    assert session.get(AuthorizerRow, "kmaki").building_codes == ()


def test_an_authorizer_needs_a_keyholder_that_exists(session):
    session.add(AuthorizerRow(identifier="ghost", department="geography"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_keyholder_takes_the_signature_with_it(session):
    person = a_person(session)
    session.add(AuthorizerRow(identifier="kmaki", department="geography"))
    session.flush()
    session.delete(person)
    session.flush()
    assert session.execute(select(AuthorizerRow)).scalars().all() == []


def test_a_request_round_trips(session):
    a_person(session)
    row = KeyRequestRow(
        number="R-001",
        keyholder="kmaki",
        symbol="AA1",
        building="MOO",
        reason="moved into 214",
        raised_on=WHEN,
    )
    session.add(row)
    session.flush()
    found = session.get(KeyRequestRow, "R-001")
    assert found.state == "raised"
    assert not found.is_master
    assert "R-001" in repr(found)


def test_a_decided_request_needs_a_date(session):
    a_person(session)
    session.add(
        KeyRequestRow(
            number="R-001",
            keyholder="kmaki",
            symbol="AA1",
            building="MOO",
            reason="moved in",
            raised_on=WHEN,
            state="approved",
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_request_needs_a_keyholder_that_exists(session):
    session.add(
        KeyRequestRow(
            number="R-001",
            keyholder="ghost",
            symbol="AA1",
            building="MOO",
            reason="moved in",
            raised_on=WHEN,
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_key_round_trips_against_its_holder(session):
    a_person(session)
    key = a_key(session)
    assert key.is_out
    assert key.holder.department == "geography"
    assert "41207" in repr(key)


def test_a_negative_deposit_is_refused(session):
    a_person(session)
    with pytest.raises(IntegrityError):
        a_key(session, deposit_cents=-1)


def test_a_key_cannot_be_due_before_it_went_out(session):
    a_person(session)
    with pytest.raises(IntegrityError):
        a_key(session, due_on=date(2025, 1, 1))


def test_a_key_due_on_the_day_it_went_out_is_allowed(session):
    a_person(session)
    a_key(session, due_on=WHEN)
    assert session.get(IssuedKeyRow, "41207").due_on == WHEN


def test_a_keyholder_lists_the_keys_they_have_had(session):
    person = a_person(session)
    a_key(session)
    a_key(session, "41208", symbol="AA")
    session.refresh(person)
    assert sorted(key.tag for key in person.keys) == ["41207", "41208"]


def test_a_key_cannot_be_issued_to_somebody_who_is_not_there(session):
    with pytest.raises(IntegrityError):
        a_key(session, keyholder="ghost")


def test_deposit_lines_hang_off_the_key(session):
    a_person(session)
    key = a_key(session)
    session.add_all(
        [
            DepositLineRow(
                tag="41207", keyholder="kmaki", kind="charge", cents=2500, happened_on=WHEN
            ),
            DepositLineRow(
                tag="41207", keyholder="kmaki", kind="refund", cents=2500, happened_on=LATER
            ),
        ]
    )
    session.flush()
    session.refresh(key)
    assert len(key.deposits) == 2
    assert sum(line.signed_cents for line in key.deposits) == 0


def test_a_line_that_moves_nothing_is_refused(session):
    a_person(session)
    a_key(session)
    session.add(
        DepositLineRow(tag="41207", keyholder="kmaki", kind="charge", cents=0, happened_on=WHEN)
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_line_needs_a_key_that_exists(session):
    session.add(
        DepositLineRow(tag="99999", keyholder="kmaki", kind="charge", cents=100, happened_on=WHEN)
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_charge_adds_and_everything_else_takes_away(session):
    a_person(session)
    a_key(session)
    charge = DepositLineRow(
        tag="41207", keyholder="kmaki", kind="charge", cents=2500, happened_on=WHEN
    )
    forfeit = DepositLineRow(
        tag="41207", keyholder="kmaki", kind="forfeit", cents=2500, happened_on=LATER
    )
    session.add_all([charge, forfeit])
    session.flush()
    assert charge.signed_cents == 2500
    assert forfeit.signed_cents == -2500
    assert "forfeit" in repr(forfeit)

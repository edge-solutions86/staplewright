from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.control.transfer import Transfer, TransferState
from hasplib.errors import InputError
from hasplib.persistence.mappers.transfer import (
    apply_to_row,
    book_from_rows,
    confirmed_moves,
    holder_now,
    holdings_of,
    strays,
    transfer_from_row,
    transfer_to_row,
)
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.persistence.models.transfer import TransferRow

CLAIMED = date(2025, 3, 4)
SOON = date(2025, 3, 10)
LATER = date(2025, 5, 1)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    for name in ("kmaki", "rlaakso", "jkoivu"):
        made.add(
            KeyholderRow(
                identifier=name,
                name=name,
                affiliation="staff",
                department="grounds",
                standing="active",
            )
        )
    made.flush()
    made.add(
        IssuedKeyRow(
            tag="41207",
            symbol="AA1",
            keyholder="kmaki",
            issued_on=date(2025, 1, 6),
            due_on=date(2026, 1, 6),
            state="out",
        )
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def a_transfer(number: str = "T-0001", **overrides) -> Transfer:
    settings = {
        "number": number,
        "tag": "41207",
        "symbol": "AA1",
        "passed_by": "kmaki",
        "passed_to": "rlaakso",
        "claimed_on": CLAIMED,
    }
    settings.update(overrides)
    return Transfer(**settings)


def a_key(tag: str = "41207", holder: str = "kmaki") -> IssuedKeyRow:
    return IssuedKeyRow(
        tag=tag,
        symbol="AA1",
        keyholder=holder,
        issued_on=date(2025, 1, 6),
        due_on=date(2026, 1, 6),
        state="out",
    )


def test_a_transfer_goes_to_a_row_and_back():
    assert transfer_from_row(transfer_to_row(a_transfer())) == a_transfer()


def test_a_settled_transfer_keeps_its_day():
    held = a_transfer(state=TransferState.CONFIRMED, settled_on=SOON)
    assert transfer_from_row(transfer_to_row(held)).settled_on == SOON


def test_a_state_nobody_recognises_is_refused():
    row = transfer_to_row(a_transfer())
    row.state = "posted"
    with pytest.raises(InputError):
        transfer_from_row(row)


def test_a_row_moves_to_match_a_settled_transfer():
    row = transfer_to_row(a_transfer())
    settled = a_transfer(state=TransferState.CONFIRMED, settled_on=SOON, note="picked up")
    apply_to_row(row, settled)
    assert row.state == "confirmed"
    assert row.settled_on == SOON
    assert row.note == "picked up"


def test_the_book_comes_back_out_of_rows():
    rows = [transfer_to_row(a_transfer()), transfer_to_row(a_transfer("T-0002", tag="41208"))]
    assert len(book_from_rows(rows)) == 2


def test_the_book_keeps_a_transfer_in_the_state_it_reached():
    row = transfer_to_row(a_transfer(state=TransferState.LAPSED, settled_on=LATER))
    assert book_from_rows([row]).transfer("T-0001").state is TransferState.LAPSED


def test_only_the_confirmed_moves_count():
    rows = [
        transfer_to_row(a_transfer()),
        transfer_to_row(a_transfer("T-0002", state=TransferState.CONFIRMED, settled_on=SOON)),
    ]
    assert [row.number for row in confirmed_moves(rows, "41207")] == ["T-0002"]


def test_the_moves_come_back_in_the_order_they_settled():
    rows = [
        transfer_to_row(a_transfer("T-0002", state=TransferState.CONFIRMED, settled_on=LATER)),
        transfer_to_row(a_transfer("T-0001", state=TransferState.CONFIRMED, settled_on=SOON)),
    ]
    assert [row.number for row in confirmed_moves(rows, "41207")] == ["T-0001", "T-0002"]


def test_a_key_nobody_handed_on_is_still_with_its_holder():
    assert holder_now(a_key(), [], LATER) == "kmaki"


def test_a_confirmed_handover_moves_the_holder():
    rows = [transfer_to_row(a_transfer(state=TransferState.CONFIRMED, settled_on=SOON))]
    assert holder_now(a_key(), rows, LATER) == "rlaakso"


def test_a_day_before_the_handover_settled_keeps_the_old_holder():
    rows = [transfer_to_row(a_transfer(state=TransferState.CONFIRMED, settled_on=SOON))]
    assert holder_now(a_key(), rows, CLAIMED) == "kmaki"


def test_a_claim_nobody_confirmed_moves_nothing():
    rows = [transfer_to_row(a_transfer())]
    assert holder_now(a_key(), rows, LATER) == "kmaki"


def test_the_last_settled_handover_wins_even_when_it_was_claimed_first():
    rows = [
        transfer_to_row(
            a_transfer(
                "T-0001",
                claimed_on=CLAIMED,
                state=TransferState.CONFIRMED,
                settled_on=LATER,
                passed_to="jkoivu",
            )
        ),
        transfer_to_row(
            a_transfer(
                "T-0002",
                claimed_on=date(2025, 4, 1),
                state=TransferState.CONFIRMED,
                settled_on=SOON,
                passed_to="rlaakso",
            )
        ),
    ]
    assert holder_now(a_key(), rows, date(2025, 6, 1)) == "jkoivu"


def test_what_a_person_actually_holds():
    keys = [a_key(), a_key("41208")]
    rows = [transfer_to_row(a_transfer(state=TransferState.CONFIRMED, settled_on=SOON))]
    assert holdings_of(keys, rows, "kmaki", LATER) == ("41208",)
    assert holdings_of(keys, rows, "rlaakso", LATER) == ("41207",)


def test_a_person_with_nothing_holds_nothing():
    assert holdings_of([a_key()], [], "jkoivu", LATER) == ()


def test_a_key_that_moved_shows_up_as_a_stray():
    rows = [transfer_to_row(a_transfer(state=TransferState.CONFIRMED, settled_on=SOON))]
    assert strays([a_key()], rows, LATER) == (("41207", "kmaki", "rlaakso"),)


def test_a_key_that_never_moved_is_not_a_stray():
    assert strays([a_key()], [], LATER) == ()


def test_a_transfer_row_goes_in_and_comes_out(session):
    session.add(transfer_to_row(a_transfer()))
    session.flush()
    row = session.get(TransferRow, "T-0001")
    assert row.is_open
    assert not row.moved


def test_a_state_the_table_does_not_know_is_refused(session):
    row = transfer_to_row(a_transfer())
    row.state = "posted"
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_handover_to_the_same_person_is_refused(session):
    row = transfer_to_row(a_transfer())
    row.passed_to = "kmaki"
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_settled_transfer_without_a_day_is_refused(session):
    row = transfer_to_row(a_transfer())
    row.state = "confirmed"
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_settlement_before_the_claim_is_refused(session):
    row = transfer_to_row(a_transfer())
    row.state = "confirmed"
    row.settled_on = date(2024, 1, 1)
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_refusal_without_a_reason_is_refused(session):
    row = transfer_to_row(a_transfer())
    row.state = "refused"
    row.settled_on = SOON
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_one_key_cannot_have_two_open_claims(session):
    session.add(transfer_to_row(a_transfer()))
    session.flush()
    session.add(transfer_to_row(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu")))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_settled_claim_lets_another_one_open(session):
    first = transfer_to_row(a_transfer())
    session.add(first)
    session.flush()
    first.state = "confirmed"
    first.settled_on = SOON
    session.flush()
    session.add(transfer_to_row(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu")))
    session.flush()
    assert len(session.execute(select(TransferRow)).scalars().all()) == 2


def test_a_handover_of_a_key_nobody_issued_is_refused(session):
    session.add(transfer_to_row(a_transfer(tag="99999")))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_handover_to_somebody_nobody_has_is_refused(session):
    session.add(transfer_to_row(a_transfer(passed_to="nobody")))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_transfer_row_knows_its_key(session):
    session.add(transfer_to_row(a_transfer()))
    session.flush()
    assert session.get(TransferRow, "T-0001").key.symbol == "AA1"

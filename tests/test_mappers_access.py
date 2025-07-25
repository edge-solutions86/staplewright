from datetime import date

import pytest
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.keyed_alike import KeyedAlikeSet
from hasplib.control.issue import KeyLedger
from hasplib.control.review import Answer, Review, ReviewState
from hasplib.persistence.mappers.access import (
    alike_from_row,
    alike_register_from_rows,
    alike_to_rows,
    review_from_rows,
    review_line_from_row,
    review_line_to_row,
    review_to_rows,
)
from hasplib.persistence.models import (
    BuildingRow,
    KeyedAlikeSetRow,
    OpeningRow,
    ReviewRow,
)
from people import TODAY, a_directory

OPENED = date(2025, 9, 1)
DUE = date(2025, 10, 1)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    made.add(BuildingRow(code="MOO", name="Moore Hall"))
    made.flush()
    made.add_all(
        [
            OpeningRow(
                number="MOO-214", building_code="MOO", floor="2", room="214", function="office"
            ),
            OpeningRow(
                number="MOO-216", building_code="MOO", floor="2", room="216", function="office"
            ),
        ]
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def a_ledger() -> KeyLedger:
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY)
    ledger.issue("41208", "AA", "kmaki", TODAY)
    return ledger


def a_review() -> Review:
    review = Review("geography", OPENED, DUE)
    review.build_from(a_ledger())
    return review


def store(session, review: Review) -> ReviewRow:
    header, lines = review_to_rows(review)
    session.add(header)
    session.flush()
    for row in lines:
        row.review_id = header.id
        session.add(row)
    session.flush()
    return header


def test_a_line_survives_a_round_trip():
    review = a_review()
    line = review.answer("41207", Answer.CONFIRMED, DUE, "still in 214")
    assert review_line_from_row(review_line_to_row(line, 1)) == line


def test_an_unanswered_line_survives():
    line = a_review().line("41207")
    assert not review_line_from_row(review_line_to_row(line, 1)).is_answered


def test_an_answer_this_version_does_not_know_reads_as_unanswered():
    row = review_line_to_row(a_review().line("41207"), 1)
    row.answer = "maybe next year"
    assert review_line_from_row(row).answer is Answer.UNANSWERED


def test_a_whole_review_survives_the_database(session):
    review = a_review()
    review.answer("41207", Answer.CONFIRMED, DUE)
    review.answer("41208", Answer.RETURN, DUE)
    header = store(session, review)
    back = review_from_rows(header)
    assert len(back) == 2
    assert len(back.confirmed()) == 1
    assert len(back.to_return()) == 1


def test_a_closed_review_comes_back_closed(session):
    review = a_review()
    review.close(DUE)
    header = store(session, review)
    back = review_from_rows(header)
    assert back.state is ReviewState.CLOSED
    assert back.closed_on == DUE


def test_a_loaded_review_keeps_answers_the_front_door_would_refuse(session):
    review = a_review()
    review.answer("41207", Answer.CONFIRMED, DUE)
    review.close(DUE)
    back = review_from_rows(store(session, review))
    assert back.line("41207").answer is Answer.CONFIRMED


def test_the_dates_survive(session):
    header = store(session, a_review())
    back = review_from_rows(header)
    assert back.opened_on == OPENED
    assert back.due_on == DUE


def test_a_set_survives_a_round_trip(session):
    alike = KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216"), "the map room store")
    header, members = alike_to_rows(alike)
    session.add(header)
    session.flush()
    for row in members:
        session.add(row)
    session.flush()
    session.refresh(header)
    assert alike_from_row(header) == alike


def test_the_members_come_back_in_door_order(session):
    alike = KeyedAlikeSet("cupboards", ("MOO-216", "MOO-214"))
    header, members = alike_to_rows(alike)
    session.add(header)
    session.flush()
    for row in members:
        session.add(row)
    session.flush()
    session.refresh(header)
    assert alike_from_row(header).openings == ("MOO-214", "MOO-216")


def test_a_whole_register_comes_back(session):
    alike = KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216"))
    header, members = alike_to_rows(alike)
    session.add(header)
    session.flush()
    for row in members:
        session.add(row)
    session.flush()
    rows = session.execute(select(KeyedAlikeSetRow)).scalars().all()
    register = alike_register_from_rows(rows)
    assert len(register) == 1
    assert register.set_holding("MOO-214").name == "cupboards"

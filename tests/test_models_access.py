from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    BuildingRow,
    KeyedAlikeMemberRow,
    KeyedAlikeSetRow,
    OpeningRow,
    ReviewLineRow,
    ReviewRow,
)

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


def a_review(session, **overrides) -> ReviewRow:
    settings = {"department": "geography", "opened_on": OPENED, "due_on": DUE}
    settings.update(overrides)
    row = ReviewRow(**settings)
    session.add(row)
    session.flush()
    return row


def a_set(session, name: str = "moore cupboards") -> KeyedAlikeSetRow:
    row = KeyedAlikeSetRow(name=name)
    session.add(row)
    session.flush()
    return row


def test_the_access_tables_are_on_the_metadata():
    names = table_names()
    for wanted in (
        "access_reviews",
        "access_review_lines",
        "keyed_alike_sets",
        "keyed_alike_members",
    ):
        assert wanted in names


def test_a_review_round_trips(session):
    row = a_review(session)
    assert row.is_open
    assert row.lines == []
    assert "geography" in repr(row)


def test_a_review_is_due_after_it_opens(session):
    with pytest.raises(IntegrityError):
        a_review(session, due_on=date(2025, 8, 1))


def test_a_closed_review_needs_a_date(session):
    with pytest.raises(IntegrityError):
        a_review(session, state="closed")


def test_a_closed_review_with_a_date_is_fine(session):
    row = a_review(session, state="closed", closed_on=DUE)
    assert not row.is_open


def test_a_department_is_reviewed_once_per_opening_date(session):
    a_review(session)
    with pytest.raises(IntegrityError):
        a_review(session)


def test_the_same_department_next_year_is_a_new_review(session):
    a_review(session)
    a_review(session, opened_on=date(2026, 9, 1), due_on=date(2026, 10, 1))
    assert len(session.execute(select(ReviewRow)).scalars().all()) == 2


def test_lines_hang_off_a_review(session):
    review = a_review(session)
    session.add_all(
        [
            ReviewLineRow(review_id=review.id, tag="41207", keyholder="kmaki", symbol="AA1"),
            ReviewLineRow(review_id=review.id, tag="41208", keyholder="kmaki", symbol="AA"),
        ]
    )
    session.flush()
    session.refresh(review)
    assert len(review.lines) == 2
    assert not review.lines[0].is_answered


def test_an_answered_line_needs_a_date(session):
    review = a_review(session)
    session.add(
        ReviewLineRow(
            review_id=review.id,
            tag="41207",
            keyholder="kmaki",
            symbol="AA1",
            answer="confirmed",
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_an_answered_line_with_a_date_is_fine(session):
    review = a_review(session)
    row = ReviewLineRow(
        review_id=review.id,
        tag="41207",
        keyholder="kmaki",
        symbol="AA1",
        answer="confirmed",
        answered_on=DUE,
    )
    session.add(row)
    session.flush()
    assert row.is_answered
    assert "confirmed" in repr(row)


def test_a_key_appears_once_on_a_review(session):
    review = a_review(session)
    session.add(ReviewLineRow(review_id=review.id, tag="41207", keyholder="kmaki", symbol="AA1"))
    session.flush()
    session.add(ReviewLineRow(review_id=review.id, tag="41207", keyholder="dsalo", symbol="AA1"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_review_takes_its_lines(session):
    review = a_review(session)
    session.add(ReviewLineRow(review_id=review.id, tag="41207", keyholder="kmaki", symbol="AA1"))
    session.flush()
    session.delete(review)
    session.flush()
    assert session.execute(select(ReviewLineRow)).scalars().all() == []


def test_a_set_holds_its_doors(session):
    alike = a_set(session)
    session.add_all(
        [
            KeyedAlikeMemberRow(set_name=alike.name, opening_number="MOO-214"),
            KeyedAlikeMemberRow(set_name=alike.name, opening_number="MOO-216"),
        ]
    )
    session.flush()
    session.refresh(alike)
    assert alike.openings == ("MOO-214", "MOO-216")
    assert "moore cupboards" in repr(alike)


def test_a_door_belongs_to_one_set_and_the_database_says_so(session):
    first = a_set(session, "cupboards")
    second = a_set(session, "stores")
    session.add(KeyedAlikeMemberRow(set_name=first.name, opening_number="MOO-214"))
    session.flush()
    session.add(KeyedAlikeMemberRow(set_name=second.name, opening_number="MOO-214"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_member_needs_a_door_that_exists(session):
    alike = a_set(session)
    session.add(KeyedAlikeMemberRow(set_name=alike.name, opening_number="ZZZ-1"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_set_takes_its_members(session):
    alike = a_set(session)
    session.add(KeyedAlikeMemberRow(set_name=alike.name, opening_number="MOO-214"))
    session.flush()
    session.delete(alike)
    session.flush()
    assert session.execute(select(KeyedAlikeMemberRow)).scalars().all() == []

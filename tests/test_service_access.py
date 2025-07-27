from datetime import date

import pytest

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.keyed_alike import KeyedAlikeSet
from hasplib.control.review import Answer, ReviewState
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.services.access import KeyedAlikeService, ReviewService

OPENED = date(2025, 9, 1)
DUE = date(2025, 10, 1)
CLOSED = date(2025, 11, 1)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def an_open_review(session) -> ReviewService:
    service = ReviewService(session)
    service.open_review("geography", OPENED, DUE)
    return service


def test_opening_a_review_freezes_what_the_department_holds(session):
    service = an_open_review(session)
    review = service.review("geography", OPENED)
    assert len(review) == 3
    assert "41207" in review


def test_a_key_issued_later_is_not_on_this_years_review(session):
    service = an_open_review(session)
    from hasplib.persistence.models import IssuedKeyRow

    session.add(IssuedKeyRow(tag="41999", symbol="AA1", keyholder="kmaki", issued_on=DUE))
    session.flush()
    assert "41999" not in service.review("geography", OPENED)


def test_a_department_is_reviewed_once_on_a_day(session):
    service = an_open_review(session)
    with pytest.raises(ConflictError):
        service.open_review("geography", OPENED, DUE)


def test_a_review_that_never_happened(session):
    service = ReviewService(session)
    with pytest.raises(NotFoundError):
        service.review("geography", OPENED)


def test_answering_records_the_day_and_the_note(session):
    service = an_open_review(session)
    line = service.answer("geography", OPENED, "41207", Answer.CONFIRMED, DUE, "still in 214")
    assert line.answer == "confirmed"
    assert line.answered_on == DUE
    assert line.note == "still in 214"


def test_an_answer_says_something(session):
    service = an_open_review(session)
    with pytest.raises(InputError):
        service.answer("geography", OPENED, "41207", Answer.UNANSWERED, DUE)


def test_a_key_is_answered_once(session):
    service = an_open_review(session)
    service.answer("geography", OPENED, "41207", Answer.CONFIRMED, DUE)
    with pytest.raises(ConflictError):
        service.answer("geography", OPENED, "41207", Answer.RETURN, DUE)


def test_a_key_that_is_not_on_the_review(session):
    service = an_open_review(session)
    with pytest.raises(NotFoundError):
        service.answer("geography", OPENED, "41301", Answer.CONFIRMED, DUE)


def test_a_closed_review_takes_no_more_answers(session):
    service = an_open_review(session)
    service.close("geography", OPENED, CLOSED)
    with pytest.raises(ConflictError):
        service.answer("geography", OPENED, "41207", Answer.CONFIRMED, CLOSED)


def test_a_review_closes_once(session):
    service = an_open_review(session)
    service.close("geography", OPENED, CLOSED)
    with pytest.raises(ConflictError):
        service.close("geography", OPENED, CLOSED)


def test_the_open_reviews_come_back_by_department(session):
    service = an_open_review(session)
    service.open_review("facilities", OPENED, DUE)
    assert [row.department for row in service.open_reviews()] == ["facilities", "geography"]


def test_a_closed_review_drops_off_the_open_list(session):
    service = an_open_review(session)
    service.close("geography", OPENED, CLOSED)
    assert service.open_reviews() == []


def test_the_review_reads_back_with_its_answers(session):
    service = an_open_review(session)
    service.answer("geography", OPENED, "41207", Answer.RETURN, DUE)
    review = service.review("geography", OPENED)
    assert len(review.to_return()) == 1
    assert review.state is ReviewState.OPEN


def test_a_set_can_be_written(session):
    service = KeyedAlikeService(session)
    service.create(KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216")))
    assert service.get("cupboards").size == 2


def test_a_door_already_in_a_set_is_refused(session):
    service = KeyedAlikeService(session)
    service.create(KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216")))
    with pytest.raises(ConflictError):
        service.create(KeyedAlikeSet("stores", ("MOO-214", "MOO-218")))


def test_a_set_that_is_not_there(session):
    with pytest.raises(NotFoundError):
        KeyedAlikeService(session).get("nothing")


def test_the_set_holding_a_door_can_be_found(session):
    service = KeyedAlikeService(session)
    service.create(KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216")))
    assert service.set_holding("MOO-214").name == "cupboards"
    assert service.set_holding("MOO-218") is None


def test_dissolving_a_set_frees_its_doors(session):
    service = KeyedAlikeService(session)
    service.create(KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216")))
    service.dissolve("cupboards")
    assert service.set_holding("MOO-214") is None
    service.create(KeyedAlikeSet("stores", ("MOO-214", "MOO-218")))
    assert service.get("stores").size == 2


def test_dissolving_a_set_that_is_not_there(session):
    with pytest.raises(NotFoundError):
        KeyedAlikeService(session).dissolve("nothing")


def test_the_sets_come_back_in_name_order(session):
    service = KeyedAlikeService(session)
    service.create(KeyedAlikeSet("stores", ("MOO-218", "MOO-110")))
    service.create(KeyedAlikeSet("cupboards", ("MOO-214", "MOO-216")))
    assert [alike.name for alike in service.sets()] == ["cupboards", "stores"]

from datetime import date

import pytest

from hasplib.control.issue import KeyLedger
from hasplib.control.review import (
    Answer,
    Review,
    ReviewState,
    chase_list,
    keys_to_collect,
    review_for,
)
from hasplib.errors import ConflictError, InputError, NotFoundError
from people import TODAY, a_directory

OPENED = date(2025, 9, 1)
DUE = date(2025, 10, 1)
LATE = date(2025, 11, 1)


def a_ledger() -> KeyLedger:
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY)
    ledger.issue("41208", "AA", "kmaki", TODAY)
    ledger.issue("41209", "AA1", "dsalo", TODAY)
    ledger.issue("41301", "B1", "phamm", TODAY)
    return ledger


def a_review(department: str = "geography") -> Review:
    review = Review(department, OPENED, DUE)
    review.build_from(a_ledger())
    return review


def test_a_review_covers_a_department_and_a_window():
    with pytest.raises(InputError):
        Review("  ", OPENED, DUE)
    with pytest.raises(ConflictError):
        Review("geography", DUE, OPENED)


def test_a_review_lists_what_the_department_holds():
    review = a_review()
    assert len(review) == 3
    assert "41301" not in review


def test_building_twice_adds_nothing_new():
    review = a_review()
    assert review.build_from(a_ledger()) == 0


def test_a_department_holding_nothing_gets_an_empty_list():
    review = Review("physics", OPENED, DUE)
    assert review.build_from(a_ledger()) == 0
    assert len(review) == 0


def test_a_new_line_is_unanswered():
    review = a_review()
    line = review.line("41207")
    assert not line.is_answered
    assert not line.wants_return


def test_confirming_a_key_records_the_day():
    review = a_review()
    line = review.answer("41207", Answer.CONFIRMED, DUE)
    assert line.answer is Answer.CONFIRMED
    assert line.answered_on == DUE
    assert not line.wants_return


def test_asking_for_a_key_back_wants_a_return():
    review = a_review()
    assert review.answer("41207", Answer.RETURN, DUE).wants_return


def test_a_key_the_department_does_not_recognise_also_wants_a_return():
    review = a_review()
    assert review.answer("41207", Answer.UNKNOWN, DUE).wants_return


def test_an_answer_says_something():
    review = a_review()
    with pytest.raises(InputError):
        review.answer("41207", Answer.UNANSWERED, DUE)


def test_a_key_is_answered_once():
    review = a_review()
    review.answer("41207", Answer.CONFIRMED, DUE)
    with pytest.raises(ConflictError):
        review.answer("41207", Answer.RETURN, DUE)


def test_a_key_that_is_not_on_the_list():
    review = a_review()
    with pytest.raises(NotFoundError):
        review.answer("41301", Answer.CONFIRMED, DUE)


def test_the_three_lists_partition_the_review():
    review = a_review()
    review.answer("41207", Answer.CONFIRMED, DUE)
    review.answer("41208", Answer.RETURN, DUE)
    assert len(review.confirmed()) == 1
    assert len(review.to_return()) == 1
    assert len(review.unanswered()) == 1


def test_a_review_is_complete_when_everything_is_answered():
    review = a_review()
    assert not review.is_complete()
    for tag in ["41207", "41208", "41209"]:
        review.answer(tag, Answer.CONFIRMED, DUE)
    assert review.is_complete()


def test_an_empty_review_is_not_complete():
    assert not Review("physics", OPENED, DUE).is_complete()


def test_the_progress_reads_as_a_fraction():
    review = a_review()
    assert review.progress() == 0.0
    review.answer("41207", Answer.CONFIRMED, DUE)
    assert review.progress() == 0.333


def test_an_empty_review_is_all_the_way_through():
    assert Review("physics", OPENED, DUE).progress() == 1.0


def test_a_closed_review_takes_no_more_answers():
    review = a_review()
    review.close(LATE)
    assert review.state is ReviewState.CLOSED
    with pytest.raises(ConflictError):
        review.answer("41207", Answer.CONFIRMED, LATE)


def test_a_review_closes_once():
    review = a_review()
    review.close(LATE)
    with pytest.raises(ConflictError):
        review.close(LATE)


def test_an_abandoned_review_says_why():
    review = a_review()
    with pytest.raises(InputError):
        review.abandon(LATE, "  ")
    review.abandon(LATE, "the department was merged into geology")
    assert review.state is ReviewState.ABANDONED


def test_a_closed_review_cannot_be_rebuilt():
    review = a_review()
    review.close(LATE)
    with pytest.raises(ConflictError):
        review.build_from(a_ledger())


def test_a_review_is_overdue_only_while_it_is_open():
    review = a_review()
    assert not review.overdue_on(DUE)
    assert review.overdue_on(LATE)
    review.close(LATE)
    assert not review.overdue_on(LATE)


def test_the_summary_counts_the_three_lists():
    review = a_review()
    review.answer("41207", Answer.CONFIRMED, DUE)
    assert review.summary() == "geography: 1 confirmed, 0 back, 2 unanswered"


def test_the_chase_list_names_departments_that_owe_answers():
    first = a_review()
    second = Review("facilities", OPENED, DUE)
    second.build_from(a_ledger())
    for tag in ["41301"]:
        second.answer(tag, Answer.CONFIRMED, DUE)
    assert chase_list([first, second], LATE) == ("geography",)


def test_nothing_is_chased_before_the_due_date():
    assert chase_list([a_review()], DUE) == ()


def test_the_keys_to_collect_come_from_every_review():
    first = a_review()
    first.answer("41207", Answer.RETURN, DUE)
    second = Review("facilities", OPENED, DUE)
    second.build_from(a_ledger())
    second.answer("41301", Answer.UNKNOWN, DUE)
    assert keys_to_collect([first, second]) == ("41207", "41301")


def test_a_review_can_be_found_by_department():
    review = a_review()
    assert review_for([review], "geography") is review
    with pytest.raises(NotFoundError):
        review_for([review], "physics")

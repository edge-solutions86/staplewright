import pytest
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.persistence.models import CounterRow
from hasplib.persistence.numbering import Numbering
from hasplib.persistence.unit_of_work import unit_of_work


@pytest.fixture()
def sessions():
    engine = fresh_engine()
    yield sessions_for(engine)
    teardown(engine)


@pytest.fixture()
def session(sessions):
    made = sessions()
    yield made
    made.close()


def test_a_series_hands_out_consecutive_numbers(session):
    numbers = Numbering(session)
    numbers.open_series("work orders", prefix="W-", width=4, start=1041)
    assert numbers.next("work orders") == "W-1041"
    assert numbers.next("work orders") == "W-1042"
    assert numbers.next("work orders") == "W-1043"


def test_peeking_does_not_take_the_number(session):
    numbers = Numbering(session)
    numbers.open_series("work orders", prefix="W-", start=1041)
    assert numbers.peek("work orders") == "W-1041"
    assert numbers.peek("work orders") == "W-1041"
    assert numbers.next("work orders") == "W-1041"


def test_the_width_pads_the_number(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=6, start=41207)
    assert numbers.next("tags") == "041207"


def test_a_series_with_no_prefix_is_just_the_number(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=5, start=41207)
    assert numbers.next("tags") == "41207"


def test_a_number_wider_than_the_padding_is_not_truncated(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=3, start=41207)
    assert numbers.next("tags") == "41207"


def test_reserving_takes_a_run_at_once(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=5, start=41207)
    assert numbers.reserve("tags", 3) == ("41207", "41208", "41209")
    assert numbers.next("tags") == "41210"


def test_reserving_nothing_is_refused(session):
    numbers = Numbering(session)
    numbers.open_series("tags", start=1)
    with pytest.raises(InputError):
        numbers.reserve("tags", 0)


def test_two_series_do_not_interfere(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=5, start=41207)
    numbers.open_series("work orders", prefix="W-", start=1041)
    assert numbers.next("tags") == "41207"
    assert numbers.next("work orders") == "W-1041"
    assert numbers.next("tags") == "41208"


def test_a_series_is_opened_once(session):
    numbers = Numbering(session)
    numbers.open_series("tags")
    with pytest.raises(ConflictError):
        numbers.open_series("tags")


def test_asking_a_series_that_was_never_opened(session):
    numbers = Numbering(session)
    with pytest.raises(NotFoundError):
        numbers.next("nothing")
    with pytest.raises(NotFoundError):
        numbers.peek("nothing")


@pytest.mark.parametrize("broken", [{"name": " "}, {"width": 0}, {"start": 0}])
def test_a_series_that_makes_no_sense_is_refused(session, broken):
    settings = {"name": "tags", "prefix": "", "width": 4, "start": 1}
    settings.update(broken)
    with pytest.raises(InputError):
        Numbering(session).open_series(**settings)


def test_the_series_can_be_listed(session):
    numbers = Numbering(session)
    numbers.open_series("work orders", prefix="W-")
    numbers.open_series("tags", width=5)
    assert [row.name for row in numbers.all_series()] == ["tags", "work orders"]


def test_the_counter_row_reads_back_its_own_state(session):
    numbers = Numbering(session)
    numbers.open_series("tags", width=5, start=41207)
    numbers.next("tags")
    row = numbers.series("tags")
    assert row.next_value == 41208
    assert "41208" in repr(row)


def test_a_rolled_back_transaction_puts_the_number_back(sessions):
    with unit_of_work(sessions) as work:
        Numbering(work.session).open_series("tags", width=5, start=41207)
    with pytest.raises(RuntimeError), unit_of_work(sessions) as work:
        assert Numbering(work.session).next("tags") == "41207"
        raise RuntimeError("the clerk changed their mind")
    with unit_of_work(sessions) as work:
        assert Numbering(work.session).next("tags") == "41207"


def test_a_committed_number_stays_taken(sessions):
    with unit_of_work(sessions) as work:
        Numbering(work.session).open_series("tags", width=5, start=41207)
    with unit_of_work(sessions) as work:
        assert Numbering(work.session).next("tags") == "41207"
    with unit_of_work(sessions) as work:
        assert Numbering(work.session).next("tags") == "41208"


def test_a_counter_cannot_be_wound_below_one(session):
    session.add(CounterRow(name="tags", next_value=0))
    with pytest.raises(IntegrityError):
        session.flush()

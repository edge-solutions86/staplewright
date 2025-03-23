import pytest

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError
from hasplib.persistence.models import ChartRow
from hasplib.persistence.unit_of_work import UnitOfWork, unit_of_work


@pytest.fixture()
def sessions():
    engine = fresh_engine()
    yield sessions_for(engine)
    teardown(engine)


def a_chart(family: str = "LA six pin") -> ChartRow:
    return ChartRow(
        manufacturer="Sargent",
        family=family,
        chambers=6,
        depths=tuple(range(10)),
        root_mils=335,
        increment_mils=15,
        first_space_mils=231,
        space_increment_mils=156,
        macs=7,
    )


def test_leaving_the_block_commits(sessions):
    with unit_of_work(sessions) as work:
        work.charts.add(a_chart())
    with unit_of_work(sessions) as work:
        assert work.charts.count() == 1


def test_the_unit_says_it_committed(sessions):
    work = unit_of_work(sessions)
    with work:
        work.charts.add(a_chart())
    assert work.committed
    assert not work.rolled_back


def test_raising_out_of_the_block_rolls_back(sessions):
    with pytest.raises(RuntimeError), unit_of_work(sessions) as work:
        work.charts.add(a_chart())
        raise RuntimeError("something went wrong halfway")
    with unit_of_work(sessions) as work:
        assert work.charts.count() == 0


def test_the_unit_says_it_rolled_back(sessions):
    work = unit_of_work(sessions)
    with pytest.raises(RuntimeError), work:
        raise RuntimeError("no")
    assert work.rolled_back
    assert not work.committed


def test_rolling_back_by_hand_stops_the_commit(sessions):
    with unit_of_work(sessions) as work:
        work.charts.add(a_chart())
        work.rollback()
    with unit_of_work(sessions) as work:
        assert work.charts.count() == 0


def test_committing_by_hand_inside_the_block_sticks(sessions):
    with unit_of_work(sessions) as work:
        work.charts.add(a_chart())
        work.commit()
        work.charts.add(a_chart("60 five pin"))
        work.rollback()
    with unit_of_work(sessions) as work:
        assert work.charts.count() == 1


def test_a_flush_gives_the_row_an_id_without_ending_anything(sessions):
    with unit_of_work(sessions) as work:
        row = work.charts.add(a_chart())
        work.flush()
        assert row.id is not None
        assert not work.committed


def test_every_repository_is_on_the_unit(sessions):
    with unit_of_work(sessions) as work:
        for name in [
            "charts",
            "systems",
            "openings",
            "cylinders",
            "keyholders",
            "keys",
            "requests",
            "incidents",
            "orders",
            "quarantine",
        ]:
            assert hasattr(work, name)


def test_the_repositories_share_one_session(sessions):
    with unit_of_work(sessions) as work:
        assert work.charts.session is work.systems.session
        assert work.charts.session is work.session


def test_asking_for_the_session_before_opening_is_refused(sessions):
    work = UnitOfWork(sessions)
    with pytest.raises(ConflictError):
        work.session


def test_the_session_is_closed_on_the_way_out(sessions):
    work = unit_of_work(sessions)
    with work:
        work.charts.add(a_chart())
    with pytest.raises(ConflictError):
        work.session


def test_a_unit_can_be_opened_twice_in_a_row(sessions):
    work = unit_of_work(sessions)
    with work:
        work.charts.add(a_chart())
    with work:
        work.charts.add(a_chart("60 five pin"))
    with unit_of_work(sessions) as reader:
        assert reader.charts.count() == 2


def test_a_rollback_after_a_flush_still_undoes_the_write(sessions):
    with unit_of_work(sessions) as work:
        work.charts.add(a_chart())
        work.flush()
        assert work.charts.count() == 1
        work.rollback()
    with unit_of_work(sessions) as work:
        assert work.charts.count() == 0

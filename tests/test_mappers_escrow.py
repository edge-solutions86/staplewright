from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.control.escrow import BoxPolicy, BoxState, KeyBox, OpenedBy
from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.escrow import (
    apply_to_row,
    box_from_row,
    box_to_row,
    close_seal,
    current_seal,
    register_from_rows,
    seal_history,
    seal_on,
    seal_to_row,
    symbol_on,
)
from hasplib.persistence.models.campus import BuildingRow, OpeningRow
from hasplib.persistence.models.escrow import KeyBoxRow, SealRow

SEALED = date(2025, 1, 6)
WALKED = date(2025, 6, 30)
LATE = date(2025, 12, 1)
CHANGE_KEY = KeySymbol.parse("AA1")


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    made.add(BuildingRow(code="MOO", name="moore hall"))
    made.flush()
    made.add(
        OpeningRow(
            number="MOO-100",
            building_code="MOO",
            floor="1",
            room="100",
            function="entrance",
        )
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def a_box(number: str = "KB-01", **overrides) -> KeyBox:
    settings = {
        "number": number,
        "building": "MOO",
        "opening": "MOO-100",
        "symbol": CHANGE_KEY,
        "seal": "44012",
        "sealed_on": SEALED,
    }
    settings.update(overrides)
    return KeyBox(**settings)


def a_seal(seal: str = "44012", **overrides) -> SealRow:
    settings = {
        "id": 0,
        "box_number": "KB-01",
        "seal": seal,
        "symbol": CHANGE_KEY,
        "sealed_on": SEALED,
        "broken_on": None,
        "broken_by": "",
        "note": "",
    }
    settings.update(overrides)
    return SealRow(**settings)


def test_a_box_goes_to_a_row_and_back():
    found = box_from_row(box_to_row(a_box()))
    assert found == a_box()


def test_an_open_box_keeps_who_opened_it():
    box = a_box(state=BoxState.OPENED, opened_on=WALKED, opened_by=OpenedBy.FIRE)
    assert box_from_row(box_to_row(box)).opened_by is OpenedBy.FIRE


def test_a_box_nobody_opened_has_no_opener():
    assert box_to_row(a_box()).opened_by == ""


def test_a_state_nobody_recognises_is_refused():
    row = box_to_row(a_box())
    row.state = "melted"
    with pytest.raises(InputError):
        box_from_row(row)


def test_an_opener_nobody_recognises_is_refused():
    row = box_to_row(a_box())
    row.opened_by = "the caretaker"
    with pytest.raises(InputError):
        box_from_row(row)


def test_a_row_moves_to_match_a_changed_box():
    row = box_to_row(a_box())
    changed = a_box(state=BoxState.OPENED, opened_on=WALKED, opened_by=OpenedBy.FIRE)
    apply_to_row(row, changed)
    assert row.state == "opened"
    assert row.opened_by == "fire service"


def test_a_row_that_was_resealed_forgets_the_opening():
    row = box_to_row(a_box(state=BoxState.OPENED, opened_on=WALKED, opened_by=OpenedBy.FIRE))
    apply_to_row(row, a_box(seal="44120", sealed_on=LATE))
    assert row.opened_on is None
    assert row.opened_by == ""


def test_the_seal_row_starts_open():
    assert seal_to_row(a_box()).is_current


def test_closing_a_seal_writes_the_day_it_came_off():
    row = close_seal(a_seal(), WALKED, "fire service")
    assert row.broken_on == WALKED
    assert not row.is_current


def test_a_seal_cannot_be_closed_twice():
    row = close_seal(a_seal(), WALKED)
    with pytest.raises(InputError):
        close_seal(row, LATE)


def test_a_seal_cannot_come_off_before_it_went_on():
    with pytest.raises(InputError):
        close_seal(a_seal(), date(2024, 1, 1))


def test_a_closing_note_replaces_the_old_one():
    row = close_seal(a_seal(note="original"), WALKED, "fire", "kitchen fire")
    assert row.note == "kitchen fire"


def test_the_current_seal_is_the_one_with_no_end():
    rows = [a_seal("44012", broken_on=WALKED), a_seal("44120", sealed_on=WALKED)]
    assert current_seal(rows).seal == "44120"


def test_a_box_with_every_seal_closed_has_no_current_one():
    assert current_seal([a_seal(broken_on=WALKED)]) is None


def test_the_history_is_oldest_first():
    rows = [a_seal("44120", sealed_on=WALKED), a_seal("44012", broken_on=WALKED)]
    assert [row.seal for row in seal_history(rows)] == ["44012", "44120"]


def test_the_seal_on_a_day_is_the_one_that_was_on_it():
    rows = [
        a_seal("44012", broken_on=WALKED),
        a_seal("44120", sealed_on=WALKED),
    ]
    assert seal_on(rows, date(2025, 3, 1)).seal == "44012"


def test_a_day_before_any_seal_has_none():
    assert seal_on([a_seal()], date(2024, 1, 1)) is None


def test_the_open_seal_covers_every_day_after_it_went_on():
    assert seal_on([a_seal()], LATE).seal == "44012"


def test_which_key_was_in_the_box_on_a_day():
    rows = [
        a_seal("44012", broken_on=WALKED),
        a_seal("44120", symbol=KeySymbol.parse("AA2"), sealed_on=WALKED),
    ]
    assert symbol_on(rows, LATE).render() == "AA2"


def test_no_seal_on_a_day_means_no_key():
    assert symbol_on([a_seal()], date(2024, 1, 1)) is None


def test_a_days_held_count_runs_to_today_while_it_is_on():
    assert a_seal().days_held(date(2025, 1, 16)) == 10


def test_a_closed_seal_counts_to_the_day_it_came_off():
    assert a_seal(broken_on=date(2025, 1, 16)).days_held(LATE) == 10


def test_the_register_comes_back_out_of_rows():
    rows = [box_to_row(a_box()), box_to_row(a_box("KB-02", opening="MOO-101"))]
    register = register_from_rows(rows)
    assert len(register) == 2


def test_the_register_keeps_a_box_in_the_state_it_reached():
    row = box_to_row(a_box(state=BoxState.EMPTY, seal=""))
    assert register_from_rows([row]).box("KB-01").state is BoxState.EMPTY


def test_the_register_takes_the_policy_it_is_given():
    register = register_from_rows([], BoxPolicy(lowest_level=1))
    assert register.policy.lowest_level == 1


def test_a_box_row_goes_in_and_comes_out(session):
    session.add(box_to_row(a_box()))
    session.flush()
    row = session.get(KeyBoxRow, "KB-01")
    assert row.symbol.render() == "AA1"
    assert row.is_live


def test_a_state_the_table_does_not_know_is_refused(session):
    row = box_to_row(a_box())
    row.state = "melted"
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_sealed_row_without_a_seal_is_refused(session):
    row = box_to_row(a_box())
    row.seal = ""
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_an_open_row_without_a_day_is_refused(session):
    row = box_to_row(a_box())
    row.state = "opened"
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_check_before_the_seal_went_on_is_refused(session):
    row = box_to_row(a_box())
    row.checked_on = date(2024, 1, 1)
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_two_boxes_cannot_sit_on_one_opening(session):
    session.add(box_to_row(a_box()))
    session.flush()
    session.add(box_to_row(a_box("KB-02")))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_seal_hangs_off_its_box(session):
    session.add(box_to_row(a_box()))
    session.flush()
    session.add(seal_to_row(a_box()))
    session.flush()
    row = session.get(KeyBoxRow, "KB-01")
    assert [item.seal for item in row.seals] == ["44012"]


def test_deleting_a_box_takes_its_seals_with_it(session):
    session.add(box_to_row(a_box()))
    session.flush()
    session.add(seal_to_row(a_box()))
    session.flush()
    session.delete(session.get(KeyBoxRow, "KB-01"))
    session.flush()
    from sqlalchemy import select

    assert session.execute(select(SealRow)).scalars().all() == []


def test_a_seal_broken_before_it_went_on_is_refused(session):
    session.add(box_to_row(a_box()))
    session.flush()
    row = seal_to_row(a_box())
    row.broken_on = date(2024, 1, 1)
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()

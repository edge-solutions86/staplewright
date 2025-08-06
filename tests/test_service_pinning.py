import pytest

import dbcampus
from charts import six_pin_chart
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import CapacityError, ConflictError, NotFoundError
from hasplib.pinning.segments import DriverProfile, SegmentKind, standard_kit
from hasplib.pinning.stack import PinningSpec
from hasplib.services.pinning import PinningService


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> PinningService:
    return PinningService(session, PinningSpec(six_pin_chart(), 150, 560))


def a_kit():
    return standard_kit(
        "bench",
        bottom_lengths=[150 + 15 * step for step in range(10)],
        wafer_lengths=[15 * step for step in range(1, 10)],
        driver_lengths=[100 + 5 * step for step in range(80)],
        count=40,
    )


def test_the_cuts_behind_a_group_come_back_in_order(session):
    service = a_service(session)
    found = service.bittings_for("chester park", "moore offices")
    assert [cut.render() for cut in found] == ["442446", "642446", "622446", "620446"]


def test_a_group_with_an_uncut_symbol_is_refused(session):
    from sqlalchemy import select

    from hasplib.persistence.models import ScheduleEntryRow

    row = session.execute(
        select(ScheduleEntryRow).where(ScheduleEntryRow.symbol == "AA1")
    ).scalar_one()
    row.bitting = None
    session.flush()
    service = a_service(session)
    with pytest.raises(ConflictError) as caught:
        service.bittings_for("chester park", "moore offices")
    assert caught.value.facts["symbol"] == "AA1"


def test_a_group_nobody_defined(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.bittings_for("chester park", "nothing at all")


def test_a_system_nobody_opened(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.bittings_for("nowhere", "moore offices")


def test_pinning_a_group_gives_a_stack_a_chamber(session):
    pinning = a_service(session).pin("chester park", "moore offices")
    assert pinning.chambers == 6
    assert pinning.wafer_count > 0


def test_the_wafers_land_where_the_keys_differ(session):
    pinning = a_service(session).pin("chester park", "moore offices")
    assert pinning.mastered_chambers == (1, 2, 3)


def test_a_group_of_one_key_needs_no_wafers(session):
    pinning = a_service(session).pin("chester park", "moore entrance")
    assert pinning.wafer_count == 1


def test_spooling_reaches_every_driver(session):
    pinning = a_service(session).pin("chester park", "moore offices", DriverProfile.SPOOL)
    drivers = [part for part in pinning.parts() if part.kind is SegmentKind.DRIVER]
    assert all(part.profile is DriverProfile.SPOOL for part in drivers)


def test_the_chart_carries_the_group_and_the_serial(session):
    lines = a_service(session).chart_for("chester park", "moore offices", "C-1001")
    assert lines[0] == "moore offices  C-1001"
    assert len(lines) == 6 + 6


def test_a_chart_without_a_serial_still_names_the_group(session):
    lines = a_service(session).chart_for("chester park", "moore offices")
    assert lines[0] == "moore offices"


def test_drawing_takes_the_brass_out_of_the_kit(session):
    kit = a_kit()
    before = kit.total()
    pinning, summary = a_service(session).draw_from(kit, "chester park", "moore offices")
    assert kit.total() == before - pinning.piece_count
    assert summary


def test_a_kit_that_cannot_fill_it_takes_nothing(session):
    kit = standard_kit("thin", [195], [30], [285], count=1)
    before = kit.total()
    with pytest.raises(CapacityError):
        a_service(session).draw_from(kit, "chester park", "moore offices")
    assert kit.total() == before


def test_the_cylinders_in_a_group_come_back(session):
    found = a_service(session).cylinders_for("moore offices")
    assert [row.serial for row in found] == ["C-1001", "C-1002"]


def test_a_group_with_no_cylinders_comes_back_empty(session):
    assert a_service(session).cylinders_for("shops") != []


def test_the_symbols_come_back_in_schedule_order(session):
    found = a_service(session).symbols_for("moore offices")
    assert [symbol.render() for symbol in found] == ["GGM", "A", "AA", "AA1"]

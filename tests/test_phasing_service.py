from datetime import date

import pytest
from click.testing import CliRunner

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.openings import Function, Opening
from hasplib.cli.main import cli
from hasplib.errors import NotFoundError
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.rekey.phasing import Restriction
from hasplib.services.phasing import (
    PhasingService,
    academic_year,
    buildings_with_work,
    door_for,
    orders_to_plan,
    restriction_for,
)

YEAR = 2025


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> PhasingService:
    return PhasingService(session, academic_year(YEAR))


def an_opening(function: Function) -> Opening:
    return Opening("MOO-101", "MOO", "1", "101", function)


def test_a_classroom_cannot_be_touched_in_teaching_time():
    assert restriction_for(an_opening(Function.CLASSROOM)) is Restriction.TEACHING


def test_an_office_counts_as_occupied():
    assert restriction_for(an_opening(Function.OFFICE)) is Restriction.OCCUPIED


def test_a_store_room_stops_nothing():
    assert restriction_for(an_opening(Function.STOREROOM)) is Restriction.NONE


def test_an_entrance_stops_nothing():
    assert restriction_for(an_opening(Function.ENTRANCE)) is Restriction.NONE


def test_a_door_carries_its_building_and_its_restriction():
    found = door_for(an_opening(Function.CLASSROOM))
    assert found.number == "MOO-101"
    assert found.building == "MOO"
    assert found.restriction is Restriction.TEACHING


def test_a_door_takes_the_standard_minutes():
    assert door_for(an_opening(Function.OFFICE)).minutes == 25


def test_the_academic_year_has_four_windows():
    assert len(academic_year(YEAR)) == 4


def test_the_windows_run_in_order():
    assert [window.name for window in academic_year(YEAR)] == [
        "summer",
        "reading week",
        "winter shutdown",
        "spring break",
    ]


def test_only_the_summer_takes_research_work():
    calendar = academic_year(YEAR)
    found = calendar.windows_for(Restriction.RESEARCH)
    assert [window.name for window in found] == ["summer"]


def test_the_reading_week_runs_at_half_rate():
    assert academic_year(YEAR, rate=12).window("reading week").doors_per_day == 6


def test_a_rate_of_one_still_gets_a_reading_week():
    assert academic_year(YEAR, rate=1).window("reading week").doors_per_day == 1


def test_the_winter_window_crosses_the_new_year():
    window = academic_year(YEAR).window("winter shutdown")
    assert window.opens_on.year == YEAR
    assert window.closes_on.year == YEAR + 1


def test_the_doors_on_a_building_come_back_in_number_order(session):
    found = a_service(session).doors_in("MOO")
    assert [door.number for door in found] == [
        "MOO-101",
        "MOO-110",
        "MOO-214",
        "MOO-216",
        "MOO-218",
    ]


def test_a_door_that_takes_no_cylinder_is_left_out(session):
    found = a_service(session).doors_in("MOO")
    assert "MOO-260" not in [door.number for door in found]


def test_a_building_with_no_keyed_doors_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).doors_in("ZZZ")


def test_every_keyed_door_on_the_campus_comes_back(session):
    found = a_service(session).every_keyed_door()
    assert len(found) == 10


def test_a_work_order_nobody_raised_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).doors_on("W-9999")


def test_the_plan_places_what_it_can(session):
    found = a_service(session).plan_campus()
    assert found.landed
    assert found.door_count == 10


def test_the_office_doors_land_in_a_window_that_takes_them(session):
    found = a_service(session).plan_campus()
    for item in found.placed:
        if item.door.restriction is Restriction.OCCUPIED:
            assert item.window in {"summer", "winter shutdown"}


def test_a_campus_bigger_than_the_year_carries_over(session):
    service = PhasingService(session, academic_year(YEAR, rate=1))
    doors = service.every_keyed_door()
    wanted, room = service.capacity_against(doors)
    assert wanted == 10
    assert room > 0


def test_the_plan_for_one_building_only_covers_it(session):
    found = a_service(session).plan_building("VOS")
    assert {item.door.building for item in found.placed} == {"VOS"}


def test_the_sheet_leads_with_the_totals(session):
    service = a_service(session)
    lines = service.sheet(service.doors_in("VOS"))
    assert lines[0] == "3 doors across 4 windows"


def test_the_day_sheet_names_what_is_booked(session):
    service = a_service(session)
    doors = service.doors_in("VOS")
    day = service.plan(doors).placed[0].on
    lines = service.day_sheet(doors, day)
    assert lines[0].endswith(day.isoformat())


def test_a_day_with_nothing_on_it_says_so(session):
    service = a_service(session)
    lines = service.day_sheet(service.doors_in("VOS"), date(2025, 2, 2))
    assert lines[0] == "nothing booked for 2025-02-02"


def test_the_buildings_with_work_are_listed(session):
    assert buildings_with_work(session) == ("HEI", "MOO", "VOS")


def test_the_open_orders_are_listed(session):
    assert orders_to_plan(session) == ()


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    made = build_sessions(engine)()
    dbcampus.build(made)
    made.commit()
    made.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def test_the_windows_command_lists_the_year(shop):
    lines = call(shop, "phasing", "windows", "--year", "2025").output.splitlines()
    assert lines[0].startswith("summer 2025-06-01")
    assert lines[-1].endswith("doors in the year")


def test_a_different_rate_changes_the_capacity(shop):
    slow = call(shop, "phasing", "windows", "--year", "2025", "--rate", "1").output
    fast = call(shop, "phasing", "windows", "--year", "2025", "--rate", "20").output
    assert slow != fast


def test_a_rate_of_nothing_is_rejected_by_click(shop):
    assert call(shop, "phasing", "windows", "--rate", "0").exit_code != 0


def test_the_buildings_command_lists_them(shop):
    assert call(shop, "phasing", "buildings").output.splitlines() == ["HEI", "MOO", "VOS"]


def test_the_orders_command_says_when_there_is_nothing(shop):
    assert "nothing open to plan" in call(shop, "phasing", "orders").output


def test_the_building_plan_prints(shop):
    lines = call(shop, "phasing", "building", "VOS", "--year", "2025").output.splitlines()
    assert lines[0] == "3 doors across 4 windows"


def test_a_building_nobody_has_is_refused(shop):
    assert call(shop, "phasing", "building", "ZZZ").exit_code != 0


def test_the_campus_plan_leads_with_the_capacity(shop):
    lines = call(shop, "phasing", "campus", "--year", "2025").output.splitlines()
    assert lines[0] == "10 doors against 1302 the year can take"


def test_a_campus_bigger_than_the_year_says_what_does_not_fit(shop):
    output = call(shop, "phasing", "campus", "--year", "2025", "--rate", "1").output
    assert "doors do not fit" in output or "everything fits" in output


def test_the_day_command_says_when_nothing_is_booked(shop):
    output = call(shop, "phasing", "day", "2025-02-02", "--year", "2025").output
    assert "nothing booked for 2025-02-02" in output


def test_the_carry_over_is_empty_on_a_year_that_fits(shop):
    assert "nothing carries over" in call(shop, "phasing", "carry-over", "--year", "2025").output


def test_an_order_nobody_raised_is_refused(shop):
    assert call(shop, "phasing", "order", "W-9999").exit_code != 0

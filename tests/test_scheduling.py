import pytest

from campus import a_campus
from hasplib.errors import ConflictError, InputError
from hasplib.rekey.costing import RateCard
from hasplib.rekey.orders import LineState, OrderLine, WorkOrder
from hasplib.rekey.scheduling import fits_in, plan_day, route, split_day

RATES = RateCard()


def an_order(*pairs, **overrides) -> WorkOrder:
    doors = pairs or (("C-1", "MOO-214"), ("C-2", "MOO-216"))
    settings = {
        "number": "W-1041",
        "incident": "I-1",
        "lines": tuple(OrderLine(serial, opening) for serial, opening in doors),
        "technician": "jkoivu",
    }
    settings.update(overrides)
    return WorkOrder(**settings)


def a_big_order() -> WorkOrder:
    doors = (
        ("C-1", "VOS-311"),
        ("C-2", "MOO-214"),
        ("C-3", "HEI-001"),
        ("C-4", "MOO-101"),
        ("C-5", "VOS-312"),
    )
    return an_order(*doors)


def test_the_route_walks_building_then_floor_then_door():
    stops = route(a_big_order().lines, a_campus(), RATES)
    assert [stop.opening for stop in stops] == [
        "HEI-001",
        "MOO-101",
        "MOO-214",
        "VOS-311",
        "VOS-312",
    ]


def test_a_stop_carries_where_it_is_and_how_long_it_takes():
    stop = route(an_order().lines, a_campus(), RATES)[0]
    assert stop.building == "MOO"
    assert stop.minutes == RATES.door_minutes_per_cylinder
    assert "MOO-214" in stop.describe()


def test_a_day_adds_up_bench_door_and_travel():
    plan = plan_day(an_order(), a_campus(), RATES)
    assert plan.bench_minutes == RATES.bench_minutes_per_cylinder * 2
    assert plan.door_minutes == RATES.door_minutes_per_cylinder * 2
    assert plan.travel_minutes == RATES.travel_minutes_per_building
    assert plan.total_minutes == plan.bench_minutes + plan.door_minutes + plan.travel_minutes


def test_a_day_across_three_buildings_pays_three_trips():
    plan = plan_day(a_big_order(), a_campus(), RATES)
    assert plan.buildings == ("HEI", "MOO", "VOS")
    assert plan.travel_minutes == RATES.travel_minutes_per_building * 3


def test_a_day_is_planned_for_somebody():
    with pytest.raises(ConflictError):
        plan_day(an_order(technician=""), a_campus(), RATES)


def test_a_technician_can_be_named_over_the_order():
    plan = plan_day(an_order(), a_campus(), RATES, technician="phamm")
    assert plan.technician == "phamm"


def test_an_order_with_nothing_left_plans_no_day():
    lines = (
        OrderLine("C-1", "MOO-214", LineState.INSTALLED),
        OrderLine("C-2", "MOO-216", LineState.SKIPPED),
    )
    with pytest.raises(ConflictError):
        plan_day(an_order(lines=lines), a_campus(), RATES)


def test_a_settled_cylinder_is_not_on_the_route():
    lines = (
        OrderLine("C-1", "MOO-214", LineState.INSTALLED),
        OrderLine("C-2", "MOO-216"),
    )
    plan = plan_day(an_order(lines=lines), a_campus(), RATES)
    assert [stop.serial for stop in plan.stops] == ["C-2"]


def test_the_plan_prints_a_line_a_door():
    plan = plan_day(an_order(), a_campus(), RATES)
    lines = plan.lines()
    assert lines[0].startswith("jkoivu: 2 doors")
    assert len(lines) == 3


def test_a_short_day_fits_and_a_long_one_does_not():
    plan = plan_day(a_big_order(), a_campus(), RATES)
    assert fits_in(plan, plan.total_minutes)
    assert not fits_in(plan, plan.total_minutes - 1)


def test_a_negative_day_is_refused():
    plan = plan_day(an_order(), a_campus(), RATES)
    with pytest.raises(InputError):
        fits_in(plan, -1)


def test_a_day_that_fits_is_not_split():
    plan = plan_day(an_order(), a_campus(), RATES)
    first, second = split_day(plan, plan.total_minutes)
    assert first is plan
    assert second is None


def test_a_long_day_splits_on_a_building_boundary():
    plan = plan_day(a_big_order(), a_campus(), RATES)
    first, second = split_day(plan, 120)
    assert second is not None
    assert not set(first.buildings) & set(second.buildings)


def test_a_split_keeps_every_door():
    plan = plan_day(a_big_order(), a_campus(), RATES)
    first, second = split_day(plan, 120)
    assert len(first.stops) + len(second.stops) == len(plan.stops)


def test_a_split_that_cannot_fit_one_building_takes_it_anyway():
    plan = plan_day(a_big_order(), a_campus(), RATES)
    first, second = split_day(plan, 1)
    assert first.stops
    assert second is not None


def test_a_day_of_nothing_cannot_be_split():
    plan = plan_day(an_order(), a_campus(), RATES)
    with pytest.raises(InputError):
        split_day(plan, 0)

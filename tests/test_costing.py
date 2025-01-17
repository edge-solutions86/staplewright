from datetime import date

import pytest

from campus import a_campus
from hasplib.campus.coverage import CoverageMap
from hasplib.campus.hardware import Cylinder, CylinderFormat, HardwareRegister, Position
from hasplib.errors import InputError
from hasplib.rekey.compromise import Cause, Incident, assess
from hasplib.rekey.costing import DEFAULT_RATES, RateCard, cheaper_of, compare, estimate
from hasplib.rekey.scope import decide
from keyed import a_coverage, a_ledger, keying_groups

WHEN = date(2025, 4, 8)


def decision_for(symbol: str, tag: str, coverage: CoverageMap | None = None):
    coverage = coverage or a_coverage()
    incident = Incident("I-1", tag, symbol, Cause.LOST, WHEN)
    return decide(assess(incident, coverage, a_ledger()), coverage), coverage


def a_core_coverage() -> CoverageMap:
    register = HardwareRegister(a_campus())
    for serial, opening in [("K-1", "MOO-214"), ("K-2", "MOO-216")]:
        register.add(Cylinder(serial, CylinderFormat.SFIC, "A2", 7))
        register.install(serial, opening, Position.LOCKSET)
        register.assign_group(serial, "moore offices")
    return CoverageMap(register, keying_groups())


def test_a_small_rekey_costs_bench_time_door_time_and_one_trip():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, ["41208"])
    assert price.cylinders == 2
    assert price.keys == 1
    assert price.buildings == 1
    assert price.bench_minutes == 12 * 2 + 3
    assert price.door_minutes == 20 * 2
    assert price.travel_minutes == 25


def test_the_totals_add_up_to_the_parts():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, ["41208"])
    assert price.total_minutes == price.bench_minutes + price.door_minutes + price.travel_minutes
    assert price.total_cents == price.labour_cents + price.parts_cents


def test_labour_is_priced_off_the_minutes():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, ["41208"])
    assert price.labour_cents == round(price.total_minutes * 5200 / 60)


def test_parts_are_blanks_plus_pins():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, ["41208", "41209"])
    assert price.parts_cents == 180 * 2 + 12 * 12 * 2


def test_a_bigger_scope_costs_more():
    small, coverage = decision_for("AA1", "41207")
    large, coverage = decision_for("GGM", "41211", coverage)
    assert estimate(large, coverage, []).total_cents > estimate(small, coverage, []).total_cents


def test_two_buildings_means_two_trips():
    found, coverage = decision_for("GGM", "41211")
    price = estimate(found, coverage, [])
    assert price.buildings == 2
    assert price.travel_minutes == 50


def test_cores_are_quick_at_the_door_and_the_same_at_the_bench():
    coverage = a_core_coverage()
    found, coverage = decision_for("AA1", "41207", coverage)
    price = estimate(found, coverage, [])
    assert price.cores == 2
    assert price.door_minutes == 2 * 2
    assert price.bench_minutes == 12 * 2


def test_cutting_no_keys_costs_no_blanks():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, [])
    assert price.parts_cents == 12 * 12 * 2
    assert price.keys == 0


def test_an_empty_decision_costs_nothing_but_still_prices():
    found, coverage = decision_for("ZZ9", "41207")
    price = estimate(found, coverage, [])
    assert price.cylinders == 0
    assert price.total_cents == 0
    assert price.hours == 0.0


def test_the_summary_names_the_two_numbers_that_matter():
    found, coverage = decision_for("AA1", "41207")
    summary = estimate(found, coverage, ["41208"]).summary()
    assert summary.startswith("2 cylinders and 1 keys")


def test_hours_are_rounded_for_the_memo():
    found, coverage = decision_for("AA1", "41207")
    price = estimate(found, coverage, ["41208"])
    assert price.hours == round(price.total_minutes / 60, 2)


def test_a_different_rate_card_moves_the_number():
    found, coverage = decision_for("AA1", "41207")
    cheap = estimate(found, coverage, ["41208"], RateCard(labour_cents_per_hour=2600))
    dear = estimate(found, coverage, ["41208"], DEFAULT_RATES)
    assert cheap.labour_cents < dear.labour_cents
    assert cheap.parts_cents == dear.parts_cents


def test_comparing_two_options_gives_the_saving():
    found, coverage = decision_for("AA1", "41207")
    dear = estimate(found, coverage, ["41208"])
    cheap = estimate(found, coverage, [])
    assert compare(dear, cheap) > 0


def test_the_cheapest_option_is_named():
    mortise, mortise_coverage = decision_for("AA1", "41207")
    cores, core_coverage = decision_for("AA1", "41207", a_core_coverage())
    options = [
        ("repin in place", estimate(mortise, mortise_coverage, ["41208"])),
        ("swap cores", estimate(cores, core_coverage, [])),
    ]
    assert cheaper_of(options) == "swap cores"


def test_comparing_nothing_is_refused():
    with pytest.raises(InputError):
        cheaper_of([])


@pytest.mark.parametrize(
    "broken",
    [{"bench_minutes_per_cylinder": -1}, {"labour_cents_per_hour": 0}, {"blank_cents": -5}],
)
def test_a_rate_card_that_makes_no_sense_is_refused(broken):
    with pytest.raises(InputError):
        RateCard(**broken)

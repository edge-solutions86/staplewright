from datetime import date, datetime

from campus import a_campus
from hasplib.campus.lockouts import Lockout, LockoutLog
from hasplib.control.deposits import DepositBook
from hasplib.reports.statistics import (
    campus_numbers,
    exposure,
    incidents_by_year,
    keys_by_building,
    keys_by_department,
    lockout_pressure,
    summary,
)
from keyed import a_coverage, a_ledger
from people import TODAY

CALLED = datetime(2025, 4, 8, 14, 12)


def a_book() -> DepositBook:
    book = DepositBook()
    book.charge("41207", "kmaki", 2500, TODAY)
    book.charge("41209", "rvirta", 5000, TODAY)
    return book


def a_log() -> LockoutLog:
    log = LockoutLog()
    log.log(Lockout("L-001", "MOO-214", "kmaki", CALLED))
    log.log(Lockout("L-002", "MOO-214", "dsalo", CALLED))
    log.log(Lockout("L-003", "MOO-216", "kmaki", CALLED))
    return log


def test_the_campus_counts_itself_once():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger())
    assert found.buildings == 3
    assert found.openings == 11
    assert found.keyed_openings == 10
    assert found.cylinders_installed == 6
    assert found.cylinders_shelved == 1


def test_the_keys_out_and_gone_are_counted():
    ledger = a_ledger()
    ledger.report_lost("41207", TODAY)
    found = campus_numbers(a_campus(), a_coverage(), ledger)
    assert found.keys_out == 4
    assert found.keys_gone == 1


def test_the_deposits_come_off_the_book_when_one_is_given():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger(), a_book())
    assert found.deposits_held_cents == 7500


def test_without_a_book_the_deposits_come_off_the_ledger():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger())
    assert found.deposits_held_cents == 0


def test_the_uncovered_openings_fall_out_of_the_counts():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger())
    assert found.uncovered == 4


def test_the_summary_reads_as_lines():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger())
    lines = summary(found, "chester park, 2025")
    assert lines[0] == "chester park, 2025"
    assert any("buildings" in line for line in lines)
    assert any("nothing in them" in line for line in lines)


def test_a_campus_with_nothing_uncovered_says_nothing_about_it():
    found = campus_numbers(a_campus(), a_coverage(), a_ledger())
    tidy = type(found)(**{**found.__dict__, "cylinders_installed": found.keyed_openings})
    assert not any("nothing in them" in line for line in summary(tidy))


def test_a_key_reaching_two_buildings_counts_in_both():
    counts = keys_by_building(a_coverage(), a_ledger())
    assert counts["MOO"] >= counts["HEI"]
    assert counts["HEI"] == 1


def test_keys_are_counted_by_department():
    counts = keys_by_department(a_ledger())
    assert counts["facilities"] == 2
    assert counts["geography"] == 3


def test_a_returned_key_is_not_counted_anywhere():
    ledger = a_ledger()
    ledger.take_back("41211", TODAY)
    counts = keys_by_building(a_coverage(), ledger)
    assert "HEI" not in counts


def test_incidents_are_counted_by_year():
    found = incidents_by_year([date(2024, 5, 1), date(2025, 1, 4), date(2025, 9, 9)])
    assert found == {2024: 1, 2025: 2}


def test_no_incidents_count_as_nothing():
    assert incidents_by_year([]) == {}


def test_the_lockout_pressure_names_the_room():
    found = lockout_pressure(a_log(), a_campus())
    assert found[0] == ("MOO 214", 2)


def test_a_door_nobody_has_a_record_of_keeps_its_number():
    log = LockoutLog()
    log.log(Lockout("L-009", "ZZZ-1", "kmaki", CALLED))
    assert lockout_pressure(log, a_campus())[0][0] == "ZZZ-1"


def test_the_widest_key_out_comes_first():
    found = exposure(a_coverage(), a_ledger())
    assert found[0][0] == "GGM"
    assert found[0][1] == 6


def test_a_symbol_held_twice_is_only_counted_once():
    found = exposure(a_coverage(), a_ledger())
    symbols = [symbol for symbol, _ in found]
    assert len(symbols) == len(set(symbols))


def test_the_exposure_list_can_be_cut_short():
    assert len(exposure(a_coverage(), a_ledger(), limit=2)) == 2

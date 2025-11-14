from datetime import date

import pytest

from hasplib.control.issue import KeyLedger
from hasplib.errors import InputError
from hasplib.reports.annual import (
    Return,
    annual_return,
    concentration,
    is_master,
    master_table,
    masters_out,
    movement,
    returns_table,
    take,
    unaccounted_keys,
    unaccounted_table,
)
from keyed import a_coverage, a_ledger
from people import TODAY, a_directory

LATER = date(2025, 12, 1)


def a_dated_ledger() -> KeyLedger:
    """The same keys, but with a day they were due back."""
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY, due_on=date(2025, 6, 1))
    ledger.issue("41210", "A", "phamm", TODAY, due_on=date(2025, 6, 1))
    return ledger


def a_lost_ledger() -> KeyLedger:
    ledger = a_ledger()
    ledger.report_lost("41208", TODAY, "left it in a taxi")
    return ledger


def test_a_return_is_for_a_real_year():
    with pytest.raises(InputError):
        Return(1066, 1, 1, 1, 1)


def test_a_change_key_is_not_a_master():
    assert not is_master("AA1")


def test_a_master_is():
    assert is_master("AA")
    assert is_master("GGM")


def test_the_masters_out_come_back_shallowest_first():
    found = masters_out(a_ledger())
    assert [key.symbol for key in found] == ["GGM", "A", "AA"]


def test_a_ledger_with_no_masters_has_none_out():
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY)
    assert masters_out(ledger) == ()


def test_a_lost_key_is_unaccounted_for():
    assert [key.tag for key in unaccounted_keys(a_lost_ledger(), TODAY)] == ["41208"]


def test_a_key_still_in_date_is_accounted_for():
    assert unaccounted_keys(a_ledger(), TODAY) == ()


def test_a_key_out_past_its_day_is_unaccounted_for():
    found = unaccounted_keys(a_dated_ledger(), date(2030, 1, 1))
    assert [key.tag for key in found] == ["41207", "41210"]


def test_a_key_with_no_day_on_it_is_never_overdue():
    assert unaccounted_keys(a_ledger(), date(2030, 1, 1)) == ()


def test_a_lost_key_is_not_counted_twice():
    ledger = a_dated_ledger()
    ledger.report_lost("41207", TODAY, "left it in a taxi")
    found = unaccounted_keys(ledger, date(2030, 1, 1))
    assert len([key for key in found if key.tag == "41207"]) == 1


def test_the_return_counts_what_is_out():
    found = take(a_ledger(), a_directory(), TODAY)
    assert found.keys_out == 5
    assert found.masters_out == 3
    assert found.unaccounted == 0


def test_the_return_counts_the_people_holding_something():
    assert take(a_ledger(), a_directory(), TODAY).keyholders == 4


def test_the_keys_per_holder_is_the_average():
    found = take(a_ledger(), a_directory(), TODAY)
    assert found.keys_per_holder == 1.25


def test_a_shop_with_nobody_holding_anything_averages_nothing():
    assert Return(2025, 0, 0, 0, 0).keys_per_holder == 0.0


def test_a_return_reads_back_as_one_line():
    found = take(a_ledger(), a_directory(), TODAY)
    assert found.describe() == "2025: 5 keys out, 3 masters, 0 unaccounted"


def test_the_master_table_names_everybody_holding_one():
    lines = master_table(a_ledger())
    assert lines[0] == "3 master keys are out"
    assert any("phamm" in line for line in lines)


def test_the_master_table_can_say_how_far_a_key_reaches():
    lines = master_table(a_ledger(), a_coverage())
    assert lines[1].endswith("doors")
    assert any(line.rstrip().split()[-1].isdigit() for line in lines[2:])


def test_a_shop_with_no_masters_out_says_so():
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY)
    assert master_table(ledger) == ("no master keys are out",)


def test_the_unaccounted_table_says_why_each_one_is_on_it():
    lines = unaccounted_table(a_lost_ledger(), TODAY)
    assert lines[0] == "1 keys cannot be accounted for"
    assert "lost" in lines[1]


def test_an_overdue_key_says_overdue():
    lines = unaccounted_table(a_dated_ledger(), date(2030, 1, 1))
    assert all("overdue" in line for line in lines[1:])


def test_a_clean_year_says_everything_is_accounted_for():
    assert unaccounted_table(a_ledger(), TODAY) == ("every key out is accounted for",)


def test_the_concentration_is_most_keys_first():
    assert concentration(a_ledger())[0] == ("phamm", 2)


def test_the_concentration_can_be_cut_short():
    assert len(concentration(a_ledger(), limit=1)) == 1


def test_a_first_year_has_nothing_to_compare_against():
    found = take(a_ledger(), a_directory(), TODAY)
    assert movement(found, None)[0].startswith("no return was filed")


def test_the_movement_says_which_way_each_number_went():
    this_year = Return(2025, 200, 12, 4, 150)
    last_year = Return(2024, 180, 12, 1, 150)
    lines = movement(this_year, last_year)
    assert lines[0] == "against 2024"
    assert "up 20" in lines[1]
    assert "level 0" in lines[2]
    assert "up 3" in lines[3]


def test_a_number_that_fell_says_down():
    lines = movement(Return(2025, 100, 1, 1, 10), Return(2024, 120, 1, 1, 10))
    assert "down 20" in lines[1]


def test_the_return_reads_top_to_bottom():
    lines = annual_return(a_ledger(), a_directory(), TODAY, a_coverage())
    assert lines[0] == "key control return for 2025"
    assert any(line.startswith("3 master keys are out") for line in lines)
    assert any(line == "most keys held" for line in lines)


def test_the_return_carries_last_year_when_there_is_one():
    last = Return(2024, 4, 2, 0, 3)
    lines = annual_return(a_ledger(), a_directory(), TODAY, None, last)
    assert lines[-5] == "against 2024"


def test_no_returns_filed_says_so():
    assert returns_table([]) == ("no returns have been filed",)


def test_the_returns_table_is_oldest_first():
    lines = returns_table([Return(2025, 1, 1, 1, 1), Return(2023, 1, 1, 1, 1)])
    assert lines[0].startswith("year")
    assert lines[1].startswith("2023")


def test_the_returns_table_has_a_row_per_year():
    lines = returns_table([Return(2025, 1, 1, 1, 1), Return(2024, 1, 1, 1, 1)])
    assert len(lines) == 3

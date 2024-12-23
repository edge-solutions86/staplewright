import pytest

from hasplib.campus.coverage import (
    CoverageMap,
    keys_that_open,
    openings_a_tag_turns,
    people_who_can_open,
)
from hasplib.campus.hardware import Position
from hasplib.errors import NotFoundError
from hasplib.keys.symbol import KeySymbol
from keyed import a_coverage, a_ledger, a_register, keying_groups


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def test_a_change_key_opens_the_one_door_it_was_cut_for():
    assert a_coverage().openings_for(sym("AA1")) == ("MOO-214", "MOO-216")


def test_a_master_opens_everything_under_it_that_says_so():
    assert a_coverage().openings_for(sym("AA")) == ("MOO-214", "MOO-216", "MOO-218")


def test_the_top_key_opens_every_mapped_cylinder():
    coverage = a_coverage()
    assert len(coverage.openings_for(sym("GGM"))) == 6


def test_a_symbol_nobody_keyed_opens_nothing():
    assert a_coverage().openings_for(sym("ZZ9")) == ()


def test_a_door_reports_every_symbol_that_turns_it():
    symbols = a_coverage().symbols_opening("MOO-214")
    assert [s.render() for s in symbols] == ["GGM", "A", "AA", "AA1"]


def test_symbols_come_back_shallow_first():
    symbols = a_coverage().symbols_opening("MOO-218")
    assert [s.level for s in symbols] == sorted(s.level for s in symbols)


def test_a_door_with_no_cylinder_reports_nothing():
    assert a_coverage().symbols_opening("MOO-260") == ()


def test_several_symbols_at_once_come_back_as_one_list():
    coverage = a_coverage()
    both = coverage.openings_for_any([sym("AA1"), sym("BA1")])
    assert both == ("HEI-001", "MOO-214", "MOO-216")


def test_the_buildings_a_key_reaches():
    coverage = a_coverage()
    assert coverage.buildings_for(sym("AA1")) == ("MOO",)
    assert coverage.buildings_for(sym("GGM")) == ("HEI", "MOO")


def test_the_cylinders_that_would_have_to_come_apart():
    serials = [c.serial for c in a_coverage().cylinders_for(sym("AA"))]
    assert serials == ["C-1001", "C-1002", "C-1003"]


def test_a_shelved_cylinder_is_not_in_anybodys_reach():
    coverage = a_coverage()
    assert "C-1099" not in [c.serial for c in coverage.cylinders_for(sym("GGM"))]


def test_a_cylinder_with_no_group_is_reported_as_unmapped():
    register = a_register()
    register.install("C-1099", "VOS-311", Position.LOCKSET)
    coverage = CoverageMap(register, keying_groups())
    assert [c.serial for c in coverage.unmapped_cylinders()] == ["C-1099"]


def test_a_cylinder_with_a_group_nobody_defined_is_also_unmapped():
    register = a_register()
    register.assign_group("C-1001", "a group that does not exist")
    coverage = CoverageMap(register, keying_groups())
    assert [c.serial for c in coverage.unmapped_cylinders()] == ["C-1001"]


def test_a_door_with_nothing_in_the_lockset_is_reported():
    coverage = a_coverage()
    uncovered = dict(coverage.uncovered_positions())
    assert "VOS-311" in uncovered
    assert uncovered["VOS-311"] is Position.LOCKSET
    assert "MOO-214" not in uncovered


def test_asking_for_a_group_that_was_never_defined():
    with pytest.raises(NotFoundError):
        a_coverage().group("nothing like this")


def test_the_reach_of_a_key_is_the_count_of_its_doors():
    coverage = a_coverage()
    assert coverage.reach_of(sym("AA1")) == 2
    assert coverage.reach_of(sym("GGM")) == 6


def test_the_widest_keys_come_first():
    widest = a_coverage().widest_symbols(limit=3)
    assert widest[0][0] == sym("GGM")
    assert widest[0][1] == 6
    assert [count for _, count in widest] == sorted([count for _, count in widest], reverse=True)


def test_a_tag_turns_whatever_its_cut_turns():
    coverage = a_coverage()
    ledger = a_ledger()
    assert openings_a_tag_turns(coverage, ledger, "41207") == ("MOO-214", "MOO-216")
    assert len(openings_a_tag_turns(coverage, ledger, "41211")) == 6


def test_a_tag_that_was_never_issued():
    with pytest.raises(NotFoundError):
        openings_a_tag_turns(a_coverage(), a_ledger(), "99999")


def test_the_keys_out_on_a_symbol_come_back_by_tag():
    keys = keys_that_open(a_ledger(), [sym("AA1")])
    assert [key.tag for key in keys] == ["41207", "41208"]


def test_a_returned_key_is_not_out_on_anything():
    ledger = a_ledger()
    ledger.take_back("41207", ledger.key("41207").issued_on)
    assert [key.tag for key in keys_that_open(ledger, [sym("AA1")])] == ["41208"]


def test_everybody_who_can_open_one_door():
    holders = people_who_can_open(a_coverage(), a_ledger(), "MOO-214")
    assert holders == ("dsalo", "kmaki", "phamm", "rvirta")


def test_a_door_only_the_top_key_reaches():
    holders = people_who_can_open(a_coverage(), a_ledger(), "HEI-001")
    assert holders == ("phamm",)


def test_a_door_nobody_can_open():
    assert people_who_can_open(a_coverage(), a_ledger(), "MOO-260") == ()

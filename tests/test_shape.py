import pytest

from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.shape import (
    barren_masters,
    compare,
    level_table,
    only_children,
    orphans,
    shape_of,
    shape_report,
    wide_masters,
)


def symbols(*names: str) -> list[KeySymbol]:
    return [KeySymbol.parse(name) for name in names]


def a_small_system() -> list[KeySymbol]:
    return symbols("GGM", "A", "B", "AA", "AB", "AA1", "AA2", "AB1", "BA", "BA1")


def a_wide_system() -> list[KeySymbol]:
    return symbols("GGM", "A", *[f"A{n}" for n in range(1, 31)])


def test_nothing_to_measure_is_refused():
    with pytest.raises(InputError):
        shape_of([])


def test_the_symbol_count_is_what_went_in():
    assert shape_of(a_small_system()).symbols == 10


def test_the_depth_is_the_deepest_level():
    assert shape_of(a_small_system()).depth == 3


def test_the_top_key_sits_on_level_nothing():
    found = shape_of(a_small_system())
    assert found.levels[0].depth == 0
    assert found.levels[0].keys == 1


def test_a_level_separates_the_masters_from_the_change_keys():
    found = shape_of(a_small_system())
    third = [level for level in found.levels if level.depth == 3][0]
    assert third.masters == 0
    assert third.change_keys == 4


def test_a_level_reads_back_as_one_line():
    found = shape_of(a_small_system())
    assert found.levels[0].describe() == "level 0: 1 keys, 1 of them masters"


def test_the_fan_out_counts_what_hangs_off_each_master():
    found = shape_of(a_small_system())
    assert found.fan_out["GGM"] == 2
    assert found.fan_out["AA"] == 2


def test_the_top_key_hangs_off_nothing():
    assert "" not in shape_of(a_small_system()).fan_out


def test_the_widest_master_is_named_with_its_count():
    assert shape_of(a_wide_system()).widest == ("A", 30)


def test_a_system_with_nothing_under_anything_has_no_widest():
    found = shape_of(symbols("GGM"))
    assert found.widest == ("", 0)


def test_the_average_fan_out_is_the_mean():
    found = shape_of(symbols("GGM", "A", "B"))
    assert found.average_fan_out == 2.0


def test_the_median_fan_out_ignores_the_outlier():
    found = shape_of(symbols("GGM", "A", "B", "C", "A1", "A2", "A3", "B1", "C1"))
    assert found.median_fan_out == 2.0


def test_an_empty_system_averages_nothing():
    found = shape_of(symbols("GGM"))
    assert found.average_fan_out == 0.0
    assert found.median_fan_out == 0.0


def test_a_small_tree_is_never_called_lopsided():
    assert not shape_of(symbols("GGM", "A", "A1")).is_lopsided


def test_one_branch_carrying_everything_is_lopsided():
    found = shape_of(symbols("GGM", "A", "B", "C", "B1", "C1", *[f"A{n}" for n in range(1, 20)]))
    assert found.is_lopsided


def test_an_even_tree_is_not_lopsided():
    found = shape_of(symbols("GGM", "A", "B", "C", "A1", "A2", "B1", "B2", "C1", "C2"))
    assert not found.is_lopsided


def test_a_shape_reads_back_as_one_line():
    assert shape_of(a_small_system()).describe() == "10 symbols, 3 deep, widest master 2"


def test_a_wide_master_is_named():
    found = wide_masters(shape_of(a_wide_system()), over=25)
    assert found == (("A", 30),)


def test_nothing_wide_comes_back_empty():
    assert wide_masters(shape_of(a_small_system())) == ()


def test_a_lower_limit_catches_more():
    found = wide_masters(shape_of(a_small_system()), over=1)
    assert [name for name, _ in found] == ["A", "AA", "GGM"]


def test_a_limit_of_nothing_is_refused():
    with pytest.raises(InputError):
        wide_masters(shape_of(a_small_system()), over=0)


def test_a_master_with_one_key_under_it_is_named():
    found = only_children(shape_of(a_small_system()))
    assert found == ("AB", "B", "BA")


def test_a_master_with_two_is_not():
    assert "AA" not in only_children(shape_of(a_small_system()))


def test_a_master_with_nothing_under_it_is_named():
    assert barren_masters(symbols("GGM", "A", "B", "A1")) == ("B",)


def test_a_master_with_something_under_it_is_not():
    assert barren_masters(symbols("GGM", "A", "A1")) == ()


def test_the_top_key_is_never_barren():
    assert "GGM" not in barren_masters(symbols("GGM"))


def test_a_key_whose_master_is_missing_is_an_orphan():
    assert orphans(symbols("GGM", "AA1")) == ("AA",)


def test_a_full_tree_has_no_orphans():
    assert orphans(a_small_system()) == ()


def test_the_level_table_counts_the_ranks():
    lines = level_table(shape_of(a_small_system()))
    assert lines[0] == "10 symbols across 4 levels"
    assert len(lines) == 5


def test_the_report_leads_with_the_shape():
    lines = shape_report(a_small_system())
    assert lines[0] == "10 symbols, 3 deep, widest master 2"


def test_the_report_says_when_nothing_is_wide():
    lines = shape_report(a_small_system())
    assert any(line == "no master carries more than 25 keys" for line in lines)


def test_the_report_names_a_wide_master():
    lines = shape_report(a_wide_system())
    assert any(line.startswith("1 masters carry more than 25") for line in lines)


def test_the_report_names_the_thin_masters():
    lines = shape_report(a_small_system())
    assert any(line.startswith("3 masters have one key under them") for line in lines)


def test_the_report_warns_about_a_deep_tree():
    deep = symbols("GGM", "A", "AA", "AAA", "AAAA", "AAAA1")
    lines = shape_report(deep)
    assert any("wafer in every chamber" in line for line in lines)


def test_the_report_names_the_orphans():
    lines = shape_report(symbols("GGM", "AA1"))
    assert any(line.startswith("1 masters are referred to") for line in lines)


def test_two_systems_can_be_put_side_by_side():
    lines = compare(shape_of(a_small_system()), shape_of(a_wide_system()))
    assert lines[0].startswith("symbols")
    assert lines[1].split()[1:] == ["3", "2"]

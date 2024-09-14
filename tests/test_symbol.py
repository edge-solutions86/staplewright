import pytest

from hasplib.errors import HierarchyError
from hasplib.keys.symbol import KeySymbol, common_ancestor, sort_symbols


def test_the_top_key_has_its_own_spelling():
    top = KeySymbol.parse("GGM")
    assert top.is_top
    assert top.level == 0
    assert top.render() == "GGM"
    assert top.level_name == "great grand master"


def test_letters_alone_are_a_master_at_that_depth():
    assert KeySymbol.parse("A").level == 1
    assert KeySymbol.parse("AB").level == 2
    assert KeySymbol.parse("ABC").level == 3
    assert KeySymbol.parse("A").level_name == "grand master"
    assert KeySymbol.parse("AB").level_name == "master"
    assert KeySymbol.parse("ABC").level_name == "sub master"


def test_a_number_makes_it_a_change_key_one_level_down():
    symbol = KeySymbol.parse("AB3")
    assert symbol.is_change_key
    assert symbol.level == 3
    assert symbol.level_name == "change key"
    assert symbol.number == 3


def test_parsing_tidies_case_and_whitespace():
    assert KeySymbol.parse("  ab3 ") == KeySymbol("AB", 3)


def test_a_symbol_renders_back_to_what_was_read():
    for text in ["GGM", "A", "AB", "ABC", "AB3", "ABCD17"]:
        assert KeySymbol.parse(text).render() == text


def test_a_change_key_hangs_under_its_master():
    assert KeySymbol.parse("AB3").parent() == KeySymbol.parse("AB")


def test_a_master_hangs_under_the_one_with_a_letter_less():
    assert KeySymbol.parse("ABC").parent() == KeySymbol.parse("AB")
    assert KeySymbol.parse("A").parent() == KeySymbol.parse("GGM")


def test_the_top_key_has_nothing_above_it():
    assert KeySymbol.parse("GGM").parent() is None


def test_ancestry_climbs_all_the_way():
    line = [s.render() for s in KeySymbol.parse("AB3").ancestry()]
    assert line == ["AB3", "AB", "A", "GGM"]


def test_descent_reads_downward_only():
    change = KeySymbol.parse("AB3")
    assert change.descends_from(KeySymbol.parse("AB"))
    assert change.descends_from(KeySymbol.parse("A"))
    assert change.descends_from(KeySymbol.parse("GGM"))
    assert change.descends_from(change)
    assert not KeySymbol.parse("AB").descends_from(change)


def test_keys_on_different_branches_do_not_descend_from_each_other():
    assert not KeySymbol.parse("AB3").descends_from(KeySymbol.parse("AC"))
    assert not KeySymbol.parse("AB3").descends_from(KeySymbol.parse("B"))


def test_building_a_master_downward():
    assert KeySymbol.parse("A").child_master("b") == KeySymbol.parse("AB")
    assert KeySymbol.parse("GGM").child_master("A") == KeySymbol.parse("A")


def test_building_a_change_key_downward():
    assert KeySymbol.parse("AB").change_key(7) == KeySymbol.parse("AB7")


def test_nothing_hangs_off_a_change_key():
    change = KeySymbol.parse("AB3")
    with pytest.raises(HierarchyError):
        change.child_master("C")
    with pytest.raises(HierarchyError):
        change.change_key(1)


def test_change_keys_do_not_hang_off_the_top():
    with pytest.raises(HierarchyError):
        KeySymbol.parse("GGM").change_key(1)


def test_a_master_is_named_by_one_letter():
    with pytest.raises(HierarchyError):
        KeySymbol.parse("A").child_master("BC")
    with pytest.raises(HierarchyError):
        KeySymbol.parse("A").child_master("4")


@pytest.mark.parametrize("text", ["", "  ", "1A", "A-1", "ABCDE", "A0", "a b"])
def test_junk_is_not_a_symbol(text):
    with pytest.raises(HierarchyError):
        KeySymbol.parse(text)


def test_a_number_without_letters_is_refused():
    with pytest.raises(HierarchyError):
        KeySymbol("", 3)


def test_the_common_key_above_two_symbols():
    assert common_ancestor(KeySymbol.parse("AB3"), KeySymbol.parse("AB7")).render() == "AB"
    assert common_ancestor(KeySymbol.parse("AB3"), KeySymbol.parse("AC1")).render() == "A"
    assert common_ancestor(KeySymbol.parse("AB3"), KeySymbol.parse("BC1")).render() == "GGM"
    assert common_ancestor(KeySymbol.parse("AB3"), KeySymbol.parse("AB")).render() == "AB"


def test_schedule_order_is_shallow_first_then_alphabetical():
    order = sort_symbols(
        [
            KeySymbol.parse("AB2"),
            KeySymbol.parse("A"),
            KeySymbol.parse("AB10"),
            KeySymbol.parse("GGM"),
            KeySymbol.parse("AB"),
            KeySymbol.parse("AA"),
        ]
    )
    assert [s.render() for s in order] == ["GGM", "A", "AA", "AB", "AB2", "AB10"]

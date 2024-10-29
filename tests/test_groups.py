import pytest

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.groups import (
    KeyingGroup,
    bittings_for,
    cost_of,
    cylinder_section,
    sections_for,
    worst_groups,
)
from systems import small_schedule


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def group(name: str, *symbols: str) -> KeyingGroup:
    return KeyingGroup(name, tuple(sym(text) for text in symbols))


def a_cut_schedule():
    schedule = small_schedule()
    cuts = {
        "GGM": "442446",
        "A": "642446",
        "B": "242446",
        "AA": "622446",
        "AB": "662446",
        "AA1": "620446",
        "AA2": "624446",
        "AB1": "662046",
        "AB2": "662646",
    }
    for symbol, bitting in cuts.items():
        schedule.assign(sym(symbol), Bitting.parse(bitting))
    return schedule


def test_a_group_holds_the_symbols_it_was_given():
    keying = group("office 214", "AA1", "AA", "A", "GGM")
    assert keying.size == 4
    assert keying.holds(sym("AA"))
    assert not keying.holds(sym("AB"))


def test_a_group_orders_its_symbols_for_printing():
    keying = group("office 214", "AA1", "GGM", "A", "AA")
    assert [s.render() for s in keying.ordered()] == ["GGM", "A", "AA", "AA1"]


def test_an_empty_group_is_refused():
    with pytest.raises(ConflictError):
        KeyingGroup("nothing", ())


def test_a_symbol_listed_twice_is_refused():
    with pytest.raises(ConflictError):
        group("twice", "AA1", "AA1")


def test_the_top_symbol_is_the_one_everything_hangs_under():
    assert group("g", "AA1", "AA", "A").top_symbol() == sym("A")
    assert group("g", "AA1", "AB1").top_symbol() == sym("A")
    assert group("g", "AA1", "BB1").top_symbol() == sym("GGM")


def test_a_straight_line_of_descent_is_not_cross_keyed():
    assert not group("office", "AA1", "AA", "A", "GGM").is_cross_keyed


def test_one_key_alone_is_not_cross_keyed():
    assert not group("closet", "AA1").is_cross_keyed


def test_two_change_keys_under_one_master_is_cross_keying():
    assert group("shared lab", "AA1", "AA2", "AA").is_cross_keyed


def test_keys_on_different_branches_are_cross_keyed():
    assert group("shared lab", "AA1", "AB1").is_cross_keyed


def test_the_cuts_come_back_in_schedule_order():
    schedule = a_cut_schedule()
    cuts = bittings_for(schedule, group("office", "AA1", "GGM", "AA"))
    assert [b.render() for b in cuts] == ["442446", "622446", "620446"]


def test_a_key_that_has_not_been_cut_stops_the_group():
    schedule = small_schedule()
    with pytest.raises(NotFoundError) as caught:
        bittings_for(schedule, group("office", "AA1"))
    assert caught.value.facts["symbol"] == "AA1"


def test_the_sections_of_a_group_are_reported_once_each():
    schedule = a_cut_schedule()
    assert sections_for(schedule, group("office", "AA1", "AA", "A", "GGM")) == (
        "L",
        "LA",
        "LC",
    )


def test_the_cylinder_takes_the_section_every_key_reaches():
    schedule = a_cut_schedule()
    assert cylinder_section(schedule, group("office", "AA1", "AA", "GGM")) == "LC"


def test_a_group_no_single_section_covers_is_refused():
    schedule = a_cut_schedule()
    with pytest.raises(ConflictError):
        cylinder_section(schedule, group("odd", "AA1", "AB1"))


def test_one_key_alone_costs_nothing_and_leaks_nothing():
    schedule = a_cut_schedule()
    cost = cost_of(schedule, group("closet", "AA1"))
    assert cost.wafers == 0
    assert cost.operating == 1
    assert cost.incidental == 0
    assert cost.is_clean


def test_a_master_over_a_change_key_costs_one_wafer_a_split():
    schedule = a_cut_schedule()
    cost = cost_of(schedule, group("office", "AA1", "AA"))
    assert cost.keys == 2
    assert cost.wafers == 1
    assert cost.incidental == 0


def test_a_full_line_of_descent_leaks_keys():
    schedule = a_cut_schedule()
    cost = cost_of(schedule, group("office", "AA1", "AA", "A", "GGM"))
    assert cost.wafers == 3
    assert cost.operating == 8
    assert cost.incidental == 4


def test_cross_keying_two_branches_leaks_more_than_a_straight_line():
    schedule = a_cut_schedule()
    straight = cost_of(schedule, group("office", "AA1", "AA"))
    crossed = cost_of(schedule, group("lab", "AA1", "AB1"))
    assert crossed.incidental > straight.incidental


def test_the_worst_groups_come_back_first():
    schedule = a_cut_schedule()
    groups = [
        group("closet", "AA1"),
        group("office", "AA1", "AA"),
        group("lab", "AA1", "AB1", "GGM"),
    ]
    worst = worst_groups(schedule, groups)
    assert [cost.group for cost in worst] == ["lab", "office", "closet"]


def test_the_worst_list_can_be_cut_short():
    schedule = a_cut_schedule()
    groups = [group("closet", "AA1"), group("lab", "AA1", "AB1", "GGM")]
    assert len(worst_groups(schedule, groups, limit=1)) == 1

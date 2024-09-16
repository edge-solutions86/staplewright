import pytest

from hasplib.errors import ConflictError, HierarchyError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from systems import empty_schedule, small_schedule


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def test_a_new_schedule_holds_only_the_top_key():
    schedule = empty_schedule()
    assert len(schedule) == 1
    assert schedule.symbols() == (sym("GGM"),)
    assert schedule.entry(sym("GGM")).section == "L"


def test_symbols_come_back_in_schedule_order():
    order = [s.render() for s in small_schedule().symbols()]
    assert order == ["GGM", "A", "B", "AA", "AB", "AA1", "AA2", "AB1", "AB2"]


def test_a_key_inherits_the_section_above_it_unless_told_otherwise():
    schedule = small_schedule()
    assert schedule.entry(sym("AA1")).section == "LC"
    assert schedule.entry(sym("AB1")).section == "LD"


def test_a_section_the_key_above_cannot_enter_is_refused():
    schedule = empty_schedule()
    schedule.add(sym("A"), section="LA")
    with pytest.raises(HierarchyError) as caught:
        schedule.add(sym("AA"), section="LE")
    assert caught.value.facts["above"] == "LA"


def test_a_symbol_cannot_be_added_twice():
    schedule = small_schedule()
    with pytest.raises(ConflictError):
        schedule.add(sym("AA"))


def test_a_symbol_with_no_parent_in_the_schedule_is_refused():
    schedule = empty_schedule()
    with pytest.raises(HierarchyError) as caught:
        schedule.add(sym("AA1"))
    assert caught.value.facts["wanted"] == "AA"


def test_the_top_key_cannot_be_added_again():
    with pytest.raises(ConflictError):
        empty_schedule().add(sym("GGM"))


def test_asking_about_a_symbol_that_is_not_there():
    with pytest.raises(NotFoundError):
        empty_schedule().entry(sym("AA"))


def test_children_are_the_keys_directly_under_one():
    schedule = small_schedule()
    assert [s.render() for s in schedule.children(sym("A"))] == ["AA", "AB"]
    assert [s.render() for s in schedule.children(sym("AA"))] == ["AA1", "AA2"]
    assert schedule.children(sym("AA1")) == ()


def test_descendants_go_all_the_way_down():
    schedule = small_schedule()
    assert [s.render() for s in schedule.descendants(sym("A"))] == [
        "AA",
        "AB",
        "AA1",
        "AA2",
        "AB1",
        "AB2",
    ]
    assert len(schedule.descendants(sym("GGM"))) == 8


def test_masters_and_change_keys_are_told_apart():
    schedule = small_schedule()
    assert [s.render() for s in schedule.masters()] == ["GGM", "A", "B", "AA", "AB"]
    assert [s.render() for s in schedule.change_keys()] == ["AA1", "AA2", "AB1", "AB2"]


def test_a_drawn_schedule_starts_with_everything_open():
    schedule = small_schedule()
    assert len(schedule.open_symbols()) == len(schedule)
    assert schedule.cut_symbols() == ()


def test_cutting_a_symbol_moves_it_out_of_the_open_list():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    assert schedule.bitting_of(sym("AA1")) == Bitting.parse("342156")
    assert sym("AA1") in schedule.cut_symbols()
    assert sym("AA1") not in schedule.open_symbols()


def test_clearing_a_symbol_puts_it_back():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    schedule.clear(sym("AA1"))
    assert schedule.bitting_of(sym("AA1")) is None


def test_two_symbols_cannot_be_cut_the_same():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    with pytest.raises(ConflictError) as caught:
        schedule.assign(sym("AA2"), Bitting.parse("342156"))
    assert caught.value.facts["held_by"] == "AA1"


def test_recutting_a_symbol_to_what_it_already_is_is_allowed():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    assert schedule.symbol_cut_to(Bitting.parse("342156")) == sym("AA1")


def test_a_bitting_the_ruleset_refuses_never_reaches_the_schedule():
    schedule = small_schedule()
    with pytest.raises(ConflictError) as caught:
        schedule.assign(sym("AA1"), Bitting.parse("192156"))
    assert "macs" in caught.value.facts["reasons"]
    assert schedule.bitting_of(sym("AA1")) is None


def test_nobody_holds_a_bitting_that_was_never_cut():
    assert small_schedule().symbol_cut_to(Bitting.parse("342156")) is None


def test_a_schedule_reports_keys_that_policy_would_no_longer_cut():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("342159"))
    assert schedule.problems() == ()
    schedule.ruleset = type(schedule.ruleset)(
        schedule.ruleset.chart, type(schedule.ruleset.rules)(shallow_tip_limit=4)
    )
    problems = schedule.problems()
    assert [p.rule for p in problems] == ["tip"]
    assert problems[0].message.startswith("AA1:")


def test_an_entry_renders_for_the_printed_schedule():
    schedule = small_schedule()
    assert schedule.entry(sym("AA1")).render().split() == ["AA1", "LC", "open"]
    schedule.assign(sym("AA1"), Bitting.parse("342156"))
    assert schedule.entry(sym("AA1")).render().split() == ["AA1", "LC", "342156"]


def test_a_schedule_is_a_container_of_symbols():
    schedule = small_schedule()
    assert sym("AA1") in schedule
    assert sym("AC1") not in schedule
    assert "AA1" not in schedule
    assert len(list(schedule)) == len(schedule)

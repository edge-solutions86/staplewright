from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.reports.page import as_text
from hasplib.reports.schedule import (
    bitting_list,
    cross_reference,
    open_symbols_report,
    schedule_line,
    schedule_report,
    tree_report,
)
from systems import small_schedule

CUTS = {
    "GGM": "442446",
    "A": "642446",
    "AA": "622446",
    "AA1": "620446",
    "AA2": "624446",
}


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def a_cut_schedule():
    schedule = small_schedule()
    for symbol, bitting in CUTS.items():
        schedule.assign(sym(symbol), Bitting.parse(bitting))
    return schedule


def test_a_line_carries_the_symbol_the_section_and_the_cut():
    schedule = a_cut_schedule()
    line = schedule_line(schedule.entry(sym("AA1")))
    assert "AA1" in line
    assert "LC" in line
    assert "620446" in line


def test_an_uncut_line_says_open():
    schedule = a_cut_schedule()
    assert "open" in schedule_line(schedule.entry(sym("AB1")))


def test_a_line_is_indented_by_its_level():
    schedule = a_cut_schedule()
    top = schedule_line(schedule.entry(sym("GGM")))
    change = schedule_line(schedule.entry(sym("AA1")))
    assert not top.startswith(" ")
    assert change.startswith("      ")


def test_indentation_can_be_turned_off():
    schedule = a_cut_schedule()
    assert not schedule_line(schedule.entry(sym("AA1")), indent=False).startswith(" ")


def test_the_report_has_a_heading_and_a_line_a_symbol():
    schedule = a_cut_schedule()
    lines = schedule_report(schedule, "chester park")
    assert lines[0] == "chester park"
    assert lines[1].startswith("symbol")
    assert len(lines) == len(schedule) + 3


def test_the_report_counts_what_is_still_open():
    schedule = a_cut_schedule()
    assert lines_end(schedule_report(schedule)) == "9 symbols, 4 of them open"


def lines_end(lines):
    return lines[-1]


def test_a_report_without_a_title_starts_at_the_heading():
    assert schedule_report(a_cut_schedule())[0].startswith("symbol")


def test_the_bitting_list_is_sorted_by_cut():
    lines = bitting_list(a_cut_schedule())[1:]
    cuts = [line.split()[0] for line in lines]
    assert cuts == sorted(cuts)


def test_the_bitting_list_leaves_out_what_is_not_cut():
    lines = bitting_list(a_cut_schedule())
    assert len(lines) == len(CUTS) + 1
    assert all("open" not in line for line in lines)


def test_the_bitting_list_of_nothing_is_just_a_heading():
    assert len(bitting_list(small_schedule())) == 1


def test_the_open_report_names_what_is_owed():
    lines = open_symbols_report(a_cut_schedule())
    assert lines[0] == "4 symbols drawn and not cut"
    assert any("AB1" in line for line in lines)


def test_a_finished_schedule_says_so():
    schedule = small_schedule()
    for number, symbol in enumerate(schedule.symbols()):
        schedule.assign(symbol, Bitting((2, 4, 2, 4, 4, 6)).with_depth(1, number))
    assert open_symbols_report(schedule) == ("every symbol on this schedule is cut",)


def test_the_tree_report_counts_what_hangs_under_each_key():
    lines = tree_report(a_cut_schedule())
    assert lines[0].startswith("GGM")
    assert "8 under it" in lines[0]
    assert any(line.strip().startswith("AA1") and "leaf" in line for line in lines)


def test_the_cross_reference_lists_the_doors():
    schedule = a_cut_schedule()
    doors = {sym("AA1"): ["MOO-214", "MOO-216"]}
    lines = cross_reference(schedule, doors)
    assert any("MOO-214, MOO-216" in line for line in lines)


def test_a_symbol_with_no_doors_says_so():
    lines = cross_reference(a_cut_schedule(), {})
    assert all("nothing on record" in line for line in lines[1:])


def test_lines_join_into_a_page():
    page = as_text(schedule_report(a_cut_schedule(), "chester park"))
    assert page.startswith("chester park")
    assert page.count("\n") == len(schedule_report(a_cut_schedule(), "chester park")) - 1

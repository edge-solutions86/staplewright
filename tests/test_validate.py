from charts import six_pin_chart
from hasplib.keys.bitting import Bitting
from hasplib.keys.schedule import ScheduleEntry
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.plan import ProgressionPlan
from hasplib.progression.validate import (
    Severity,
    blocking,
    crowded_chambers,
    duplicate_cuts,
    is_buildable,
    keys_matching_their_master,
    keys_off_plan,
    review,
    thin_splits,
    uncut_symbols,
)
from systems import small_schedule


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


CUTS = {
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


def a_good_schedule():
    schedule = small_schedule()
    for symbol, bitting in CUTS.items():
        schedule.assign(sym(symbol), b(bitting))
    return schedule


def force(schedule, symbol: str, bitting: str):
    """Write a line the way the database would, checks and all skipped."""
    entry = schedule.entry(sym(symbol))
    schedule.load(ScheduleEntry(entry.symbol, entry.section, b(bitting), entry.note))
    return schedule


def test_a_clean_schedule_has_nothing_to_say():
    schedule = a_good_schedule()
    assert duplicate_cuts(schedule) == []
    assert keys_matching_their_master(schedule) == []
    assert thin_splits(schedule) == []
    assert review(schedule) == ()
    assert is_buildable(review(schedule))


def test_two_keys_cut_the_same_is_blocking():
    schedule = force(a_good_schedule(), "AB2", "620446")
    found = duplicate_cuts(schedule)
    assert len(found) == 1
    assert found[0].severity is Severity.BLOCKING
    assert "AA1" in found[0].message
    assert found[0].symbol == "AB2"


def test_a_change_key_cut_like_its_master_is_blocking():
    schedule = force(a_good_schedule(), "AA1", "622446")
    found = keys_matching_their_master(schedule)
    assert [f.symbol for f in found] == ["AA1"]
    assert found[0].code == "same_as_master"


def test_a_one_step_split_is_serious_not_blocking():
    schedule = force(a_good_schedule(), "AA1", "621446")
    found = thin_splits(schedule)
    assert [f.code for f in found] == ["off_step"]
    assert found[0].severity is Severity.SERIOUS
    assert "chamber 3" in found[0].message


def test_a_system_that_moves_one_step_at_a_time_sees_nothing():
    schedule = force(a_good_schedule(), "AA1", "621446")
    assert thin_splits(schedule, step=1) == []


def test_a_key_that_moved_in_a_constant_chamber_is_off_plan():
    schedule = force(a_good_schedule(), "AA1", "621456")
    plan = ProgressionPlan(six_pin_chart(), b("622446"), (3,), step=2)
    found = keys_off_plan(schedule, plan)
    codes = {f.symbol for f in found}
    assert "AA1" in codes
    assert any("constant chamber" in f.message for f in found)


def test_a_key_the_plan_holds_is_not_reported():
    schedule = a_good_schedule()
    plan = ProgressionPlan(six_pin_chart(), b("622446"), (3,), step=2)
    off = {f.symbol for f in keys_off_plan(schedule, plan)}
    assert "AA1" not in off


def test_an_uncut_symbol_is_only_a_note():
    schedule = small_schedule()
    found = uncut_symbols(schedule)
    assert len(found) == len(schedule)
    assert all(f.severity is Severity.NOTE for f in found)


def test_a_crowded_chamber_is_reported_against_the_group():
    keys = [b("442446"), b("642446"), b("242446"), b("842446")]
    found = crowded_chambers(a_good_schedule(), [keys], most_wafers=2)
    assert [f.code for f in found] == ["crowded_chamber"]
    assert "chamber 1" in found[0].message


def test_a_group_inside_the_limit_is_quiet():
    keys = [b("442446"), b("642446")]
    assert crowded_chambers(a_good_schedule(), [keys], most_wafers=2) == []


def test_the_report_puts_the_blocking_findings_first():
    schedule = force(a_good_schedule(), "AB2", "620446")
    schedule = force(schedule, "AA2", "623446")
    found = review(schedule)
    assert found[0].severity is Severity.BLOCKING
    assert [f.severity for f in found] == sorted(
        [f.severity for f in found], key=lambda s: ["blocking", "serious", "note"].index(str(s))
    )


def test_blocking_pulls_out_the_ones_that_stop_the_job():
    schedule = force(a_good_schedule(), "AB2", "620446")
    assert len(blocking(review(schedule))) == 1
    assert not is_buildable(review(schedule))


def test_a_finding_renders_flat_for_the_report():
    schedule = force(a_good_schedule(), "AB2", "620446")
    rendered = duplicate_cuts(schedule)[0].as_dict()
    assert rendered["code"] == "duplicate_cut"
    assert rendered["severity"] == "blocking"
    assert rendered["symbol"] == "AB2"


def test_a_finding_without_a_symbol_leaves_it_out():
    keys = [b("442446"), b("642446"), b("242446"), b("842446")]
    rendered = crowded_chambers(a_good_schedule(), [keys], most_wafers=2)[0].as_dict()
    assert "symbol" not in rendered

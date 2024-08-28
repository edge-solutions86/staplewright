import pytest

from charts import five_pin_chart, six_pin_chart
from hasplib.keys.bitting import Bitting
from hasplib.spec.rules import (
    CuttingRules,
    RuleSet,
    check_cuts,
    check_macs,
    check_runs,
    check_tip,
    check_variety,
    is_acceptable,
    reasons,
)


def test_a_reasonable_key_draws_no_complaints():
    assert check_cuts(six_pin_chart(), Bitting.parse("342156")) == ()
    assert is_acceptable(six_pin_chart(), Bitting.parse("342156"))


def test_a_step_past_the_macs_names_the_lower_chamber():
    found = check_macs(six_pin_chart(), Bitting.parse("192156"))
    assert [v.chamber for v in found] == [1]
    assert found[0].facts == (("step", 8), ("macs", 7))


def test_a_tighter_macs_catches_what_the_wider_one_allows():
    bitting = Bitting.parse("16151")
    assert check_macs(five_pin_chart(macs=7), bitting) == []
    assert reasons(check_macs(five_pin_chart(), bitting)) == ("macs", "macs")


def test_a_long_run_of_one_depth_is_caught_once():
    found = check_runs(CuttingRules(), Bitting.parse("144442"))
    assert len(found) == 1
    assert found[0].chamber == 2
    assert dict(found[0].facts)["length"] == 4


def test_a_run_exactly_at_the_limit_passes():
    assert check_runs(CuttingRules(max_run=3), Bitting.parse("144426")) == []


def test_two_separate_runs_are_two_complaints():
    found = check_runs(CuttingRules(max_run=1), Bitting.parse("113355"))
    assert [v.chamber for v in found] == [1, 3, 5]


def test_a_run_that_reaches_the_tip_is_still_seen():
    found = check_runs(CuttingRules(max_run=2), Bitting.parse("142222"))
    assert [v.chamber for v in found] == [3]


def test_a_key_of_one_depth_fails_variety_and_the_end_of_the_chart():
    found = check_variety(six_pin_chart(), CuttingRules(), Bitting.parse("000000"))
    assert reasons(found) == ("variety", "all_shallowest")


def test_all_deepest_is_its_own_complaint():
    found = check_variety(six_pin_chart(), CuttingRules(), Bitting.parse("999999"))
    assert reasons(found) == ("variety", "all_deepest")


def test_policy_can_stop_caring_about_the_ends():
    rules = CuttingRules(min_distinct=1, forbid_all_shallowest=False, forbid_all_deepest=False)
    assert check_variety(six_pin_chart(), rules, Bitting.parse("000000")) == []


def test_the_tip_rule_is_off_unless_policy_sets_it():
    assert check_tip(CuttingRules(), Bitting.parse("342159")) == []
    found = check_tip(CuttingRules(shallow_tip_limit=6), Bitting.parse("342159"))
    assert found[0].chamber == 6
    assert dict(found[0].facts) == {"depth": 9, "limit": 6}


def test_a_key_of_the_wrong_length_gets_that_answer_alone():
    found = check_cuts(six_pin_chart(), Bitting.parse("19"))
    assert reasons(found) == ("length",)


def test_a_depth_off_the_chart_stops_the_later_rules():
    chart = six_pin_chart(depths=(0, 1, 2, 3, 4, 5, 6))
    found = check_cuts(chart, Bitting.parse("199999"))
    assert reasons(found) == ("depth",) * 5
    assert [v.chamber for v in found] == [2, 3, 4, 5, 6]


def test_the_rules_stack_up_on_one_bad_key():
    found = check_cuts(
        six_pin_chart(),
        Bitting.parse("199999"),
        CuttingRules(max_run=3, shallow_tip_limit=6),
    )
    assert reasons(found) == ("macs", "run", "tip")


def test_a_violation_renders_flat_for_a_report():
    found = check_macs(six_pin_chart(), Bitting.parse("192156"))
    assert found[0].as_dict() == {
        "rule": "macs",
        "message": "chambers 1 and 2 step 8, the chart allows 7",
        "chamber": 1,
        "step": 8,
        "macs": 7,
    }


def test_a_violation_without_a_chamber_leaves_it_out():
    found = check_variety(six_pin_chart(), CuttingRules(), Bitting.parse("111111"))
    assert "chamber" not in found[0].as_dict()


def test_a_ruleset_carries_both_halves_together():
    ruleset = RuleSet(six_pin_chart(), CuttingRules(shallow_tip_limit=4))
    assert ruleset.accepts(Bitting.parse("342154"))
    assert reasons(ruleset.check(Bitting.parse("342159"))) == ("tip",)


def test_a_ruleset_takes_default_policy_if_none_is_given():
    assert RuleSet(six_pin_chart()).accepts(Bitting.parse("342159"))


@pytest.mark.parametrize("broken", [{"max_run": 0}, {"min_distinct": 0}])
def test_policy_that_forbids_every_key_is_refused(broken):
    with pytest.raises(ValueError):
        CuttingRules(**broken)

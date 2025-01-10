from datetime import date

import pytest

from hasplib.keys.symbol import KeySymbol
from hasplib.rekey.compromise import Cause, Incident, assess
from hasplib.rekey.scope import Scope, ScopePolicy, closure, decide
from keyed import a_coverage, a_ledger

WHEN = date(2025, 4, 8)


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def an_incident(**overrides) -> Incident:
    settings = {
        "number": "I-2025-004",
        "tag": "41207",
        "symbol": "AA1",
        "cause": Cause.LOST,
        "reported_on": WHEN,
        "keyholder": "kmaki",
    }
    settings.update(overrides)
    return Incident(**settings)


def decision_for(symbol: str, tag: str = "41207", **policy_bits):
    coverage = a_coverage()
    incident = an_incident(symbol=symbol, tag=tag)
    found = assess(incident, coverage, a_ledger())
    return decide(found, coverage, ScopePolicy(**policy_bits))


def test_a_change_key_drags_in_only_its_own_cylinders():
    symbols, serials = closure(a_coverage(), [sym("AA1")])
    assert [s.render() for s in symbols] == ["AA1"]
    assert serials == ("C-1001", "C-1002")


def test_a_master_drags_in_everything_it_turns():
    symbols, serials = closure(a_coverage(), [sym("AA")])
    assert [s.render() for s in symbols] == ["AA"]
    assert serials == ("C-1001", "C-1002", "C-1003")


def test_the_strict_policy_pulls_in_the_other_keys_on_the_cylinder():
    symbols, serials = closure(a_coverage(), [sym("AA1")], recut_all_on_repin=True)
    rendered = {s.render() for s in symbols}
    assert {"GGM", "A", "AA", "AA1"} <= rendered
    assert len(serials) == 6


def test_the_strict_policy_reaches_a_fixpoint_rather_than_looping():
    symbols, serials = closure(a_coverage(), [sym("AA1")], recut_all_on_repin=True)
    again = closure(a_coverage(), symbols, recut_all_on_repin=True)
    assert again == (symbols, serials)


def test_a_symbol_that_turns_nothing_closes_over_nothing():
    symbols, serials = closure(a_coverage(), [sym("ZZ9")])
    assert serials == ()
    assert [s.render() for s in symbols] == ["ZZ9"]


def test_a_lost_change_key_rekeys_its_group():
    found = decision_for("AA1")
    assert found.scope is Scope.GROUP
    assert found.cylinder_count == 2
    assert not found.over_cap


def test_a_lost_master_says_so_in_the_reason():
    found = decision_for("AA", tag="41209")
    assert "master" in found.reason
    assert found.cylinder_count == 3


def test_a_key_reaching_two_buildings_is_a_system_rekey():
    found = decision_for("GGM", tag="41211")
    assert found.scope is Scope.SYSTEM
    assert found.cylinder_count == 6


def test_one_cylinder_alone_is_an_opening_rekey():
    found = decision_for("AA2")
    assert found.scope is Scope.OPENING
    assert found.cylinder_count == 1


def test_a_key_on_record_as_turning_nothing_gets_no_work():
    found = decision_for("ZZ9")
    assert found.does_nothing
    assert found.cylinders == ()


def test_a_small_incident_under_the_threshold_is_left_alone():
    found = decision_for("AA1", ignore_below_openings=3)
    assert found.does_nothing
    assert "threshold" in found.reason


def test_a_master_is_rekeyed_even_under_the_threshold():
    found = decision_for("AA", tag="41209", ignore_below_openings=10)
    assert not found.does_nothing
    assert "master" in found.reason


def test_an_exterior_door_forces_the_work_through_the_threshold():
    found = decision_for("A", tag="41210", ignore_below_openings=10, always_rekey_masters=False)
    assert "exterior" in found.reason


def test_a_shop_that_does_not_care_about_exteriors_can_say_so():
    coverage = a_coverage()
    incident = an_incident(symbol="A", tag="41210")
    found = assess(incident, coverage, a_ledger())
    policy = ScopePolicy(
        always_rekey_masters=False, always_rekey_exterior=False, ignore_below_openings=10
    )
    assert decide(found, coverage, policy).does_nothing


def test_a_theft_is_acted_on_whatever_the_threshold_says():
    coverage = a_coverage()
    incident = an_incident(cause=Cause.STOLEN, note="taken from a coat")
    found = assess(incident, coverage, a_ledger())
    policy = ScopePolicy(ignore_below_openings=10)
    assert "taken" in decide(found, coverage, policy).reason


def test_going_over_the_cap_is_flagged_rather_than_refused():
    found = decision_for("GGM", tag="41211", cylinder_cap=4)
    assert found.over_cap
    assert "cap of 4" in found.reason
    assert found.cylinder_count == 6


def test_staying_under_the_cap_is_not_flagged():
    assert not decision_for("AA1", cylinder_cap=4).over_cap


def test_the_summary_reads_like_a_work_order_line():
    summary = decision_for("AA1").summary()
    assert summary.startswith("group: 2 cylinders")


@pytest.mark.parametrize("broken", [{"ignore_below_openings": -1}, {"cylinder_cap": 0}])
def test_a_policy_that_contradicts_itself_is_refused(broken):
    with pytest.raises(ValueError):
        ScopePolicy(**broken)

from datetime import date

import pytest

from hasplib.control.issue import KeyLedger
from hasplib.errors import ConflictError, InputError
from hasplib.keys.duplication import (
    Authorisation,
    DuplicationPolicy,
    DuplicationRegister,
    restricted_needed,
    unstamped,
)
from hasplib.keys.symbol import KeySymbol
from people import TODAY, a_directory

SIGNED = date(2025, 4, 1)


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def a_ledger(**counts) -> KeyLedger:
    ledger = KeyLedger(a_directory())
    people = ["kmaki", "dsalo", "rvirta", "phamm"]
    tag = 41000
    for symbol, number in counts.items():
        for index in range(number):
            ledger.issue(str(tag), symbol, people[index % len(people)], TODAY)
            tag += 1
    return ledger


def an_authorisation(**overrides) -> Authorisation:
    settings = {
        "symbol": "AA1",
        "ceiling": 6,
        "signed_by": "phamm",
        "signed_on": SIGNED,
        "reason": "the lab runs three shifts",
    }
    settings.update(overrides)
    return Authorisation(**settings)


def test_the_standing_ceilings_come_off_the_policy():
    policy = DuplicationPolicy()
    assert policy.ceiling_for(sym("AA1")) == 4
    assert policy.ceiling_for(sym("AA")) == 2
    assert policy.ceiling_for(sym("GGM")) == 1


def test_a_shop_can_set_its_own_ceilings():
    policy = DuplicationPolicy(change_key_limit=8, master_limit=3)
    assert policy.ceiling_for(sym("AA1")) == 8
    assert policy.ceiling_for(sym("AB")) == 3


@pytest.mark.parametrize("broken", [{"change_key_limit": 0}, {"top_key_limit": 0}])
def test_a_ceiling_of_nothing_is_refused(broken):
    with pytest.raises(InputError):
        DuplicationPolicy(**broken)


def test_an_authorisation_says_why_and_raises_something():
    with pytest.raises(InputError):
        an_authorisation(reason="  ")
    with pytest.raises(InputError):
        an_authorisation(ceiling=0)


def test_an_authorisation_raises_the_ceiling():
    register = DuplicationRegister()
    register.authorise(an_authorisation())
    assert register.ceiling_for(sym("AA1")) == 6
    assert register.is_authorised(sym("AA1"))


def test_a_symbol_nobody_signed_for_keeps_the_policy_ceiling():
    register = DuplicationRegister()
    assert register.ceiling_for(sym("AB1")) == 4
    assert not register.is_authorised(sym("AB1"))
    assert register.authorisation("AB1") is None


def test_a_ceiling_cannot_be_lowered_here():
    register = DuplicationRegister()
    register.authorise(an_authorisation(ceiling=6))
    with pytest.raises(ConflictError):
        register.authorise(an_authorisation(ceiling=5))


def test_a_ceiling_can_be_raised_again():
    register = DuplicationRegister()
    register.authorise(an_authorisation(ceiling=6))
    register.authorise(an_authorisation(ceiling=9))
    assert register.ceiling_for(sym("AA1")) == 9


def test_nothing_is_over_when_the_counts_are_under():
    register = DuplicationRegister()
    assert register.overruns(a_ledger(AA1=3)) == ()


def test_a_symbol_over_its_ceiling_is_reported():
    register = DuplicationRegister()
    found = register.overruns(a_ledger(AA1=6))
    assert found[0].symbol == "AA1"
    assert found[0].out == 6
    assert found[0].over_by == 2
    assert not found[0].authorised


def test_an_authorised_symbol_is_measured_against_its_own_ceiling():
    register = DuplicationRegister()
    register.authorise(an_authorisation(ceiling=6))
    assert register.overruns(a_ledger(AA1=6)) == ()


def test_an_authorised_symbol_can_still_go_over():
    register = DuplicationRegister()
    register.authorise(an_authorisation(ceiling=6))
    found = register.overruns(a_ledger(AA1=8))
    assert found[0].authorised
    assert "authorised ceiling 6" in found[0].describe()


def test_the_worst_overrun_comes_first():
    register = DuplicationRegister()
    found = register.overruns(a_ledger(AA1=8, AA=4))
    assert [item.symbol for item in found] == ["AA1", "AA"]


def test_a_returned_key_is_not_counted():
    register = DuplicationRegister()
    ledger = a_ledger(AA1=5)
    ledger.take_back("41000", SIGNED)
    assert register.overruns(ledger) == ()


def test_the_room_left_is_the_ceiling_less_what_is_out():
    register = DuplicationRegister()
    ledger = a_ledger(AA1=3)
    assert register.room_for(sym("AA1"), ledger) == 1
    assert not register.would_exceed(sym("AA1"), ledger)


def test_a_symbol_at_its_ceiling_has_no_room():
    register = DuplicationRegister()
    ledger = a_ledger(AA1=4)
    assert register.room_for(sym("AA1"), ledger) == 0
    assert register.would_exceed(sym("AA1"), ledger)


def test_a_symbol_over_its_ceiling_has_no_negative_room():
    register = DuplicationRegister()
    assert register.room_for(sym("AA1"), a_ledger(AA1=9)) == 0


def test_the_authorisations_come_back_in_symbol_order():
    register = DuplicationRegister()
    register.authorise(an_authorisation(symbol="AB1"))
    register.authorise(an_authorisation(symbol="AA1"))
    assert [item.symbol for item in register.authorisations()] == ["AA1", "AB1"]


def test_masters_need_a_restricted_blank_and_change_keys_do_not():
    policy = DuplicationPolicy()
    assert restricted_needed(policy, sym("AA"))
    assert restricted_needed(policy, sym("GGM"))
    assert not restricted_needed(policy, sym("AA1"))


def test_a_shop_can_restrict_everything():
    policy = DuplicationPolicy(restricted_only_for_masters=False)
    assert restricted_needed(policy, sym("AA1"))


def test_the_stock_check_lists_what_needs_a_restricted_blank():
    symbols = [sym("GGM"), sym("A"), sym("AA1")]
    assert unstamped(symbols, DuplicationPolicy()) == ("GGM", "A")

from datetime import date

import pytest

from hasplib.control.escrow import (
    BoxPolicy,
    BoxRegister,
    BoxState,
    KeyBox,
    OpenedBy,
    boxes_needing_work,
    coverage,
)
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.keys.symbol import KeySymbol

SEALED = date(2025, 1, 6)
WALKED = date(2025, 6, 30)
LATE = date(2025, 12, 1)
CHANGE_KEY = KeySymbol.parse("AA1")
MASTER = KeySymbol.parse("A")


def a_box(number: str = "KB-01", **overrides) -> KeyBox:
    settings = {
        "number": number,
        "building": "MOO",
        "opening": "MOO-100",
        "symbol": CHANGE_KEY,
        "seal": "44012",
        "sealed_on": SEALED,
    }
    settings.update(overrides)
    return KeyBox(**settings)


def a_register(policy: BoxPolicy | None = None) -> BoxRegister:
    register = BoxRegister(policy)
    register.install(a_box())
    return register


def test_a_policy_needs_a_level():
    with pytest.raises(InputError):
        BoxPolicy(lowest_level=0)


def test_a_policy_needs_a_check_window():
    with pytest.raises(InputError):
        BoxPolicy(check_days=0)


def test_a_reseal_window_does_not_run_backwards():
    with pytest.raises(InputError):
        BoxPolicy(reseal_days=-1)


def test_a_change_key_is_allowed_in_a_box():
    assert BoxPolicy().allows(CHANGE_KEY)


def test_a_master_is_not():
    assert not BoxPolicy().allows(MASTER)


def test_a_shop_that_allows_masters_can_say_so():
    assert BoxPolicy(lowest_level=1).allows(MASTER)


def test_a_box_needs_a_number():
    with pytest.raises(InputError):
        a_box(number="  ")


def test_a_sealed_box_needs_a_seal():
    with pytest.raises(InputError):
        a_box(seal="")


def test_an_empty_box_does_not_need_one():
    assert a_box(seal="", state=BoxState.EMPTY).seal == ""


def test_a_box_goes_up_and_comes_back():
    register = a_register()
    assert register.box("KB-01").state is BoxState.SEALED


def test_a_box_holding_a_master_is_refused():
    register = BoxRegister()
    with pytest.raises(ConflictError) as caught:
        register.install(a_box(symbol=MASTER))
    assert caught.value.facts["symbol"] == "A"


def test_a_box_does_not_go_up_already_open():
    register = BoxRegister()
    with pytest.raises(ConflictError):
        register.install(a_box(state=BoxState.OPENED))


def test_a_box_number_is_not_reused():
    register = a_register()
    with pytest.raises(ConflictError):
        register.install(a_box())


def test_a_box_nobody_installed_is_a_404():
    with pytest.raises(NotFoundError):
        BoxRegister().box("KB-99")


def test_reading_the_right_seal_is_just_a_check():
    register = a_register()
    found = register.check("KB-01", "44012", WALKED)
    assert found.state is BoxState.SEALED
    assert found.checked_on == WALKED


def test_a_seal_with_space_round_it_still_matches():
    assert a_register().check("KB-01", " 44012 ", WALKED).state is BoxState.SEALED


def test_a_seal_that_does_not_match_means_it_was_opened():
    found = a_register().check("KB-01", "44099", WALKED)
    assert found.state is BoxState.OPENED
    assert found.opened_by is OpenedBy.UNKNOWN


def test_the_note_says_what_was_read_and_what_was_expected():
    found = a_register().check("KB-01", "44099", WALKED)
    assert found.note == "seal read 44099, expected 44012"


def test_a_box_already_open_does_not_get_its_seal_read():
    register = a_register()
    register.check("KB-01", "44099", WALKED)
    with pytest.raises(ConflictError):
        register.check("KB-01", "44099", LATE)


def test_somebody_can_say_they_opened_it():
    found = a_register().report_opened("KB-01", OpenedBy.FIRE, WALKED, "kitchen fire")
    assert found.state is BoxState.OPENED
    assert found.opened_by is OpenedBy.FIRE


def test_a_box_cannot_be_opened_twice():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    with pytest.raises(ConflictError):
        register.report_opened("KB-01", OpenedBy.SHOP, LATE)


def test_a_key_only_goes_missing_out_of_an_open_box():
    register = a_register()
    with pytest.raises(ConflictError):
        register.empty("KB-01", WALKED)


def test_an_open_box_can_be_found_empty():
    register = a_register()
    register.check("KB-01", "44099", WALKED)
    found = register.empty("KB-01", LATE, "no key in it")
    assert found.state is BoxState.EMPTY
    assert not found.holds_a_key


def test_resealing_puts_it_back_to_sealed():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    found = register.reseal("KB-01", "44120", CHANGE_KEY, LATE)
    assert found.state is BoxState.SEALED
    assert found.seal == "44120"
    assert found.opened_on is None


def test_resealing_a_sealed_box_is_refused():
    register = a_register()
    with pytest.raises(ConflictError):
        register.reseal("KB-01", "44120", CHANGE_KEY, LATE)


def test_a_reseal_needs_a_seal_number():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    with pytest.raises(InputError):
        register.reseal("KB-01", " ", CHANGE_KEY, LATE)


def test_a_reseal_with_the_same_number_is_refused():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    with pytest.raises(ConflictError):
        register.reseal("KB-01", "44012", CHANGE_KEY, LATE)


def test_a_reseal_cannot_slip_a_master_in():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    with pytest.raises(ConflictError):
        register.reseal("KB-01", "44120", MASTER, LATE)


def test_an_empty_box_can_be_resealed():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.empty("KB-01", WALKED)
    assert register.reseal("KB-01", "44120", CHANGE_KEY, LATE).state is BoxState.SEALED


def test_taking_a_box_down_says_why():
    register = a_register()
    with pytest.raises(InputError):
        register.withdraw("KB-01", LATE, "")


def test_a_withdrawn_box_is_off_the_wall():
    register = a_register()
    found = register.withdraw("KB-01", LATE, "building came down")
    assert found.state is BoxState.WITHDRAWN
    assert not found.is_live


def test_a_box_cannot_be_taken_down_twice():
    register = a_register()
    register.withdraw("KB-01", LATE, "building came down")
    with pytest.raises(ConflictError):
        register.withdraw("KB-01", LATE, "again")


def test_a_box_is_due_a_check_after_the_window():
    register = a_register()
    assert register.due_for_check(date(2025, 7, 6)) != ()


def test_a_box_checked_recently_is_not_due():
    register = a_register()
    register.check("KB-01", "44012", WALKED)
    assert register.due_for_check(date(2025, 7, 6)) == ()


def test_a_withdrawn_box_is_never_due():
    register = a_register()
    register.withdraw("KB-01", WALKED, "gone")
    assert register.due_for_check(LATE) == ()


def test_a_box_open_longer_than_the_window_is_overdue():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert [box.number for box in register.overdue_reseal(date(2025, 7, 5))] == ["KB-01"]


def test_a_box_opened_yesterday_is_not_overdue_yet():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert register.overdue_reseal(date(2025, 7, 1)) == ()


def test_a_sealed_box_is_never_overdue_a_reseal():
    assert a_register().overdue_reseal(LATE) == ()


def test_the_open_boxes_put_the_empty_ones_first():
    register = a_register()
    register.install(a_box("KB-02", seal="44013"))
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.report_opened("KB-02", OpenedBy.SHOP, WALKED)
    register.empty("KB-02", WALKED)
    assert [box.number for box in register.open_boxes()] == ["KB-02", "KB-01"]


def test_a_sealed_campus_has_no_open_boxes():
    assert a_register().open_boxes() == ()


def test_the_boxes_on_a_building_come_back_together():
    register = a_register()
    register.install(a_box("KB-02", building="VOS", seal="44013"))
    assert [box.number for box in register.in_building("VOS")] == ["KB-02"]


def test_the_boxes_holding_a_symbol_are_the_ones_with_a_key_in():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.empty("KB-01", WALKED)
    assert register.holding(CHANGE_KEY) == ()


def test_the_exposure_counts_open_boxes_per_building():
    register = a_register()
    register.install(a_box("KB-02", seal="44013"))
    register.install(a_box("KB-03", building="VOS", seal="44014"))
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.report_opened("KB-02", OpenedBy.FIRE, WALKED)
    register.report_opened("KB-03", OpenedBy.FIRE, WALKED)
    assert register.exposure() == {"MOO": 2, "VOS": 1}


def test_the_symbols_at_risk_are_the_ones_in_an_open_box():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert [symbol.render() for symbol in register.symbols_at_risk()] == ["AA1"]


def test_an_empty_box_puts_no_symbol_at_risk():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.empty("KB-01", WALKED)
    assert register.symbols_at_risk() == ()


def test_the_walk_round_leads_with_the_open_boxes():
    register = a_register()
    register.install(a_box("KB-02", seal="44013"))
    register.report_opened("KB-02", OpenedBy.FIRE, WALKED)
    found = boxes_needing_work(register, LATE)
    assert found[0].number == "KB-02"


def test_the_walk_round_does_not_name_a_box_twice():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert len(boxes_needing_work(register, LATE)) == 1


def test_a_building_with_no_box_is_named():
    assert coverage(a_register(), ["MOO", "VOS", "HEI"]) == ("HEI", "VOS")


def test_a_withdrawn_box_leaves_a_building_uncovered():
    register = a_register()
    register.withdraw("KB-01", LATE, "building came down")
    assert coverage(register, ["MOO"]) == ("MOO",)


def test_the_register_counts_and_iterates():
    register = a_register()
    assert len(register) == 1
    assert "KB-01" in register
    assert [box.number for box in register] == ["KB-01"]


def test_a_box_reads_back_as_one_line():
    assert a_box().describe() == "KB-01 MOO MOO-100 AA1 sealed"


def test_a_box_that_was_loaded_keeps_the_state_it_was_in():
    register = BoxRegister()
    register.load(a_box(state=BoxState.EMPTY, seal=""))
    assert register.box("KB-01").state is BoxState.EMPTY

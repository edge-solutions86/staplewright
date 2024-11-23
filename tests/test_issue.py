from datetime import date

import pytest

from hasplib.control.issue import IssuedKey, KeyLedger, KeyState
from hasplib.errors import ConflictError, InputError, NotFoundError
from people import TODAY, a_directory

LATER = date(2025, 4, 1)


def a_ledger() -> KeyLedger:
    return KeyLedger(a_directory())


def stocked() -> KeyLedger:
    ledger = a_ledger()
    ledger.issue("41207", "AA1", "kmaki", TODAY, deposit_cents=2500)
    ledger.issue("41208", "AA1", "dsalo", TODAY, deposit_cents=2500)
    ledger.issue("41209", "AA", "rvirta", TODAY, deposit_cents=5000)
    ledger.issue("41210", "AB2", "tkoski", TODAY, due_on=date(2025, 5, 16))
    return ledger


def test_a_key_goes_out_against_a_person():
    ledger = a_ledger()
    key = ledger.issue("41207", "AA1", "kmaki", TODAY)
    assert key.is_out
    assert key.keyholder == "kmaki"
    assert key.describe() == "tag 41207 cut AA1, kmaki, out"


def test_a_key_needs_a_tag():
    with pytest.raises(InputError):
        IssuedKey("  ", "AA1", "kmaki", TODAY)


def test_a_deposit_is_not_negative():
    with pytest.raises(InputError):
        IssuedKey("41207", "AA1", "kmaki", TODAY, deposit_cents=-1)


def test_a_key_is_not_due_back_before_it_goes_out():
    with pytest.raises(ConflictError):
        IssuedKey("41207", "AA1", "kmaki", TODAY, due_on=date(2025, 1, 1))


def test_a_tag_cannot_be_issued_twice():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.issue("41207", "AA1", "dsalo", TODAY)


def test_a_separated_keyholder_gets_nothing():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    ledger = KeyLedger(directory)
    with pytest.raises(ConflictError):
        ledger.issue("41207", "AA1", "kmaki", TODAY)


def test_a_lapsed_contractor_gets_nothing():
    ledger = a_ledger()
    with pytest.raises(ConflictError):
        ledger.issue("41207", "AA1", "jwexler", TODAY)


def test_a_key_cannot_be_due_after_the_contract_ends():
    ledger = a_ledger()
    with pytest.raises(ConflictError) as caught:
        ledger.issue("41210", "AB2", "tkoski", TODAY, due_on=date(2025, 8, 1))
    assert caught.value.facts["ends_on"] == "2025-05-16"


def test_a_key_for_somebody_who_is_not_in_the_directory():
    ledger = a_ledger()
    with pytest.raises(NotFoundError):
        ledger.issue("41207", "AA1", "ghost", TODAY)


def test_a_returned_key_is_no_longer_out():
    ledger = stocked()
    returned = ledger.take_back("41207", LATER, "left the department")
    assert returned.state is KeyState.RETURNED
    assert returned.closed_on == LATER
    assert "41207" not in [key.tag for key in ledger.out()]


def test_a_key_cannot_come_back_twice():
    ledger = stocked()
    ledger.take_back("41207", LATER)
    with pytest.raises(ConflictError):
        ledger.take_back("41207", LATER)


def test_a_lost_key_and_a_stolen_key_are_told_apart():
    ledger = stocked()
    ledger.report_lost("41207", LATER)
    ledger.report_stolen("41208", LATER, "taken from a desk drawer overnight")
    assert ledger.key("41207").state is KeyState.LOST
    assert ledger.key("41208").state is KeyState.STOLEN
    assert [key.tag for key in ledger.gone()] == ["41207", "41208"]


def test_a_theft_has_to_be_written_up():
    ledger = stocked()
    with pytest.raises(InputError):
        ledger.report_stolen("41207", LATER, "   ")


def test_a_key_that_turns_up_comes_back_rather_than_going_out():
    ledger = stocked()
    ledger.report_lost("41207", LATER)
    found = ledger.found("41207", LATER)
    assert found.state is KeyState.RETURNED
    assert not found.is_out


def test_a_key_that_was_never_lost_cannot_be_found():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.found("41207", LATER)


def test_a_transfer_moves_the_key_without_the_counter():
    ledger = stocked()
    moved = ledger.transfer("41207", "dsalo", LATER)
    assert moved.keyholder == "dsalo"
    assert [key.tag for key in ledger.held_by("kmaki")] == []
    assert [key.tag for key in ledger.held_by("dsalo")] == ["41207", "41208"]


def test_a_transfer_to_somebody_who_may_not_hold_keys_is_refused():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.transfer("41207", "jwexler", LATER)


def test_a_transfer_to_the_current_holder_is_refused():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.transfer("41207", "kmaki", LATER)


def test_a_returned_key_cannot_be_transferred():
    ledger = stocked()
    ledger.take_back("41207", LATER)
    with pytest.raises(ConflictError):
        ledger.transfer("41207", "dsalo", LATER)


def test_a_due_date_can_be_pushed_out():
    ledger = stocked()
    assert ledger.extend("41210", date(2025, 6, 30)).due_on == date(2025, 6, 30)


def test_a_due_date_cannot_be_pulled_back():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.extend("41210", date(2025, 3, 1))


def test_a_key_with_no_due_date_has_nothing_to_extend():
    ledger = stocked()
    with pytest.raises(ConflictError):
        ledger.extend("41207", LATER)


def test_an_overdue_key_is_reported_with_its_days():
    ledger = stocked()
    late = date(2025, 6, 1)
    overdue = ledger.overdue(late)
    assert [key.tag for key in overdue] == ["41210"]
    assert overdue[0].days_late(late) == 16


def test_nothing_is_overdue_before_the_due_date():
    assert stocked().overdue(TODAY) == ()


def test_a_key_with_no_due_date_is_never_overdue():
    ledger = stocked()
    assert not ledger.key("41207").overdue_on(date(2030, 1, 1))


def test_holdings_and_history_are_different_questions():
    ledger = stocked()
    ledger.take_back("41207", LATER)
    assert ledger.held_by("kmaki") == ()
    assert [key.tag for key in ledger.history_of("kmaki")] == ["41207"]


def test_keys_cut_to_one_symbol_come_back_together():
    ledger = stocked()
    assert [key.tag for key in ledger.cut_to("AA1")] == ["41207", "41208"]
    assert ledger.cut_to("ZZ9") == ()


def test_a_returned_key_drops_out_of_the_symbol_list():
    ledger = stocked()
    ledger.take_back("41207", LATER)
    assert [key.tag for key in ledger.cut_to("AA1")] == ["41208"]


def test_deposits_add_up_over_what_is_out():
    ledger = stocked()
    assert ledger.deposits_held() == 10000
    ledger.take_back("41209", LATER)
    assert ledger.deposits_held() == 5000


def test_the_count_by_symbol_puts_the_common_key_first():
    counts = stocked().count_by_symbol()
    assert list(counts) == ["AA1", "AA", "AB2"]
    assert counts["AA1"] == 2


def test_the_ledger_is_a_container_in_issue_order():
    ledger = stocked()
    assert "41207" in ledger
    assert "99999" not in ledger
    assert [key.tag for key in ledger] == ["41207", "41208", "41209", "41210"]
    assert len(ledger) == 4


def test_asking_about_a_tag_that_was_never_issued():
    with pytest.raises(NotFoundError):
        a_ledger().key("99999")

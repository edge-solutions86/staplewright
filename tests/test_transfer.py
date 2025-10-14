from datetime import date

import pytest

from hasplib.control.transfer import (
    Transfer,
    TransferBook,
    TransferPolicy,
    TransferState,
    keys_that_moved,
    unconfirmed_exposure,
)
from hasplib.errors import ConflictError, InputError, NotFoundError

CLAIMED = date(2025, 3, 4)
SOON = date(2025, 3, 10)
LATE = date(2025, 5, 1)


def a_transfer(number: str = "T-0001", **overrides) -> Transfer:
    settings = {
        "number": number,
        "tag": "41207",
        "symbol": "AA1",
        "passed_by": "kmaki",
        "passed_to": "rlaakso",
        "claimed_on": CLAIMED,
    }
    settings.update(overrides)
    return Transfer(**settings)


def a_book(policy: TransferPolicy | None = None) -> TransferBook:
    book = TransferBook(policy)
    book.claim(a_transfer())
    return book


def test_a_transfer_needs_a_number():
    with pytest.raises(InputError):
        a_transfer(number=" ")


def test_a_transfer_needs_a_tag():
    with pytest.raises(InputError):
        a_transfer(tag="")


def test_a_key_cannot_be_handed_to_the_person_holding_it():
    with pytest.raises(ConflictError):
        a_transfer(passed_to="kmaki")


def test_a_policy_window_is_at_least_a_day():
    with pytest.raises(InputError):
        TransferPolicy(confirm_days=0)


def test_a_claim_goes_in_and_comes_back():
    book = a_book()
    assert book.transfer("T-0001").state is TransferState.CLAIMED


def test_a_transfer_that_did_not_start_claimed_is_refused():
    book = TransferBook()
    with pytest.raises(ConflictError):
        book.claim(a_transfer(state=TransferState.CONFIRMED))


def test_a_master_does_not_move_between_people():
    book = TransferBook()
    with pytest.raises(ConflictError) as caught:
        book.claim(a_transfer(symbol="A"), is_master=True)
    assert caught.value.facts["symbol"] == "A"


def test_a_shop_that_allows_it_can_move_a_master():
    book = TransferBook(TransferPolicy(allow_master_transfer=True))
    assert book.claim(a_transfer(symbol="A"), is_master=True).symbol == "A"


def test_one_key_cannot_be_claimed_twice_at_once():
    book = a_book()
    with pytest.raises(ConflictError) as caught:
        book.claim(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu"))
    assert caught.value.facts["transfer"] == "T-0001"


def test_a_settled_key_can_be_claimed_again():
    book = a_book()
    book.confirm("T-0001", SOON)
    found = book.claim(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu"))
    assert found.state is TransferState.CLAIMED


def test_a_transfer_nobody_claimed_is_a_404():
    with pytest.raises(NotFoundError):
        TransferBook().transfer("T-9999")


def test_a_claim_does_not_move_the_key():
    book = a_book()
    assert not book.transfer("T-0001").moved
    assert book.transfer("T-0001").holder_on(SOON) == "kmaki"


def test_confirming_moves_the_key():
    book = a_book()
    found = book.confirm("T-0001", SOON, "picked it up on the monday")
    assert found.moved
    assert found.holder_on(SOON) == "rlaakso"


def test_the_key_was_still_with_the_old_holder_before_the_confirmation():
    book = a_book()
    found = book.confirm("T-0001", SOON)
    assert found.holder_on(CLAIMED) == "kmaki"


def test_a_confirmation_before_the_claim_is_refused():
    book = a_book()
    with pytest.raises(InputError):
        book.confirm("T-0001", date(2025, 1, 1))


def test_a_transfer_cannot_be_settled_twice():
    book = a_book()
    book.confirm("T-0001", SOON)
    with pytest.raises(ConflictError):
        book.confirm("T-0001", LATE)


def test_a_refusal_says_why():
    book = a_book()
    with pytest.raises(InputError):
        book.refuse("T-0001", SOON, "")


def test_a_refused_transfer_leaves_the_key_where_it_was():
    book = a_book()
    found = book.refuse("T-0001", SOON, "never got it")
    assert found.state is TransferState.REFUSED
    assert found.holder_on(LATE) == "kmaki"


def test_a_claim_can_be_taken_back():
    book = a_book()
    assert book.cancel("T-0001", SOON).state is TransferState.CANCELLED


def test_a_claim_inside_its_window_cannot_lapse():
    book = a_book()
    with pytest.raises(ConflictError) as caught:
        book.lapse("T-0001", SOON)
    assert caught.value.facts["due"] == "2025-03-18"


def test_a_claim_past_its_window_lapses():
    book = a_book()
    found = book.lapse("T-0001", LATE)
    assert found.state is TransferState.LAPSED
    assert found.note == "nobody confirmed inside the window"


def test_a_shorter_window_lapses_sooner():
    book = a_book(TransferPolicy(confirm_days=1))
    assert book.lapse("T-0001", SOON).state is TransferState.LAPSED


def test_a_late_claim_is_named():
    book = a_book()
    assert [item.number for item in book.late(LATE)] == ["T-0001"]


def test_a_settled_claim_is_never_late():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert book.late(LATE) == ()


def test_the_transfers_for_a_tag_come_back():
    book = a_book()
    book.confirm("T-0001", SOON)
    book.claim(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu"))
    assert len(book.for_tag("41207")) == 2


def test_the_transfers_in_a_state_come_back():
    book = a_book()
    assert len(book.in_state(TransferState.CLAIMED)) == 1


def test_what_a_person_claimed_to_have_handed_over():
    assert [item.number for item in a_book().claimed_by("kmaki")] == ["T-0001"]


def test_what_a_person_still_owes_a_signature_on():
    assert [item.number for item in a_book().waiting_on("rlaakso")] == ["T-0001"]


def test_a_settled_transfer_is_not_waiting_on_anybody():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert book.waiting_on("rlaakso") == ()


def test_who_the_shop_believes_has_a_key():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert book.holder_of("41207", LATE) == "rlaakso"


def test_a_key_nobody_moved_has_no_new_holder():
    assert a_book().holder_of("41207", LATE) is None


def test_a_day_before_the_move_has_no_new_holder():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert book.holder_of("41207", CLAIMED) is None


def test_the_chain_follows_a_key_through_hands():
    book = a_book()
    book.confirm("T-0001", SOON)
    book.claim(a_transfer("T-0002", passed_by="rlaakso", passed_to="jkoivu"))
    book.confirm("T-0002", LATE)
    assert [item.passed_to for item in book.chain_for("41207")] == ["rlaakso", "jkoivu"]


def test_an_unconfirmed_claim_is_not_in_the_chain():
    assert a_book().chain_for("41207") == ()


def test_the_exposure_counts_who_is_sitting_on_keys():
    book = a_book()
    book.claim(a_transfer("T-0002", tag="41208", passed_to="rlaakso"))
    assert unconfirmed_exposure(book, LATE) == {"rlaakso": 2}


def test_nothing_late_is_nothing_to_report():
    assert unconfirmed_exposure(a_book(), SOON) == {}


def test_a_key_that_ended_up_outside_the_department_is_named():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert keys_that_moved(book, ["kmaki", "jkoivu"]) == ("41207",)


def test_a_key_that_stayed_inside_is_not():
    book = a_book()
    book.confirm("T-0001", SOON)
    assert keys_that_moved(book, ["kmaki", "rlaakso"]) == ()


def test_the_book_counts_and_iterates():
    book = a_book()
    assert len(book) == 1
    assert "T-0001" in book
    assert [item.number for item in book] == ["T-0001"]


def test_a_transfer_reads_back_as_one_line():
    assert a_transfer().describe() == "T-0001 41207 AA1 kmaki to rlaakso claimed"


def test_a_loaded_transfer_keeps_the_state_it_was_in():
    book = TransferBook()
    book.load(a_transfer(state=TransferState.LAPSED))
    assert book.transfer("T-0001").state is TransferState.LAPSED

from datetime import date

import pytest

from hasplib.control.deposits import DepositBook, DepositEntry, EntryKind, dollars
from hasplib.errors import ConflictError, InputError, NotFoundError

WHEN = date(2025, 3, 4)
LATER = date(2025, 6, 2)


def a_book() -> DepositBook:
    book = DepositBook()
    book.charge("41207", "kmaki", 2500, WHEN)
    book.charge("41208", "dsalo", 2500, WHEN)
    book.charge("41209", "rvirta", 5000, WHEN)
    return book


def test_a_charge_puts_money_on_the_tag():
    book = a_book()
    assert book.held_on("41207") == 2500
    assert book.total_held() == 10000


def test_a_line_has_to_move_money():
    with pytest.raises(InputError):
        DepositEntry("41207", "kmaki", EntryKind.CHARGE, 0, WHEN)


def test_a_forfeit_says_why():
    with pytest.raises(InputError):
        DepositEntry("41207", "kmaki", EntryKind.FORFEIT, 2500, WHEN)


def test_a_tag_takes_one_deposit():
    book = a_book()
    with pytest.raises(ConflictError):
        book.charge("41207", "kmaki", 2500, WHEN)


def test_a_refund_gives_the_whole_thing_back_by_default():
    book = a_book()
    entry = book.refund("41207", LATER)
    assert entry.cents == 2500
    assert book.held_on("41207") == 0
    assert book.total_held() == 7500


def test_a_partial_refund_leaves_the_rest_with_the_shop():
    book = a_book()
    book.refund("41207", LATER, 1500)
    assert book.held_on("41207") == 1000


def test_a_refund_cannot_be_more_than_the_deposit():
    book = a_book()
    with pytest.raises(ConflictError) as caught:
        book.refund("41207", LATER, 4000)
    assert caught.value.facts["charged"] == 2500


def test_a_deposit_settles_once():
    book = a_book()
    book.refund("41207", LATER)
    with pytest.raises(ConflictError):
        book.refund("41207", LATER)


def test_a_forfeit_keeps_the_money_and_closes_the_tag():
    book = a_book()
    book.forfeit("41207", LATER, "key never came back")
    assert book.held_on("41207") == 0
    assert book.forfeited() == 2500
    assert book.is_closed("41207")


def test_a_forfeited_tag_cannot_then_be_refunded():
    book = a_book()
    book.forfeit("41207", LATER, "key never came back")
    with pytest.raises(ConflictError):
        book.refund("41207", LATER)


def test_a_waiver_clears_the_tag_without_counting_as_a_forfeit():
    book = a_book()
    book.waive("41209", LATER, "department pays centrally")
    assert book.held_on("41209") == 0
    assert book.forfeited() == 0


def test_settling_a_tag_nobody_charged():
    book = a_book()
    with pytest.raises(NotFoundError):
        book.refund("99999", LATER)


def test_a_persons_balance_covers_every_tag_they_hold():
    book = a_book()
    book.charge("41210", "kmaki", 2500, WHEN)
    assert book.balance_of("kmaki") == 5000
    book.refund("41207", LATER)
    assert book.balance_of("kmaki") == 2500


def test_somebody_with_no_deposits_has_no_balance():
    assert a_book().balance_of("phamm") == 0


def test_the_open_tags_are_the_ones_still_holding_money():
    book = a_book()
    book.refund("41207", LATER)
    assert book.open_tags() == ("41208", "41209")


def test_the_lines_on_a_tag_come_back_in_order():
    book = a_book()
    book.refund("41207", LATER, 1000)
    kinds = [entry.kind for entry in book.entries_for("41207")]
    assert kinds == [EntryKind.CHARGE, EntryKind.REFUND]


def test_the_lines_for_a_person_come_back_together():
    book = a_book()
    assert [entry.tag for entry in book.entries_of("kmaki")] == ["41207"]


def test_the_totals_split_by_what_the_line_did():
    book = a_book()
    book.refund("41207", LATER)
    book.forfeit("41208", LATER, "lost on a fishing boat")
    totals = book.by_kind()
    assert totals["charge"] == 10000
    assert totals["refund"] == 2500
    assert totals["forfeit"] == 2500


def test_the_book_counts_its_own_lines():
    book = a_book()
    assert len(book) == 3
    book.refund("41207", LATER)
    assert len(book) == 4
    assert len(list(book)) == 4


@pytest.mark.parametrize(
    ("cents", "printed"),
    [(0, "$0.00"), (5, "$0.05"), (2500, "$25.00"), (123456, "$1234.56"), (-2500, "-$25.00")],
)
def test_money_prints_the_way_a_receipt_does(cents, printed):
    assert dollars(cents) == printed

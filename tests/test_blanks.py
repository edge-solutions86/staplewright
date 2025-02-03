from datetime import date

import pytest

from hasplib.control.blanks import (
    BlankKind,
    BlankLedger,
    PurchaseLine,
    PurchaseOrder,
    StockLine,
    StockMove,
)
from hasplib.errors import CapacityError, ConflictError, InputError, NotFoundError

WHEN = date(2025, 3, 4)
LATER = date(2025, 5, 20)


def a_ledger() -> BlankLedger:
    ledger = BlankLedger()
    ledger.add_kind(BlankKind("LA-6", "LA", 6, supplier="Sargent", reorder_at=20))
    ledger.add_kind(BlankKind("LC-6", "LC", 6, restricted=True, reorder_at=10))
    ledger.add_kind(BlankKind("A2-7", "A2", 7, restricted=True, reorder_at=15))
    ledger.receive("LA-6", 100, WHEN, "PO-88")
    ledger.receive("LC-6", 40, WHEN, "PO-88")
    ledger.receive("A2-7", 12, WHEN, "PO-89")
    return ledger


def test_a_blank_needs_a_part_number_and_chambers():
    with pytest.raises(InputError):
        BlankKind(" ", "LA", 6)
    with pytest.raises(InputError):
        BlankKind("LA-6", "LA", 0)


def test_a_reorder_level_is_not_negative():
    with pytest.raises(InputError):
        BlankKind("LA-6", "LA", 6, reorder_at=-2)


def test_receiving_puts_blanks_in_the_drawer():
    assert a_ledger().on_hand("LA-6") == 100


def test_a_movement_moves_something():
    with pytest.raises(InputError):
        StockLine("LA-6", StockMove.RECEIVED, 0, WHEN)


def test_cutting_takes_them_out_again():
    ledger = a_ledger()
    ledger.cut("LA-6", 4, LATER)
    assert ledger.on_hand("LA-6") == 96


def test_a_restricted_blank_needs_something_to_point_at():
    ledger = a_ledger()
    with pytest.raises(ConflictError):
        ledger.cut("LC-6", 1, LATER)
    ledger.cut("LC-6", 1, LATER, "W-1041")
    assert ledger.on_hand("LC-6") == 39


def test_an_unrestricted_blank_needs_no_paperwork():
    ledger = a_ledger()
    ledger.cut("LA-6", 1, LATER)
    assert ledger.on_hand("LA-6") == 99


def test_the_drawer_cannot_be_overdrawn():
    ledger = a_ledger()
    with pytest.raises(CapacityError) as caught:
        ledger.cut("A2-7", 20, LATER, "W-1041")
    assert caught.value.facts["held"] == 12


def test_scrapping_says_why():
    ledger = a_ledger()
    with pytest.raises(InputError):
        ledger.scrap("LA-6", 2, LATER, " ")
    ledger.scrap("LA-6", 2, LATER, "milled the wrong way round")
    assert ledger.on_hand("LA-6") == 98


def test_scrapping_more_than_is_there_is_refused():
    ledger = a_ledger()
    with pytest.raises(CapacityError):
        ledger.scrap("A2-7", 30, LATER, "dropped the tray")


def test_a_blank_that_comes_back_goes_in_the_drawer():
    ledger = a_ledger()
    ledger.cut("LA-6", 5, LATER)
    ledger.put_back("LA-6", 2, LATER)
    assert ledger.on_hand("LA-6") == 97


def test_a_count_reports_the_difference_and_leaves_the_book_alone():
    ledger = a_ledger()
    assert ledger.count("LA-6", 96, LATER) == -4
    assert ledger.on_hand("LA-6") == 100


def test_a_count_that_agrees_reports_nothing():
    assert a_ledger().count("LA-6", 100, LATER) == 0


def test_a_negative_count_is_refused():
    with pytest.raises(InputError):
        a_ledger().count("LA-6", -1, LATER)


def test_a_part_nobody_added():
    ledger = a_ledger()
    with pytest.raises(NotFoundError):
        ledger.on_hand("ZZ-9")
    with pytest.raises(NotFoundError):
        ledger.receive("ZZ-9", 10, WHEN)


def test_a_part_cannot_be_added_twice():
    ledger = a_ledger()
    with pytest.raises(ConflictError):
        ledger.add_kind(BlankKind("LA-6", "LA", 6))


def test_the_lines_for_a_part_come_back_in_order():
    ledger = a_ledger()
    ledger.cut("LA-6", 3, LATER)
    moves = [line.move for line in ledger.lines_for("LA-6")]
    assert moves == [StockMove.RECEIVED, StockMove.CUT]


def test_the_restricted_parts_are_listed_on_their_own():
    parts = [kind.part_number for kind in a_ledger().restricted_kinds()]
    assert parts == ["A2-7", "LC-6"]


def test_the_reorder_list_puts_the_worst_shortage_first():
    ledger = a_ledger()
    ledger.cut("A2-7", 10, LATER, "W-1")
    ledger.cut("LC-6", 32, LATER, "W-2")
    found = ledger.below_reorder()
    assert [item[0] for item in found] == ["A2-7", "LC-6"]
    assert found[0][1] == 2


def test_a_part_above_its_level_is_not_on_the_list():
    assert a_ledger().below_reorder() == (("A2-7", 12, 15),)


def test_the_totals_cover_every_part():
    assert a_ledger().totals() == {"A2-7": 12, "LA-6": 100, "LC-6": 40}


def test_the_ledger_counts_its_own_lines():
    ledger = a_ledger()
    assert len(ledger) == 3
    assert "LA-6" in ledger
    assert "ZZ-9" not in ledger


def test_an_order_line_orders_something():
    with pytest.raises(InputError):
        PurchaseLine("LA-6", 0)


def test_more_cannot_arrive_than_was_ordered():
    with pytest.raises(ConflictError):
        PurchaseLine("LA-6", 10, received=11)


def test_an_order_needs_a_number_and_lines():
    with pytest.raises(InputError):
        PurchaseOrder(" ", "Sargent", [PurchaseLine("LA-6", 10)])
    with pytest.raises(ConflictError):
        PurchaseOrder("PO-90", "Sargent", [])


def test_a_part_cannot_be_on_an_order_twice():
    with pytest.raises(ConflictError):
        PurchaseOrder("PO-90", "Sargent", [PurchaseLine("LA-6", 10), PurchaseLine("LA-6", 5)])


def test_receiving_walks_the_order_down_to_nothing():
    order = PurchaseOrder("PO-90", "Sargent", [PurchaseLine("LA-6", 10), PurchaseLine("LC-6", 4)])
    assert not order.is_complete
    order.receive("LA-6", 10)
    assert [line.part_number for line in order.outstanding()] == ["LC-6"]
    order.receive("LC-6", 4)
    assert order.is_complete
    assert order.outstanding() == ()


def test_a_part_that_is_not_on_the_order():
    order = PurchaseOrder("PO-90", "Sargent", [PurchaseLine("LA-6", 10)])
    with pytest.raises(ConflictError):
        order.receive("ZZ-9", 1)


def test_over_receiving_is_refused():
    order = PurchaseOrder("PO-90", "Sargent", [PurchaseLine("LA-6", 10)])
    with pytest.raises(ConflictError):
        order.receive("LA-6", 11)


def test_the_lines_come_back_in_part_order():
    order = PurchaseOrder("PO-90", "Sargent", [PurchaseLine("LC-6", 4), PurchaseLine("LA-6", 10)])
    assert [line.part_number for line in order.lines()] == ["LA-6", "LC-6"]

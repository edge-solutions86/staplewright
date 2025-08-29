from datetime import date

import pytest

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import CapacityError, ConflictError, InputError, NotFoundError
from hasplib.persistence.models.reference import BlankRow, KeywaySectionRow
from hasplib.services.stock import CountResult, StockService

WHEN = date(2025, 4, 8)
LATER = date(2025, 5, 20)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    section = KeywaySectionRow(family_name="Sargent LA", code="L", manufacturer="Sargent")
    made.add(section)
    made.flush()
    made.add_all(
        [
            BlankRow(
                part_number="1145A",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=True,
                reorder_at=50,
            ),
            BlankRow(
                part_number="RA4",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=False,
                reorder_at=20,
            ),
        ]
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def stocked(session, part: str = "1145A", quantity: int = 200) -> StockService:
    service = StockService(session)
    service.receive(part, quantity, WHEN, reference="PO-4412")
    return service


def test_receiving_brass_puts_it_in_the_drawer(session):
    service = stocked(session)
    assert service.on_hand("1145A") == 200


def test_a_part_nobody_stocks_is_a_404(session):
    with pytest.raises(NotFoundError):
        StockService(session).receive("9999Z", 10, WHEN)


def test_receiving_nothing_is_refused(session):
    with pytest.raises(InputError):
        StockService(session).receive("1145A", 0, WHEN)


def test_cutting_takes_it_back_out(session):
    service = stocked(session)
    service.cut("1145A", 6, LATER, reference="41207")
    assert service.on_hand("1145A") == 194


def test_a_restricted_blank_does_not_move_without_a_reference(session):
    service = stocked(session)
    with pytest.raises(ConflictError):
        service.cut("1145A", 1, LATER)


def test_an_open_blank_moves_without_one(session):
    service = stocked(session, "RA4", 40)
    service.cut("RA4", 2, LATER)
    assert service.on_hand("RA4") == 38


def test_cutting_more_than_the_drawer_holds_is_refused(session):
    service = stocked(session, "RA4", 3)
    with pytest.raises(CapacityError) as caught:
        service.cut("RA4", 4, LATER)
    assert caught.value.facts["held"] == 3


def test_a_refused_draw_leaves_the_drawer_alone(session):
    service = stocked(session, "RA4", 3)
    with pytest.raises(CapacityError):
        service.cut("RA4", 4, LATER)
    assert service.on_hand("RA4") == 3


def test_scrapping_says_why(session):
    service = stocked(session)
    with pytest.raises(InputError):
        service.scrap("1145A", 2, LATER, "")


def test_scrapping_takes_it_off_the_total(session):
    service = stocked(session)
    service.scrap("1145A", 2, LATER, "milled wrong at the factory")
    assert service.on_hand("1145A") == 198


def test_a_blank_that_comes_back_goes_in_again(session):
    service = stocked(session)
    service.cut("1145A", 4, LATER, reference="41207")
    service.put_back("1145A", 1, LATER, reference="41207")
    assert service.on_hand("1145A") == 197


def test_the_ledger_total_agrees_with_the_column(session):
    service = stocked(session)
    service.cut("1145A", 4, LATER, reference="41207")
    assert service.ledger_total("1145A") == service.on_hand("1145A")


def test_a_count_that_agrees_says_so(session):
    service = stocked(session)
    found = service.count("1145A", 200, LATER)
    assert found.agrees
    assert found.describe() == "1145A counted 200, agrees"


def test_a_count_that_is_short_names_the_gap(session):
    service = stocked(session)
    found = service.count("1145A", 188, LATER)
    assert found.difference == -12
    assert found.describe() == "1145A counted 188, 12 short"


def test_a_count_that_is_over_says_over(session):
    service = stocked(session)
    assert service.count("1145A", 203, LATER).describe().endswith("3 over")


def test_a_count_moves_the_total_to_what_was_found(session):
    service = stocked(session)
    service.count("1145A", 188, LATER)
    assert service.on_hand("1145A") == 188


def test_a_count_leaves_a_line_behind(session):
    service = stocked(session)
    service.count("1145A", 188, LATER)
    assert [line.movement for line in service.movements("1145A")] == ["received", "counted"]


def test_a_count_of_less_than_nothing_is_refused(session):
    with pytest.raises(InputError):
        stocked(session).count("1145A", -1, LATER)


def test_a_count_does_not_move_the_ledger_total(session):
    service = stocked(session)
    service.count("1145A", 188, LATER)
    assert service.ledger_total("1145A") == 200


def test_the_reorder_list_is_shortest_first(session):
    service = stocked(session, "1145A", 10)
    service.receive("RA4", 19, WHEN)
    assert [part for part, _, _ in service.below_reorder()] == ["1145A", "RA4"]


def test_a_part_at_its_reorder_level_is_not_on_the_list(session):
    service = stocked(session, "1145A", 50)
    service.receive("RA4", 20, WHEN)
    assert service.below_reorder() == ()


def test_an_order_goes_in_with_a_line_per_part(session):
    service = StockService(session)
    order = service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 250)])
    assert order.state == "placed"
    assert [line.ordered for line in order.lines] == [250]


def test_an_order_with_nothing_on_it_is_refused(session):
    with pytest.raises(InputError):
        StockService(session).place_order("PO-9001", "lockwood supply", WHEN, [])


def test_an_order_number_is_not_reused(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 250)])
    with pytest.raises(ConflictError):
        service.place_order("PO-9001", "lockwood supply", WHEN, [("RA4", 10)])


def test_an_order_for_a_part_nobody_stocks_is_a_404(session):
    with pytest.raises(NotFoundError):
        StockService(session).place_order("PO-9001", "supply", WHEN, [("9999Z", 10)])


def test_booking_in_moves_the_stock_and_the_line(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 250)])
    line = service.book_in("PO-9001", "1145A", 100, LATER)
    assert line.received == 100
    assert service.on_hand("1145A") == 100


def test_a_part_receipt_leaves_the_order_part_received(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 250)])
    service.book_in("PO-9001", "1145A", 100, LATER)
    assert service.order("PO-9001").state == "part received"


def test_the_last_receipt_closes_the_order(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 250)])
    service.book_in("PO-9001", "1145A", 250, LATER)
    assert service.order("PO-9001").state == "received"


def test_more_arriving_than_was_ordered_is_refused(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 10)])
    with pytest.raises(CapacityError):
        service.book_in("PO-9001", "1145A", 11, LATER)


def test_booking_in_a_part_that_is_not_on_the_order(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 10)])
    with pytest.raises(NotFoundError):
        service.book_in("PO-9001", "RA4", 1, LATER)


def test_a_cancelled_order_takes_nothing_in(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 10)])
    service.cancel_order("PO-9001", "supplier cannot get the brass")
    with pytest.raises(ConflictError):
        service.book_in("PO-9001", "1145A", 1, LATER)


def test_an_order_that_arrived_cannot_be_cancelled(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 10)])
    service.book_in("PO-9001", "1145A", 10, LATER)
    with pytest.raises(ConflictError):
        service.cancel_order("PO-9001", "changed our mind")


def test_the_outstanding_orders_are_the_ones_still_coming(session):
    service = StockService(session)
    service.place_order("PO-9001", "lockwood supply", WHEN, [("1145A", 10)])
    service.place_order("PO-9002", "lockwood supply", WHEN, [("RA4", 10)])
    service.book_in("PO-9002", "RA4", 10, LATER)
    assert [row.number for row in service.orders.outstanding()] == ["PO-9001"]


def test_the_blanks_cut_against_a_tag_come_back(session):
    service = stocked(session)
    service.cut("1145A", 1, LATER, reference="41207")
    service.cut("1145A", 1, LATER, reference="41208")
    assert [line.reference for line in service.cut_against("41207")] == ["41207"]


def test_a_receipt_against_a_number_is_not_a_cut(session):
    service = stocked(session)
    assert service.cut_against("PO-4412") == ()


def test_the_totals_since_a_day_are_most_moved_first(session):
    service = stocked(session)
    service.receive("RA4", 100, WHEN)
    service.cut("1145A", 3, LATER, reference="41207")
    service.cut("RA4", 9, LATER)
    assert list(service.totals_since(LATER)) == ["RA4", "1145A"]


def test_a_day_after_everything_moved_is_empty(session):
    service = stocked(session)
    service.cut("1145A", 3, LATER, reference="41207")
    assert service.totals_since(date(2025, 12, 1)) == {}


def test_a_count_result_is_readable_on_its_own():
    assert CountResult("RA4", 10, 10).agrees

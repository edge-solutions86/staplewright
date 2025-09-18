from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.models.reference import BlankRow, KeywaySectionRow
from hasplib.persistence.models.stock import (
    PurchaseLineRow,
    PurchaseOrderRow,
    StockLineRow,
)

WHEN = date(2025, 4, 8)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_blank(session, part: str = "1145A") -> BlankRow:
    section = session.execute(select(KeywaySectionRow)).scalars().first()
    if section is None:
        section = KeywaySectionRow(family_name="Sargent LA", code="L", manufacturer="Sargent")
        session.add(section)
        session.flush()
    row = BlankRow(
        part_number=part,
        section_id=section.id,
        manufacturer="Sargent",
        chambers=6,
        restricted=True,
    )
    session.add(row)
    session.flush()
    return row


def a_line(session, **overrides) -> StockLineRow:
    settings = {
        "part_number": "1145A",
        "movement": "received",
        "quantity": 100,
        "moved_on": WHEN,
    }
    settings.update(overrides)
    row = StockLineRow(**settings)
    session.add(row)
    session.flush()
    return row


def an_order(session, number: str = "PO-4412") -> PurchaseOrderRow:
    row = PurchaseOrderRow(number=number, supplier="lockwood supply", placed_on=WHEN)
    session.add(row)
    session.flush()
    return row


def test_a_receipt_goes_in_and_comes_back(session):
    a_blank(session)
    row = a_line(session, reference="PO-4412")
    assert row.id is not None
    assert row.reference == "PO-4412"


def test_a_receipt_adds_to_the_running_total(session):
    a_blank(session)
    assert a_line(session).signed == 100


def test_cutting_takes_off_the_total(session):
    a_blank(session)
    assert a_line(session, movement="cut", quantity=4).signed == -4


def test_scrapping_takes_off_the_total(session):
    a_blank(session)
    line = a_line(session, movement="scrapped", quantity=2, note="milled wrong")
    assert line.signed == -2


def test_a_return_puts_stock_back(session):
    a_blank(session)
    assert a_line(session, movement="returned", quantity=3).signed == 3


def test_a_movement_of_nothing_is_refused(session):
    a_blank(session)
    with pytest.raises(IntegrityError):
        a_line(session, quantity=0)


def test_a_movement_nobody_recognises_is_refused(session):
    a_blank(session)
    with pytest.raises(IntegrityError):
        a_line(session, movement="borrowed")


def test_scrapping_without_a_reason_is_refused(session):
    a_blank(session)
    with pytest.raises(IntegrityError):
        a_line(session, movement="scrapped", quantity=1)


def test_a_line_against_a_part_nobody_stocks_is_refused(session):
    with pytest.raises(IntegrityError):
        a_line(session, part_number="9999Z")


def test_a_line_knows_which_blank_it_moved(session):
    a_blank(session)
    line = a_line(session)
    assert line.blank.manufacturer == "Sargent"


def test_an_order_starts_placed(session):
    assert an_order(session).state == "placed"


def test_an_order_state_nobody_recognises_is_refused(session):
    with pytest.raises(IntegrityError):
        session.add(
            PurchaseOrderRow(
                number="PO-1", supplier="lockwood supply", placed_on=WHEN, state="posted"
            )
        )
        session.flush()


def test_a_line_hangs_off_its_order(session):
    a_blank(session)
    order = an_order(session)
    session.add(PurchaseLineRow(order_number=order.number, part_number="1145A", ordered=250))
    session.flush()
    session.refresh(order)
    assert [line.part_number for line in order.lines] == ["1145A"]


def test_a_fresh_line_is_all_outstanding(session):
    a_blank(session)
    order = an_order(session)
    line = PurchaseLineRow(order_number=order.number, part_number="1145A", ordered=250)
    session.add(line)
    session.flush()
    assert line.outstanding == 250


def test_a_part_receipt_leaves_the_rest_outstanding(session):
    a_blank(session)
    order = an_order(session)
    line = PurchaseLineRow(
        order_number=order.number, part_number="1145A", ordered=250, received=100
    )
    session.add(line)
    session.flush()
    assert line.outstanding == 150


def test_more_arriving_than_was_ordered_is_refused(session):
    a_blank(session)
    order = an_order(session)
    with pytest.raises(IntegrityError):
        session.add(
            PurchaseLineRow(order_number=order.number, part_number="1145A", ordered=10, received=11)
        )
        session.flush()


def test_an_order_for_nothing_is_refused(session):
    a_blank(session)
    order = an_order(session)
    with pytest.raises(IntegrityError):
        session.add(PurchaseLineRow(order_number=order.number, part_number="1145A", ordered=0))
        session.flush()


def test_deleting_an_order_takes_its_lines_with_it(session):
    a_blank(session)
    order = an_order(session)
    session.add(PurchaseLineRow(order_number=order.number, part_number="1145A", ordered=250))
    session.flush()
    session.delete(order)
    session.flush()
    assert session.execute(select(PurchaseLineRow)).scalars().all() == []


def test_the_lines_for_a_part_come_back_in_the_order_they_happened(session):
    a_blank(session)
    a_line(session)
    a_line(session, movement="cut", quantity=2, moved_on=date(2025, 5, 1))
    found = session.execute(select(StockLineRow).order_by(StockLineRow.id)).scalars().all()
    assert [line.movement for line in found] == ["received", "cut"]


def test_a_count_line_moves_the_total_by_what_it_found(session):
    a_blank(session)
    line = a_line(session, movement="counted", quantity=97, adjustment=-3, note="annual count")
    assert line.quantity == 97
    assert line.signed == -3


def test_a_count_without_an_adjustment_is_refused(session):
    a_blank(session)
    with pytest.raises(IntegrityError):
        a_line(session, movement="counted", quantity=97, note="annual count")


def test_a_movement_that_is_not_a_count_carries_no_adjustment(session):
    a_blank(session)
    with pytest.raises(IntegrityError):
        a_line(session, movement="received", quantity=10, adjustment=2)


def test_a_count_that_agreed_moves_nothing(session):
    a_blank(session)
    line = a_line(session, movement="counted", quantity=97, adjustment=0)
    assert line.signed == 0

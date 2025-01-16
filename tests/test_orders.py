from datetime import date

import pytest

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.rekey.compromise import Cause, Incident, assess
from hasplib.rekey.orders import (
    LineState,
    OrderLine,
    OrderState,
    WorkOrder,
    WorkOrderBook,
    lines_from,
)
from hasplib.rekey.scope import decide
from keyed import a_coverage, a_ledger

WHEN = date(2025, 4, 8)
LATER = date(2025, 4, 15)


def an_order(**overrides) -> WorkOrder:
    settings = {
        "number": "W-1041",
        "incident": "I-2025-004",
        "lines": (
            OrderLine("C-1001", "MOO-214"),
            OrderLine("C-1002", "MOO-216"),
        ),
    }
    settings.update(overrides)
    return WorkOrder(**settings)


def a_book() -> WorkOrderBook:
    book = WorkOrderBook()
    book.raise_order(an_order())
    return book


def started() -> WorkOrderBook:
    book = a_book()
    book.schedule("W-1041", "jkoivu", WHEN)
    book.start("W-1041")
    return book


def test_an_order_needs_a_number_and_some_work():
    with pytest.raises(InputError):
        an_order(number=" ")
    with pytest.raises(ConflictError):
        an_order(lines=())


def test_a_cylinder_cannot_be_on_one_order_twice():
    with pytest.raises(ConflictError):
        an_order(lines=(OrderLine("C-1001", "MOO-214"), OrderLine("C-1001", "MOO-216")))


def test_a_new_order_is_drafted_with_everything_pending():
    order = a_book().order("W-1041")
    assert order.state is OrderState.DRAFTED
    assert len(order.pending) == 2
    assert order.openings == ("MOO-214", "MOO-216")
    assert "2 cylinders" in order.summary()


def test_a_scope_decision_turns_into_lines():
    coverage = a_coverage()
    incident = Incident("I-1", "41207", "AA1", Cause.LOST, WHEN)
    found = decide(assess(incident, coverage, a_ledger()), coverage)
    lines = lines_from(found, ["MOO-214", "MOO-216"])
    assert [line.serial for line in lines] == ["C-1001", "C-1002"]
    assert all(line.state is LineState.PENDING for line in lines)


def test_lines_need_a_door_each():
    coverage = a_coverage()
    incident = Incident("I-1", "41207", "AA1", Cause.LOST, WHEN)
    found = decide(assess(incident, coverage, a_ledger()), coverage)
    with pytest.raises(InputError):
        lines_from(found, ["MOO-214"])


def test_scheduling_names_a_technician_and_a_day():
    book = a_book()
    order = book.schedule("W-1041", "jkoivu", WHEN)
    assert order.state is OrderState.SCHEDULED
    assert order.technician == "jkoivu"
    assert order.scheduled_for == WHEN


def test_scheduling_can_be_redone_before_it_starts():
    book = a_book()
    book.schedule("W-1041", "jkoivu", WHEN)
    order = book.schedule("W-1041", "phamm", LATER)
    assert order.technician == "phamm"


def test_somebody_has_to_do_the_work():
    book = a_book()
    with pytest.raises(InputError):
        book.schedule("W-1041", "  ", WHEN)


def test_an_order_cannot_start_before_it_is_scheduled():
    book = a_book()
    with pytest.raises(ConflictError):
        book.start("W-1041")


def test_a_started_order_cannot_be_rescheduled():
    book = started()
    with pytest.raises(ConflictError):
        book.schedule("W-1041", "phamm", LATER)


def test_a_line_moves_through_pinned_and_installed():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.PINNED)
    assert book.order("W-1041").line("C-1001").state is LineState.PINNED
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    assert book.order("W-1041").line("C-1001").is_settled


def test_a_pinned_line_is_not_settled():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.PINNED)
    assert len(book.order("W-1041").pending) == 2


def test_a_skipped_line_says_why():
    book = started()
    with pytest.raises(InputError):
        book.mark_line("W-1041", "C-1001", LineState.SKIPPED)
    order = book.mark_line("W-1041", "C-1001", LineState.SKIPPED, "room occupied, exam in it")
    assert order.line("C-1001").note.startswith("room occupied")
    assert order.line("C-1001").is_settled


def test_a_line_cannot_be_moved_to_where_it_already_is():
    book = started()
    with pytest.raises(ConflictError):
        book.mark_line("W-1041", "C-1001", LineState.PENDING)


def test_a_cylinder_not_on_the_order():
    book = started()
    with pytest.raises(ConflictError):
        book.mark_line("W-1041", "C-9999", LineState.PINNED)


def test_an_order_will_not_close_with_a_pending_cylinder():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    with pytest.raises(ConflictError) as caught:
        book.close("W-1041", LATER)
    assert caught.value.facts["pending"] == ("C-1002",)


def test_an_order_closes_when_every_line_is_settled():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    book.mark_line("W-1041", "C-1002", LineState.SKIPPED, "wrong format, ordered a new one")
    order = book.close("W-1041", LATER)
    assert order.state is OrderState.DONE
    assert order.closed_on == LATER
    assert not order.is_open


def test_an_order_that_never_started_cannot_close():
    book = a_book()
    book.schedule("W-1041", "jkoivu", WHEN)
    with pytest.raises(ConflictError):
        book.close("W-1041", LATER)


def test_cancelling_says_why_and_keeps_the_lines():
    book = started()
    order = book.cancel("W-1041", LATER, "department paid for new hardware instead")
    assert order.state is OrderState.CANCELLED
    assert len(order.lines) == 2
    assert not order.is_open


def test_a_cancellation_without_a_reason_is_refused():
    book = started()
    with pytest.raises(InputError):
        book.cancel("W-1041", LATER, " ")


def test_a_closed_order_cannot_be_cancelled():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    book.mark_line("W-1041", "C-1002", LineState.INSTALLED)
    book.close("W-1041", LATER)
    with pytest.raises(ConflictError):
        book.cancel("W-1041", LATER, "changed our minds")


def test_an_order_number_cannot_be_reused():
    book = a_book()
    with pytest.raises(ConflictError):
        book.raise_order(an_order())


def test_an_order_cannot_be_filed_already_started():
    book = WorkOrderBook()
    with pytest.raises(ConflictError):
        book.raise_order(an_order(state=OrderState.STARTED))


def test_asking_for_an_order_that_is_not_there():
    with pytest.raises(NotFoundError):
        a_book().order("W-9999")


def test_orders_come_back_by_technician_incident_and_day():
    book = a_book()
    book.raise_order(an_order(number="W-1042", incident="I-2025-009"))
    book.schedule("W-1041", "jkoivu", WHEN)
    book.schedule("W-1042", "phamm", LATER)
    assert [o.number for o in book.for_technician("jkoivu")] == ["W-1041"]
    assert [o.number for o in book.for_incident("I-2025-009")] == ["W-1042"]
    assert [o.number for o in book.due_on(LATER)] == ["W-1042"]


def test_a_closed_order_is_not_due_any_more():
    book = started()
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    book.mark_line("W-1041", "C-1002", LineState.INSTALLED)
    book.close("W-1041", LATER)
    assert book.due_on(WHEN) == ()
    assert book.open_orders() == ()


def test_the_workload_counts_pending_cylinders_per_person():
    book = a_book()
    book.raise_order(an_order(number="W-1042"))
    book.schedule("W-1041", "jkoivu", WHEN)
    book.schedule("W-1042", "jkoivu", LATER)
    book.start("W-1041")
    book.mark_line("W-1041", "C-1001", LineState.INSTALLED)
    assert book.workload() == {"jkoivu": 3}


def test_an_unassigned_order_is_nobodys_workload():
    assert a_book().workload() == {}


def test_the_book_is_a_container():
    book = a_book()
    assert "W-1041" in book
    assert "W-9999" not in book
    assert len(book) == 1
    assert [o.number for o in book] == ["W-1041"]

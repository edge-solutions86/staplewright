from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.keys.bitting import Bitting
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    IncidentRow,
    RetiredBittingRow,
    WorkOrderLineRow,
    WorkOrderRow,
)

WHEN = date(2025, 4, 8)
LATER = date(2025, 4, 15)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def an_incident(session, number: str = "I-2025-004", **overrides) -> IncidentRow:
    settings = {
        "number": number,
        "tag": "41207",
        "symbol": "AA1",
        "cause": "lost",
        "reported_on": WHEN,
    }
    settings.update(overrides)
    row = IncidentRow(**settings)
    session.add(row)
    session.flush()
    return row


def an_order(session, number: str = "W-1041", **overrides) -> WorkOrderRow:
    settings = {"number": number}
    settings.update(overrides)
    row = WorkOrderRow(**settings)
    session.add(row)
    session.flush()
    return row


def test_the_rekey_tables_are_on_the_metadata():
    names = table_names()
    for wanted in ("incidents", "work_orders", "work_order_lines", "retired_bittings"):
        assert wanted in names


def test_an_incident_round_trips(session):
    row = an_incident(session)
    assert row.severity == "routine"
    assert row.orders == []
    assert "I-2025-004" in repr(row)


def test_a_theft_has_to_be_written_up(session):
    with pytest.raises(IntegrityError):
        an_incident(session, cause="stolen")


def test_a_theft_with_a_note_is_accepted(session):
    row = an_incident(session, cause="stolen", note="taken from a truck on a friday")
    assert row.cause == "stolen"


def test_an_order_hangs_off_an_incident(session):
    an_incident(session)
    order = an_order(session, incident_number="I-2025-004")
    assert order.incident.symbol == "AA1"
    assert order.state == "drafted"
    assert "W-1041" in repr(order)


def test_an_order_without_an_incident_is_allowed(session):
    order = an_order(session)
    assert order.incident is None


def test_a_scheduled_order_needs_a_day(session):
    with pytest.raises(IntegrityError):
        an_order(session, state="scheduled")


def test_a_scheduled_order_with_a_day_is_fine(session):
    order = an_order(session, state="scheduled", scheduled_for=LATER, technician="jkoivu")
    assert order.scheduled_for == LATER


def test_lines_hang_off_an_order(session):
    order = an_order(session)
    session.add_all(
        [
            WorkOrderLineRow(order_number=order.number, serial="C-1001", opening_number="MOO-214"),
            WorkOrderLineRow(order_number=order.number, serial="C-1002", opening_number="MOO-216"),
        ]
    )
    session.flush()
    session.refresh(order)
    assert len(order.lines) == 2
    assert len(order.pending) == 2
    assert "C-1001" in repr(order.lines[0])


def test_a_settled_line_drops_out_of_pending(session):
    order = an_order(session)
    line = WorkOrderLineRow(
        order_number=order.number, serial="C-1001", opening_number="MOO-214", state="installed"
    )
    session.add(line)
    session.flush()
    session.refresh(order)
    assert order.pending == ()


def test_a_cylinder_is_on_an_order_once(session):
    order = an_order(session)
    session.add(
        WorkOrderLineRow(order_number=order.number, serial="C-1001", opening_number="MOO-214")
    )
    session.flush()
    session.add(
        WorkOrderLineRow(order_number=order.number, serial="C-1001", opening_number="MOO-216")
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_same_cylinder_on_another_order_is_fine(session):
    first = an_order(session)
    second = an_order(session, "W-1042")
    session.add(
        WorkOrderLineRow(order_number=first.number, serial="C-1001", opening_number="MOO-214")
    )
    session.add(
        WorkOrderLineRow(order_number=second.number, serial="C-1001", opening_number="MOO-214")
    )
    session.flush()
    assert len(session.execute(select(WorkOrderLineRow)).scalars().all()) == 2


def test_deleting_an_order_takes_its_lines(session):
    order = an_order(session)
    session.add(
        WorkOrderLineRow(order_number=order.number, serial="C-1001", opening_number="MOO-214")
    )
    session.flush()
    session.delete(order)
    session.flush()
    assert session.execute(select(WorkOrderLineRow)).scalars().all() == []


def test_a_line_needs_an_order_that_exists(session):
    session.add(WorkOrderLineRow(order_number="W-9999", serial="C-1001", opening_number="MOO-214"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_retired_cut_round_trips(session):
    row = RetiredBittingRow(bitting=b("342156"), reason="compromised", retired_on=WHEN)
    session.add(row)
    session.flush()
    found = session.execute(select(RetiredBittingRow)).scalar_one()
    assert found.bitting == b("342156")
    assert found.is_permanent
    assert "342156" in repr(found)


def test_a_superseded_cut_is_not_permanent(session):
    row = RetiredBittingRow(bitting=b("362356"), reason="superseded", retired_on=WHEN)
    session.add(row)
    session.flush()
    assert not row.is_permanent


def test_a_cut_is_retired_once(session):
    session.add(RetiredBittingRow(bitting=b("342156"), reason="compromised", retired_on=WHEN))
    session.flush()
    session.add(RetiredBittingRow(bitting=b("342156"), reason="superseded", retired_on=LATER))
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_retired_table_stands_on_its_own(session):
    session.add(RetiredBittingRow(bitting=b("342156"), reason="compromised", retired_on=WHEN))
    session.flush()
    found = session.execute(select(RetiredBittingRow)).scalar_one()
    assert found.symbol == ""

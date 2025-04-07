from datetime import date

import pytest
from sqlalchemy import select

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.models import IssuedKeyRow, RetiredBittingRow, WorkOrderLineRow
from hasplib.rekey.compromise import Cause, Severity
from hasplib.rekey.scope import Scope, ScopePolicy
from hasplib.services.incidents import IncidentService

WHEN = date(2025, 4, 8)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_service(session, **policy) -> IncidentService:
    dbcampus.build(session)
    service = IncidentService(session, ScopePolicy(**policy) if policy else ScopePolicy())
    service.open_series()
    return service


def test_reporting_writes_the_incident_and_closes_the_key(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN, "left on a bus")
    assert row.number == "I-0001"
    assert row.symbol == "AA1"
    assert session.get(IssuedKeyRow, "41207").state == "lost"


def test_a_theft_is_recorded_as_a_theft(session):
    service = a_service(session)
    service.report("41207", Cause.STOLEN, WHEN, "taken from a coat in the hall")
    assert session.get(IssuedKeyRow, "41207").state == "stolen"


def test_a_theft_has_to_be_written_up(session):
    service = a_service(session)
    with pytest.raises(ConflictError):
        service.report("41207", Cause.STOLEN, WHEN)


def test_reporting_retires_the_cut(session):
    service = a_service(session)
    service.report("41207", Cause.LOST, WHEN)
    held = session.execute(select(RetiredBittingRow)).scalars().all()
    assert [row.bitting.render() for row in held] == ["620446"]
    assert held[0].reason == "compromised"


def test_the_shop_can_decline_to_retire_the_cut(session):
    service = a_service(session)
    service.report("41207", Cause.LOST, WHEN, retire_cut=False)
    assert session.execute(select(RetiredBittingRow)).scalars().all() == []


def test_a_cut_already_retired_is_not_retired_twice(session):
    service = a_service(session)
    service.report("41207", Cause.LOST, WHEN)
    service.report("41208", Cause.LOST, WHEN)
    held = session.execute(select(RetiredBittingRow)).scalars().all()
    assert len(held) == 1


def test_a_key_that_is_not_out_cannot_be_reported(session):
    service = a_service(session)
    service.report("41207", Cause.LOST, WHEN)
    with pytest.raises(ConflictError):
        service.report("41207", Cause.LOST, WHEN)


def test_a_tag_nobody_issued(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.report("99999", Cause.LOST, WHEN)


def test_the_severity_is_graded_when_it_is_reported(session):
    service = a_service(session)
    change = service.report("41207", Cause.LOST, WHEN)
    grand = service.report("41211", Cause.LOST, WHEN)
    assert change.severity == "routine"
    assert grand.severity == "critical"


def test_the_assessment_comes_back_off_the_record(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    found = service.assessment(row.number)
    assert found.openings == ("MOO-214", "MOO-216")
    assert found.severity is Severity.ROUTINE


def test_an_incident_nobody_reported(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.assessment("I-9999")


def test_the_plan_names_the_cylinders(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    plan = service.plan(row.number)
    assert plan.cylinders == ("C-1001", "C-1002")
    assert plan.scope is Scope.GROUP


def test_a_lost_grand_master_plans_a_system_rekey(session):
    service = a_service(session)
    row = service.report("41211", Cause.LOST, WHEN)
    assert service.plan(row.number).scope is Scope.SYSTEM


def test_the_order_carries_a_line_a_cylinder(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    order = service.raise_order(row.number)
    assert order.number == "W-1041"
    lines = session.execute(select(WorkOrderLineRow)).scalars().all()
    assert sorted(line.serial for line in lines) == ["C-1001", "C-1002"]
    assert {line.opening_number for line in lines} == {"MOO-214", "MOO-216"}


def test_the_order_points_back_at_the_incident(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    order = service.raise_order(row.number)
    assert order.incident_number == row.number
    assert order.state == "drafted"


def test_an_order_can_be_scheduled_as_it_is_raised(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    order = service.raise_order(row.number, date(2025, 4, 15))
    assert order.scheduled_for == date(2025, 4, 15)


def test_an_incident_that_calls_for_no_work_raises_no_order(session):
    service = a_service(session, ignore_below_openings=10, always_rekey_masters=False)
    row = service.report("41207", Cause.LOST, WHEN)
    with pytest.raises(ConflictError):
        service.raise_order(row.number)


def test_the_symbols_that_have_to_be_recut_come_back(session):
    service = a_service(session)
    row = service.report("41207", Cause.LOST, WHEN)
    assert [s.render() for s in service.symbols_recut(row.number)] == ["AA1"]


def test_two_incidents_get_consecutive_numbers(session):
    service = a_service(session)
    first = service.report("41207", Cause.LOST, WHEN)
    second = service.report("41208", Cause.LOST, WHEN)
    assert (first.number, second.number) == ("I-0001", "I-0002")

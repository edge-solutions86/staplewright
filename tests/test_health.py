from datetime import date

import pytest
from sqlalchemy import select

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.health import (
    Severity,
    counts_by_severity,
    exit_code,
    groups_with_no_cylinders,
    health_report,
    keys_held_by_lapsed_people,
    openings_with_no_cylinder,
    quarantined_cuts_in_use,
    run_checks,
    uncut_symbols,
    uninstalled_cylinders,
    unkeyed_openings,
    worst,
)
from hasplib.persistence.models.campus import CylinderRow
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.persistence.models.keying import GroupRow
from hasplib.persistence.models.rekey import RetiredBittingRow

TODAY = date(2025, 6, 30)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_cylinder(session, serial: str) -> CylinderRow:
    return session.execute(select(CylinderRow).where(CylinderRow.serial == serial)).scalar_one()


def test_a_healthy_campus_answers_for_nothing_serious(session):
    assert worst(run_checks(session, TODAY)) is not Severity.FAULT


def test_a_cylinder_installed_with_no_group_is_a_fault(session):
    a_cylinder(session, "C-1001").group_id = None
    session.flush()
    found = unkeyed_openings(session, TODAY)
    assert [item.subject for item in found] == ["C-1001"]
    assert found[0].severity is Severity.FAULT


def test_the_fault_says_which_door_it_is_on(session):
    a_cylinder(session, "C-1001").group_id = None
    session.flush()
    assert "installed at" in unkeyed_openings(session, TODAY)[0].detail


def test_a_keyed_core_on_the_shelf_is_only_a_note(session):
    row = a_cylinder(session, "C-1001")
    row.installed_at = None
    row.position = None
    session.flush()
    found = uninstalled_cylinders(session, TODAY)
    assert found[0].severity is Severity.NOTE


def test_nothing_on_the_shelf_is_nothing_to_report(session):
    assert uninstalled_cylinders(session, TODAY) == []


def test_a_key_out_to_somebody_who_has_left_is_a_fault(session):
    person = session.execute(select(KeyholderRow)).scalars().first()
    person.ends_on = date(2024, 1, 1)
    key = (
        session.execute(select(IssuedKeyRow).where(IssuedKeyRow.keyholder == person.identifier))
        .scalars()
        .first()
    )
    key.state = "out"
    session.flush()
    found = keys_held_by_lapsed_people(session, TODAY)
    assert found[0].severity is Severity.FAULT
    assert person.identifier in found[0].detail


def test_a_returned_key_from_somebody_who_has_left_is_fine(session):
    person = session.execute(select(KeyholderRow)).scalars().first()
    person.ends_on = date(2024, 1, 1)
    for key in session.execute(select(IssuedKeyRow)).scalars().all():
        key.state = "returned"
    session.flush()
    assert keys_held_by_lapsed_people(session, TODAY) == []


def test_a_quarantine_with_nothing_in_it_checks_nothing(session):
    assert quarantined_cuts_in_use(session, TODAY) == []


def test_a_cut_in_use_that_somebody_retired_is_a_fault(session):
    session.add(
        RetiredBittingRow(bitting="442446", reason="compromised", retired_on=date(2025, 1, 4))
    )
    session.flush()
    found = quarantined_cuts_in_use(session, TODAY)
    assert [item.subject for item in found] == ["GGM"]
    assert "compromised" in found[0].detail


def test_a_retired_cut_nobody_uses_is_quiet(session):
    session.add(
        RetiredBittingRow(bitting="909090", reason="superseded", retired_on=date(2025, 1, 4))
    )
    session.flush()
    assert quarantined_cuts_in_use(session, TODAY) == []


def test_a_group_with_nothing_keyed_to_it_is_a_warning(session):
    system_id = session.execute(select(GroupRow)).scalars().first().system_id
    session.add(GroupRow(system_id=system_id, name="the old wing"))
    session.flush()
    found = groups_with_no_cylinders(session, TODAY)
    assert "the old wing" in [item.subject for item in found]
    assert found[0].severity is Severity.WARNING


def test_a_keyed_door_with_nothing_in_it_is_a_warning(session):
    found = openings_with_no_cylinder(session, TODAY)
    assert all(item.severity is Severity.WARNING for item in found)


def test_a_door_that_takes_no_cylinder_is_never_reported(session):
    numbers = [item.subject for item in openings_with_no_cylinder(session, TODAY)]
    assert "MOO-100" not in numbers


def test_an_uncut_symbol_is_a_note_naming_its_system(session):
    from hasplib.persistence.models.keying import ScheduleEntryRow

    row = session.execute(
        select(ScheduleEntryRow).where(ScheduleEntryRow.symbol == "AA1")
    ).scalar_one()
    row.bitting = None
    session.flush()
    found = uncut_symbols(session, TODAY)
    assert found[0].subject == "AA1"
    assert found[0].detail == "drawn on chester park"


def test_the_findings_come_back_worst_first(session):
    a_cylinder(session, "C-1001").group_id = None
    session.flush()
    found = run_checks(session, TODAY)
    assert found[0].severity is Severity.FAULT


def test_a_caller_can_run_one_check_on_its_own(session):
    found = run_checks(session, TODAY, checks=[uninstalled_cylinders])
    assert found == []


def test_the_worst_of_nothing_is_nothing():
    assert worst([]) is None


def test_the_counts_cover_every_severity(session):
    counted = counts_by_severity(run_checks(session, TODAY))
    assert set(counted) == {"note", "warning", "fault"}


def test_a_clean_report_says_so(session):
    lines = health_report(session, TODAY)
    assert lines[0] == "self check at 2025-06-30"


def test_the_report_groups_the_findings_under_the_check(session):
    a_cylinder(session, "C-1001").group_id = None
    session.flush()
    lines = health_report(session, TODAY)
    assert "unmapped cylinders" in lines
    assert any(line.strip().startswith("fault") for line in lines)


def test_a_report_with_nothing_wrong_at_all(session):
    lines = health_report(
        session,
        TODAY,
    )
    assert lines[1].endswith("notes") or lines[1] == "nothing to answer for"


def test_the_exit_code_is_nought_unless_something_is_really_wrong(session):
    assert exit_code(run_checks(session, TODAY)) == 0


def test_a_fault_makes_the_exit_code_one(session):
    a_cylinder(session, "C-1001").group_id = None
    session.flush()
    assert exit_code(run_checks(session, TODAY)) == 1

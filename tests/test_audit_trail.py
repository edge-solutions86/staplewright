from datetime import datetime

import pytest
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import InputError
from hasplib.persistence.base import table_names
from hasplib.persistence.models import AuditEntryRow
from hasplib.services.audit import KEY, SYMBOL, AuditTrail

MORNING = datetime(2025, 4, 8, 9, 12)
NOON = datetime(2025, 4, 8, 12, 30)
EVENING = datetime(2025, 4, 8, 17, 5)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_trail(session) -> AuditTrail:
    trail = AuditTrail(session)
    trail.record("rvirta", "issued", KEY, "41207", MORNING, "against R-0001")
    trail.record("rvirta", "returned", KEY, "41207", EVENING)
    trail.record("phamm", "cut", SYMBOL, "AA1", NOON)
    return trail


def test_the_audit_table_is_on_the_metadata():
    assert "audit_entries" in table_names()


def test_an_entry_records_who_did_what_to_which(session):
    trail = AuditTrail(session)
    row = trail.record("rvirta", "issued", KEY, "41207", MORNING)
    assert row.actor == "rvirta"
    assert row.subject == "key/41207"
    assert "issued" in repr(row)


def test_an_entry_describes_itself_for_a_printed_trail(session):
    trail = AuditTrail(session)
    row = trail.record("rvirta", "issued", KEY, "41207", MORNING)
    assert row.describe().startswith("2025-04-08 09:12")
    assert "key/41207" in row.describe()


@pytest.mark.parametrize("blank", ["actor", "action", "subject_id"])
def test_an_entry_that_names_nothing_is_refused(session, blank):
    settings = {
        "actor": "rvirta",
        "action": "issued",
        "subject_kind": KEY,
        "subject_id": "41207",
        "when": MORNING,
    }
    settings[blank] = "  "
    with pytest.raises(InputError):
        AuditTrail(session).record(**settings)


def test_the_database_refuses_an_unnamed_actor_too(session):
    session.add(
        AuditEntryRow(
            happened_at=MORNING,
            actor="",
            action="issued",
            subject_kind=KEY,
            subject_id="41207",
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_everything_about_one_key_comes_back_in_order(session):
    trail = a_trail(session)
    found = trail.about(KEY, "41207")
    assert [row.action for row in found] == ["issued", "returned"]


def test_a_subject_nobody_touched_has_no_trail(session):
    assert a_trail(session).about(KEY, "99999") == []


def test_everything_one_person_did_comes_back_in_order(session):
    trail = a_trail(session)
    found = trail.by_actor("rvirta")
    assert [row.action for row in found] == ["issued", "returned"]
    assert trail.by_actor("nobody") == []


def test_a_window_returns_what_happened_inside_it(session):
    trail = a_trail(session)
    found = trail.between(MORNING, NOON)
    assert [row.action for row in found] == ["issued", "cut"]


def test_a_window_that_runs_backward_is_refused(session):
    with pytest.raises(InputError):
        a_trail(session).between(EVENING, MORNING)


def test_one_action_can_be_pulled_out(session):
    trail = a_trail(session)
    assert [row.subject for row in trail.of_action("cut")] == ["symbol/AA1"]


def test_the_actors_are_listed_once_each(session):
    assert a_trail(session).actors() == ("phamm", "rvirta")


def test_the_trail_counts_itself(session):
    assert a_trail(session).count() == 3


def test_an_empty_trail_counts_nothing(session):
    assert AuditTrail(session).count() == 0


def test_the_trail_prints_as_lines(session):
    trail = a_trail(session)
    lines = trail.lines(trail.about(KEY, "41207"))
    assert len(lines) == 2
    assert all("key/41207" in line for line in lines)


def test_two_entries_at_the_same_moment_keep_their_order(session):
    trail = AuditTrail(session)
    trail.record("rvirta", "first", KEY, "41207", MORNING)
    trail.record("rvirta", "second", KEY, "41207", MORNING)
    assert [row.action for row in trail.about(KEY, "41207")] == ["first", "second"]

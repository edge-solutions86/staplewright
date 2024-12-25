from datetime import date

import pytest

from hasplib.errors import InputError
from hasplib.rekey.compromise import (
    Assessment,
    Cause,
    Incident,
    Severity,
    assess,
    grade,
    worst_first,
)
from keyed import a_coverage, a_ledger

WHEN = date(2025, 4, 8)


def an_incident(**overrides) -> Incident:
    settings = {
        "number": "I-2025-004",
        "tag": "41207",
        "symbol": "AA1",
        "cause": Cause.LOST,
        "reported_on": WHEN,
        "keyholder": "kmaki",
    }
    settings.update(overrides)
    return Incident(**settings)


def test_an_incident_needs_a_number():
    with pytest.raises(InputError):
        an_incident(number="  ")


def test_a_theft_has_to_be_written_up():
    with pytest.raises(InputError):
        an_incident(cause=Cause.STOLEN)


def test_a_theft_with_a_note_is_fine():
    assert an_incident(cause=Cause.STOLEN, note="taken from a truck").cause is Cause.STOLEN


def test_a_change_key_to_two_offices_is_routine():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert found.openings == ("MOO-214", "MOO-216")
    assert found.severity is Severity.ROUTINE
    assert not found.is_master


def test_the_assessment_lists_the_cylinders_to_take_apart():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert found.cylinders == ("C-1001", "C-1002")


def test_the_other_keys_on_that_cut_have_to_be_recut():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert found.keys_to_recut == ("41208",)
    assert found.holders_affected == ("dsalo",)


def test_the_missing_key_is_not_in_its_own_recut_list():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert "41207" not in found.keys_to_recut


def test_a_master_is_graded_higher_than_a_change_key():
    found = assess(an_incident(tag="41209", symbol="AA"), a_coverage(), a_ledger())
    assert found.is_master
    assert found.severity is Severity.SERIOUS


def test_a_key_that_reaches_two_buildings_is_critical():
    found = assess(an_incident(tag="41211", symbol="GGM"), a_coverage(), a_ledger())
    assert found.buildings == ("HEI", "MOO")
    assert found.severity is Severity.CRITICAL


def test_an_exterior_door_shows_up_on_its_own_line():
    found = assess(an_incident(tag="41210", symbol="A"), a_coverage(), a_ledger())
    assert found.exterior_openings == ("MOO-101",)
    assert found.touches_outside


def test_a_key_that_reaches_nothing_is_still_assessable():
    found = assess(an_incident(symbol="ZZ9"), a_coverage(), a_ledger())
    assert found.openings == ()
    assert found.severity is Severity.ROUTINE


def test_the_summary_names_the_incident_and_the_grade():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert found.summary().startswith("I-2025-004 AA1 lost")
    assert "routine" in found.summary()


@pytest.mark.parametrize(
    ("openings", "buildings", "exterior", "master", "cause", "expected"),
    [
        (1, 1, False, False, Cause.LOST, Severity.ROUTINE),
        (5, 1, False, False, Cause.LOST, Severity.ROUTINE),
        (6, 1, False, False, Cause.LOST, Severity.ELEVATED),
        (1, 1, True, False, Cause.LOST, Severity.ELEVATED),
        (1, 1, False, False, Cause.STOLEN, Severity.ELEVATED),
        (21, 1, False, False, Cause.LOST, Severity.SERIOUS),
        (3, 1, False, True, Cause.LOST, Severity.SERIOUS),
        (3, 2, False, True, Cause.LOST, Severity.CRITICAL),
        (1, 1, True, False, Cause.STOLEN, Severity.CRITICAL),
    ],
)
def test_the_ladder_reads_the_same_way_every_time(
    openings, buildings, exterior, master, cause, expected
):
    assert grade(openings, buildings, exterior, master, cause) is expected


def test_a_master_inside_one_building_is_not_automatically_critical():
    assert grade(4, 1, False, True, Cause.LOST) is Severity.SERIOUS


def test_the_morning_meeting_gets_the_worst_one_first():
    coverage = a_coverage()
    ledger = a_ledger()
    found = worst_first(
        (
            assess(an_incident(number="I-1"), coverage, ledger),
            assess(an_incident(number="I-2", tag="41211", symbol="GGM"), coverage, ledger),
            assess(an_incident(number="I-3", tag="41209", symbol="AA"), coverage, ledger),
        )
    )
    assert [item.incident.number for item in found] == ["I-2", "I-3", "I-1"]


def test_two_incidents_at_the_same_grade_sort_by_reach():
    coverage = a_coverage()
    ledger = a_ledger()
    first = assess(an_incident(number="I-1"), coverage, ledger)
    second = assess(an_incident(number="I-2", symbol="ZZ9"), coverage, ledger)
    assert worst_first((second, first))[0].incident.number == "I-1"


def test_an_assessment_is_a_plain_record():
    found = assess(an_incident(), a_coverage(), a_ledger())
    assert isinstance(found, Assessment)
    assert found.opening_count == 2

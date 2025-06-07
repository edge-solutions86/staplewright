from datetime import date, datetime

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    BuildingRow,
    LockoutRow,
    OpeningRow,
    SurveyLineRow,
    SurveyRow,
)

CALLED = datetime(2025, 4, 8, 14, 12)
ATTENDED = datetime(2025, 4, 8, 14, 31)
WALKED = date(2025, 5, 12)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    made.add(BuildingRow(code="MOO", name="Moore Hall"))
    made.flush()
    made.add_all(
        [
            OpeningRow(
                number="MOO-214", building_code="MOO", floor="2", room="214", function="office"
            ),
            OpeningRow(
                number="MOO-216", building_code="MOO", floor="2", room="216", function="office"
            ),
        ]
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def a_call(session, number: str = "L-001", **overrides) -> LockoutRow:
    settings = {
        "number": number,
        "opening_number": "MOO-214",
        "keyholder": "kmaki",
        "called_at": CALLED,
    }
    settings.update(overrides)
    row = LockoutRow(**settings)
    session.add(row)
    session.flush()
    return row


def a_survey(session, **overrides) -> SurveyRow:
    settings = {"building_code": "MOO", "walked_on": WALKED}
    settings.update(overrides)
    row = SurveyRow(**settings)
    session.add(row)
    session.flush()
    return row


def test_the_facilities_tables_are_on_the_metadata():
    names = table_names()
    for wanted in ("lockouts", "surveys", "survey_lines"):
        assert wanted in names


def test_a_call_round_trips(session):
    row = a_call(session)
    assert row.state == "logged"
    assert row.is_open
    assert "L-001" in repr(row)


def test_nobody_attended_before_the_call(session):
    with pytest.raises(IntegrityError):
        a_call(session, attended_at=datetime(2025, 4, 8, 13, 0))


def test_an_attended_call_names_a_technician(session):
    with pytest.raises(IntegrityError):
        a_call(session, state="attended", attended_at=ATTENDED)


def test_an_attended_call_with_a_technician_is_fine(session):
    row = a_call(session, state="attended", attended_at=ATTENDED, technician="jkoivu")
    assert row.technician == "jkoivu"


def test_a_call_needs_a_door_that_exists(session):
    with pytest.raises(IntegrityError):
        a_call(session, opening_number="ZZZ-1")


def test_a_closed_call_is_not_open(session):
    row = a_call(session, state="opened")
    assert not row.is_open


def test_a_survey_covers_a_building_on_a_day(session):
    row = a_survey(session)
    assert row.lines == []
    assert "MOO" in repr(row)


def test_a_building_is_surveyed_once_a_day(session):
    a_survey(session)
    with pytest.raises(IntegrityError):
        a_survey(session)


def test_the_same_building_on_another_day_is_fine(session):
    a_survey(session)
    a_survey(session, walked_on=date(2025, 5, 13))
    assert len(session.execute(select(SurveyRow)).scalars().all()) == 2


def test_a_survey_needs_a_building_that_exists(session):
    with pytest.raises(IntegrityError):
        a_survey(session, building_code="ZZZ")


def test_lines_hang_off_a_survey(session):
    survey = a_survey(session)
    session.add_all(
        [
            SurveyLineRow(
                survey_id=survey.id,
                opening_number="MOO-214",
                surveyor="jkoivu",
                condition=1,
            ),
            SurveyLineRow(
                survey_id=survey.id,
                opening_number="MOO-216",
                surveyor="jkoivu",
                condition=3,
                deficiencies="worn out,unidentified",
            ),
        ]
    )
    session.flush()
    session.refresh(survey)
    assert len(survey.lines) == 2


def test_the_deficiencies_read_back_as_a_list(session):
    survey = a_survey(session)
    row = SurveyLineRow(
        survey_id=survey.id,
        opening_number="MOO-214",
        surveyor="jkoivu",
        condition=2,
        deficiencies="worn out,unidentified",
    )
    session.add(row)
    session.flush()
    assert row.deficiency_list == ("worn out", "unidentified")


def test_a_line_with_no_deficiencies_reads_back_empty(session):
    survey = a_survey(session)
    row = SurveyLineRow(
        survey_id=survey.id, opening_number="MOO-214", surveyor="jkoivu", condition=1
    )
    session.add(row)
    session.flush()
    assert row.deficiency_list == ()


def test_a_door_is_surveyed_once_per_walk(session):
    survey = a_survey(session)
    session.add(
        SurveyLineRow(survey_id=survey.id, opening_number="MOO-214", surveyor="jkoivu", condition=1)
    )
    session.flush()
    session.add(
        SurveyLineRow(survey_id=survey.id, opening_number="MOO-214", surveyor="phamm", condition=2)
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_condition_outside_the_scale_is_refused(session):
    survey = a_survey(session)
    session.add(
        SurveyLineRow(survey_id=survey.id, opening_number="MOO-214", surveyor="jkoivu", condition=9)
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_survey_takes_its_lines(session):
    survey = a_survey(session)
    session.add(
        SurveyLineRow(survey_id=survey.id, opening_number="MOO-214", surveyor="jkoivu", condition=1)
    )
    session.flush()
    session.delete(survey)
    session.flush()
    assert session.execute(select(SurveyLineRow)).scalars().all() == []

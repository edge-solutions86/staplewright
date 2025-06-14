from datetime import date, datetime

import pytest
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.lockouts import CallState, Identification, Lockout, LockoutLog
from hasplib.campus.survey import Condition, Deficiency, Survey, SurveyLine
from hasplib.errors import InputError
from hasplib.persistence.mappers.facilities import (
    deficiencies_from_text,
    deficiencies_to_text,
    lockout_from_row,
    lockout_to_row,
    log_from_rows,
    survey_from_rows,
    survey_line_from_row,
    survey_line_to_row,
    survey_to_rows,
)
from hasplib.persistence.models import BuildingRow, LockoutRow, OpeningRow, SurveyRow

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


def a_call(**overrides) -> Lockout:
    settings = {
        "number": "L-001",
        "opening": "MOO-214",
        "keyholder": "kmaki",
        "called_at": CALLED,
    }
    settings.update(overrides)
    return Lockout(**settings)


def a_line(opening: str = "MOO-214", **overrides) -> SurveyLine:
    settings = {
        "opening": opening,
        "surveyed_on": WALKED,
        "surveyor": "jkoivu",
        "condition": Condition.GOOD,
    }
    settings.update(overrides)
    return SurveyLine(**settings)


def test_a_call_survives_a_round_trip():
    call = a_call()
    assert lockout_from_row(lockout_to_row(call)) == call


def test_an_attended_call_survives():
    call = a_call(state=CallState.ATTENDED, attended_at=ATTENDED, technician="jkoivu")
    back = lockout_from_row(lockout_to_row(call))
    assert back.response_minutes() == 19
    assert back.state is CallState.ATTENDED


def test_the_identification_survives():
    call = a_call(
        state=CallState.OPENED,
        attended_at=ATTENDED,
        technician="jkoivu",
        identified_by=Identification.CAMPUS_CARD,
    )
    assert lockout_from_row(lockout_to_row(call)).identified_by is Identification.CAMPUS_CARD


def test_an_identification_nobody_has_heard_of_reads_as_none():
    row = lockout_to_row(a_call())
    row.identified_by = "by the cut of his jib"
    assert lockout_from_row(row).identified_by is Identification.NONE


def test_a_state_nobody_has_heard_of_is_refused():
    row = lockout_to_row(a_call())
    row.state = "pondered"
    with pytest.raises(InputError):
        lockout_from_row(row)


def test_a_whole_log_comes_back_in_call_order(session):
    log = LockoutLog()
    log.log(a_call(number="L-002", called_at=datetime(2025, 4, 8, 9, 0)))
    log.log(a_call())
    for call in log:
        session.add(lockout_to_row(call))
    session.flush()
    rows = session.execute(select(LockoutRow)).scalars().all()
    back = log_from_rows(rows)
    assert [call.number for call in back] == ["L-002", "L-001"]


def test_the_deficiencies_go_out_as_their_own_words():
    text = deficiencies_to_text((Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED))
    assert text == "hardware needs replacing before it is rekeyed,cylinder cannot be identified"


def test_the_deficiencies_come_back_as_members():
    text = deficiencies_to_text((Deficiency.WORN_OUT, Deficiency.NO_STRIKE))
    assert deficiencies_from_text(text) == (Deficiency.WORN_OUT, Deficiency.NO_STRIKE)


def test_a_word_this_version_does_not_know_is_dropped():
    text = "hardware needs replacing before it is rekeyed,something from 2014"
    assert deficiencies_from_text(text) == (Deficiency.WORN_OUT,)


def test_a_word_twice_is_kept_once():
    text = "cylinder cannot be identified,cylinder cannot be identified"
    assert deficiencies_from_text(text) == (Deficiency.UNIDENTIFIED,)


def test_an_empty_column_comes_back_empty():
    assert deficiencies_from_text("") == ()


def test_a_survey_line_survives_a_round_trip():
    line = a_line(condition=Condition.FAILING, deficiencies=(Deficiency.WORN_OUT,))
    assert survey_line_from_row(survey_line_to_row(line, 1), WALKED) == line


def test_a_whole_survey_survives_the_database(session):
    survey = Survey("MOO", WALKED)
    survey.record(a_line("MOO-214"))
    survey.record(a_line("MOO-216", condition=Condition.BROKEN))
    header, lines = survey_to_rows(survey)
    session.add(header)
    session.flush()
    for row in lines:
        row.survey_id = header.id
        session.add(row)
    session.flush()
    stored = session.execute(select(SurveyRow)).scalar_one()
    back = survey_from_rows(stored)
    assert len(back) == 2
    assert back.line("MOO-216").condition is Condition.BROKEN


def test_the_survey_header_carries_the_building_and_the_day():
    survey = Survey("MOO", WALKED)
    header, lines = survey_to_rows(survey)
    assert header.building_code == "MOO"
    assert header.walked_on == WALKED
    assert lines == []

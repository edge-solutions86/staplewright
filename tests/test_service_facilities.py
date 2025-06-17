from datetime import date, datetime

import pytest

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.lockouts import Identification
from hasplib.campus.survey import Condition, Deficiency
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.services.facilities import LockoutService, SurveyService

CALLED = datetime(2025, 4, 8, 14, 12)
ATTENDED = datetime(2025, 4, 8, 14, 31)
CLOSED = datetime(2025, 4, 8, 14, 35)
WALKED = date(2025, 5, 12)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_lockout_service(session) -> LockoutService:
    service = LockoutService(session)
    service.open_series()
    return service


def a_logged_call(service, opening: str = "MOO-214"):
    return service.log(opening, "kmaki", CALLED)


def test_a_call_gets_the_next_number(session):
    service = a_lockout_service(session)
    assert a_logged_call(service).number == "L-00001"
    assert a_logged_call(service).number == "L-00002"


def test_a_call_starts_logged(session):
    service = a_lockout_service(session)
    assert a_logged_call(service).state == "logged"


def test_a_call_names_somebody(session):
    service = a_lockout_service(session)
    with pytest.raises(InputError):
        service.log("MOO-214", "  ", CALLED)


def test_a_call_about_a_door_nobody_has(session):
    service = a_lockout_service(session)
    with pytest.raises(NotFoundError):
        service.log("ZZZ-1", "kmaki", CALLED)


def test_attending_records_who_and_when(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    row = service.attend(number, "jkoivu", ATTENDED)
    assert row.state == "attended"
    assert row.technician == "jkoivu"


def test_somebody_has_to_attend(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    with pytest.raises(InputError):
        service.attend(number, " ", ATTENDED)


def test_nobody_attends_before_the_call(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    with pytest.raises(ConflictError):
        service.attend(number, "jkoivu", datetime(2025, 4, 8, 13, 0))


def test_a_door_cannot_be_opened_before_anybody_attends(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    with pytest.raises(ConflictError):
        service.open_door(number, Identification.CAMPUS_CARD, CLOSED)


def test_opening_records_the_identification(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    service.attend(number, "jkoivu", ATTENDED)
    row = service.open_door(number, Identification.ROSTER, CLOSED)
    assert row.state == "opened"
    assert row.identified_by == Identification.ROSTER.value


def test_an_unidentified_person_does_not_get_let_in(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    service.attend(number, "jkoivu", ATTENDED)
    with pytest.raises(ConflictError):
        service.open_door(number, Identification.NONE, CLOSED)


def test_a_refusal_says_why(session):
    service = a_lockout_service(session)
    number = a_logged_call(service).number
    service.attend(number, "jkoivu", ATTENDED)
    with pytest.raises(InputError):
        service.refuse(number, CLOSED, "  ")
    assert service.refuse(number, CLOSED, "not on the roster").state == "refused"


def test_a_call_that_is_not_there(session):
    service = a_lockout_service(session)
    with pytest.raises(NotFoundError):
        service.attend("L-99999", "jkoivu", ATTENDED)


def test_the_log_book_reads_back(session):
    service = a_lockout_service(session)
    a_logged_call(service)
    a_logged_call(service, "MOO-216")
    book = service.log_book()
    assert len(book) == 2
    assert book.counts_by_opening()["MOO-214"] == 1


def test_a_survey_starts_once_a_day(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    with pytest.raises(ConflictError):
        service.start("MOO", WALKED)


def test_the_same_building_on_another_day_is_a_new_survey(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    service.start("MOO", date(2025, 5, 13))
    assert service.survey("MOO", date(2025, 5, 13)) is not None


def test_a_line_is_recorded_against_the_walk(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    row = service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.WORN)
    assert row.condition == 2
    assert service.survey("MOO", WALKED).line("MOO-214").condition is Condition.WORN


def test_a_second_look_corrects_the_line(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.WORN)
    service.record("MOO", WALKED, "MOO-214", "phamm", Condition.BROKEN)
    survey = service.survey("MOO", WALKED)
    assert len(survey) == 1
    assert survey.line("MOO-214").condition is Condition.BROKEN
    assert survey.line("MOO-214").surveyor == "phamm"


def test_the_deficiencies_survive_a_correction(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    service.record(
        "MOO",
        WALKED,
        "MOO-214",
        "jkoivu",
        Condition.FAILING,
        (Deficiency.WORN_OUT,),
    )
    line = service.survey("MOO", WALKED).line("MOO-214")
    assert line.deficiencies == (Deficiency.WORN_OUT,)
    assert line.needs_work_first


def test_a_line_on_a_walk_that_never_happened(session):
    service = SurveyService(session)
    with pytest.raises(NotFoundError):
        service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.GOOD)


def test_the_latest_survey_is_the_most_recent_walk(session):
    service = SurveyService(session)
    service.start("MOO", WALKED)
    service.start("MOO", date(2025, 9, 1))
    service.record("MOO", date(2025, 9, 1), "MOO-214", "jkoivu", Condition.BROKEN)
    latest = service.latest("MOO")
    assert latest.walked_on == date(2025, 9, 1)
    assert len(latest) == 1


def test_a_building_nobody_walked_has_no_latest(session):
    assert SurveyService(session).latest("VOS") is None

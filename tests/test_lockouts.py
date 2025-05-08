from datetime import datetime

import pytest

from hasplib.campus.lockouts import (
    CallState,
    Identification,
    Lockout,
    LockoutLog,
)
from hasplib.errors import ConflictError, InputError, NotFoundError

CALLED = datetime(2025, 4, 8, 14, 12)
ATTENDED = datetime(2025, 4, 8, 14, 31)
CLOSED = datetime(2025, 4, 8, 14, 35)
NIGHT = datetime(2025, 4, 8, 22, 40)


def a_call(number: str = "L-001", **overrides) -> Lockout:
    settings = {
        "number": number,
        "opening": "MOO-214",
        "keyholder": "kmaki",
        "called_at": CALLED,
    }
    settings.update(overrides)
    return Lockout(**settings)


def a_log() -> LockoutLog:
    log = LockoutLog()
    log.log(a_call())
    return log


def attended(log: LockoutLog, number: str = "L-001") -> LockoutLog:
    log.attend(number, "jkoivu", ATTENDED)
    return log


def test_a_call_needs_a_number():
    with pytest.raises(InputError):
        a_call(" ")


def test_a_new_call_is_open():
    call = a_log().call("L-001")
    assert call.state is CallState.LOGGED
    assert call.is_open
    assert call.response_minutes() is None


def test_a_call_describes_itself_for_the_log():
    assert a_log().call("L-001").describe().startswith("L-001 MOO-214 kmaki")


def test_a_call_cannot_be_logged_already_attended():
    with pytest.raises(ConflictError):
        LockoutLog().log(a_call(state=CallState.ATTENDED))


def test_attending_records_who_went_and_when():
    log = attended(a_log())
    call = log.call("L-001")
    assert call.technician == "jkoivu"
    assert call.response_minutes() == 19


def test_somebody_has_to_attend():
    log = a_log()
    with pytest.raises(InputError):
        log.attend("L-001", "  ", ATTENDED)


def test_nobody_attended_before_the_call():
    log = a_log()
    with pytest.raises(ConflictError):
        log.attend("L-001", "jkoivu", datetime(2025, 4, 8, 13, 0))


def test_a_call_cannot_be_attended_twice():
    log = attended(a_log())
    with pytest.raises(ConflictError):
        log.attend("L-001", "phamm", ATTENDED)


def test_opening_the_door_records_how_they_were_identified():
    log = attended(a_log())
    call = log.open_door("L-001", Identification.CAMPUS_CARD, CLOSED)
    assert call.state is CallState.OPENED
    assert call.identified_by is Identification.CAMPUS_CARD
    assert not call.is_open


def test_an_unidentified_person_does_not_get_let_in():
    log = attended(a_log())
    with pytest.raises(ConflictError):
        log.open_door("L-001", Identification.NONE, CLOSED)


def test_a_door_cannot_be_opened_before_anybody_attends():
    log = a_log()
    with pytest.raises(ConflictError):
        log.open_door("L-001", Identification.CAMPUS_CARD, CLOSED)


def test_a_refusal_says_why():
    log = attended(a_log())
    with pytest.raises(InputError):
        log.refuse("L-001", CLOSED, " ")
    call = log.refuse("L-001", CLOSED, "not on the roster and nobody would vouch")
    assert call.state is CallState.REFUSED


def test_a_call_can_be_withdrawn_before_anybody_goes():
    log = a_log()
    call = log.withdraw("L-001", CLOSED, "found the key in a coat")
    assert call.state is CallState.WITHDRAWN
    assert not call.is_open


def test_a_closed_call_cannot_be_withdrawn():
    log = attended(a_log())
    log.open_door("L-001", Identification.ROSTER, CLOSED)
    with pytest.raises(ConflictError):
        log.withdraw("L-001", CLOSED)


def test_a_call_that_is_not_there():
    with pytest.raises(NotFoundError):
        a_log().call("L-999")


def test_a_daytime_call_is_not_after_hours():
    assert not a_log().call("L-001").after_hours


@pytest.mark.parametrize("hour", [17, 22, 2, 6])
def test_an_evening_or_night_call_is_after_hours(hour):
    call = a_call(called_at=datetime(2025, 4, 8, hour, 0))
    assert call.after_hours


def test_the_after_hours_list_is_only_those():
    log = a_log()
    log.log(a_call("L-002", called_at=NIGHT))
    assert [call.number for call in log.after_hours()] == ["L-002"]


def test_the_open_calls_are_the_ones_nobody_closed():
    log = attended(a_log())
    log.log(a_call("L-002"))
    log.open_door("L-001", Identification.ROSTER, CLOSED)
    assert [call.number for call in log.open_calls()] == ["L-002"]


def test_calls_come_back_by_door_and_by_person():
    log = a_log()
    log.log(a_call("L-002", opening="MOO-216", keyholder="dsalo"))
    assert [c.number for c in log.for_opening("MOO-214")] == ["L-001"]
    assert [c.number for c in log.for_keyholder("dsalo")] == ["L-002"]


def test_the_worst_door_comes_first():
    log = a_log()
    log.log(a_call("L-002"))
    log.log(a_call("L-003", opening="MOO-216"))
    counts = log.counts_by_opening()
    assert list(counts) == ["MOO-214", "MOO-216"]
    assert counts["MOO-214"] == 2


def test_a_repeat_caller_is_named():
    log = a_log()
    log.log(a_call("L-002"))
    log.log(a_call("L-003"))
    log.log(a_call("L-004", keyholder="dsalo"))
    assert log.repeat_callers() == ("kmaki",)
    assert log.repeat_callers(at_least=1) == ("dsalo", "kmaki")


def test_the_average_response_is_over_the_attended_calls():
    log = attended(a_log())
    log.log(a_call("L-002"))
    log.attend("L-002", "jkoivu", datetime(2025, 4, 8, 14, 42))
    assert log.average_response() == 24


def test_nothing_attended_has_no_average():
    assert a_log().average_response() is None


def test_the_log_is_a_container():
    log = a_log()
    assert "L-001" in log
    assert "L-999" not in log
    assert len(log) == 1
    assert [call.number for call in log] == ["L-001"]

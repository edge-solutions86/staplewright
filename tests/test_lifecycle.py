from enum import StrEnum

import pytest

from hasplib.errors import ConflictError
from hasplib.lifecycle import require, require_state, state_of


class Where(StrEnum):
    OPEN = "open"
    SHUT = "shut"
    GONE = "gone"


def test_a_state_that_is_allowed_passes_quietly():
    assert require_state(Where.OPEN, {Where.OPEN, Where.SHUT}, "no") is None


def test_a_state_that_is_not_allowed_refuses():
    with pytest.raises(ConflictError) as caught:
        require_state(Where.GONE, {Where.OPEN}, "the thing is not open", number="W-1")
    assert caught.value.message == "the thing is not open"


def test_the_state_always_reaches_the_facts():
    with pytest.raises(ConflictError) as caught:
        require_state(Where.GONE, {Where.OPEN}, "no")
    assert caught.value.facts["state"] == "gone"


def test_the_allowed_states_reach_the_facts_too():
    with pytest.raises(ConflictError) as caught:
        require_state(Where.GONE, {Where.OPEN}, "no")
    assert caught.value.facts["allowed"] == ("open",)


def test_the_callers_own_facts_come_through():
    with pytest.raises(ConflictError) as caught:
        require_state(Where.GONE, {Where.OPEN}, "no", number="W-1041", serial="C-1")
    assert caught.value.facts["number"] == "W-1041"
    assert caught.value.facts["serial"] == "C-1"


def test_a_list_of_allowed_states_works_as_well_as_a_set():
    require_state(Where.SHUT, [Where.OPEN, Where.SHUT], "no")


def test_an_empty_set_of_allowed_states_refuses_everything():
    with pytest.raises(ConflictError):
        require_state(Where.OPEN, (), "nothing is allowed")


def test_plain_strings_work_the_same_way():
    require_state("drafted", {"drafted", "scheduled"}, "no")
    with pytest.raises(ConflictError):
        require_state("done", {"drafted"}, "no")


def test_a_condition_that_holds_passes_quietly():
    assert require(True, "no") is None


def test_a_condition_that_does_not_hold_refuses():
    with pytest.raises(ConflictError) as caught:
        require(False, "that cannot be done", tag="41207")
    assert caught.value.facts == {"tag": "41207"}


def test_a_state_prints_as_its_value():
    assert state_of(Where.OPEN) == "open"
    assert state_of("drafted") == "drafted"
    assert state_of(4) == "4"

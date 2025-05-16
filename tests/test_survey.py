from datetime import date

import pytest

from hasplib.campus.survey import Condition, Deficiency, Survey, SurveyLine
from hasplib.errors import ConflictError, InputError, NotFoundError

WALKED = date(2025, 5, 12)


def a_line(opening: str = "MOO-214", **overrides) -> SurveyLine:
    settings = {
        "opening": opening,
        "surveyed_on": WALKED,
        "surveyor": "jkoivu",
        "condition": Condition.GOOD,
    }
    settings.update(overrides)
    return SurveyLine(**settings)


def a_survey() -> Survey:
    survey = Survey("MOO", WALKED)
    survey.record(a_line("MOO-101", condition=Condition.WORN))
    survey.record(a_line("MOO-214"))
    survey.record(
        a_line(
            "MOO-216",
            condition=Condition.FAILING,
            deficiencies=(Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED),
        )
    )
    survey.record(
        a_line("MOO-218", condition=Condition.BROKEN, deficiencies=(Deficiency.NO_CYLINDER,))
    )
    return survey


def test_a_line_names_the_door_and_who_walked_it():
    line = a_line()
    assert line.opening == "MOO-214"
    assert line.surveyor == "jkoivu"
    assert line.is_sound


@pytest.mark.parametrize("field", ["opening", "surveyor"])
def test_a_line_without_the_essentials_is_refused(field):
    with pytest.raises(InputError):
        a_line(**{field: "  "})


def test_a_deficiency_listed_twice_is_refused():
    with pytest.raises(ConflictError):
        a_line(deficiencies=(Deficiency.WORN_OUT, Deficiency.WORN_OUT))


def test_a_worn_door_with_nothing_wrong_is_still_sound():
    assert a_line(condition=Condition.WORN).is_sound


def test_a_failing_door_is_not_sound():
    assert not a_line(condition=Condition.FAILING).is_sound


def test_a_door_with_a_deficiency_is_not_sound():
    assert not a_line(deficiencies=(Deficiency.UNIDENTIFIED,)).is_sound


def test_some_deficiencies_have_to_be_dealt_with_first():
    assert a_line(deficiencies=(Deficiency.WORN_OUT,)).needs_work_first
    assert a_line(deficiencies=(Deficiency.NO_STRIKE,)).needs_work_first
    assert not a_line(deficiencies=(Deficiency.UNIDENTIFIED,)).needs_work_first


def test_a_line_describes_itself():
    assert a_line().describe() == "MOO-214 good: nothing to report"
    found = a_line(deficiencies=(Deficiency.UNIDENTIFIED,)).describe()
    assert "cannot be identified" in found


def test_a_survey_covers_a_building():
    with pytest.raises(InputError):
        Survey(" ", WALKED)


def test_lines_come_back_in_door_order():
    assert [line.opening for line in a_survey()] == [
        "MOO-101",
        "MOO-214",
        "MOO-216",
        "MOO-218",
    ]


def test_a_door_is_surveyed_once():
    survey = a_survey()
    with pytest.raises(ConflictError):
        survey.record(a_line("MOO-214"))


def test_a_second_look_replaces_the_line():
    survey = a_survey()
    survey.revise("MOO-214", condition=Condition.FAILING)
    assert survey.line("MOO-214").condition is Condition.FAILING
    assert len(survey) == 4


def test_a_door_nobody_walked():
    with pytest.raises(NotFoundError):
        a_survey().line("MOO-999")


def test_the_doors_with_one_deficiency_come_back_together():
    found = a_survey().with_deficiency(Deficiency.UNIDENTIFIED)
    assert [line.opening for line in found] == ["MOO-216"]


def test_the_blocking_doors_are_the_ones_that_need_work_first():
    found = a_survey().blocking()
    assert [line.opening for line in found] == ["MOO-216", "MOO-218"]


def test_the_sound_doors_are_the_ones_with_nothing_wrong():
    found = a_survey().sound()
    assert [line.opening for line in found] == ["MOO-101", "MOO-214"]


def test_the_worst_door_comes_first():
    found = a_survey().worst_first()
    assert found[0].opening == "MOO-218"
    assert found[-1].opening == "MOO-214"


def test_the_conditions_are_counted_across_every_value():
    counts = a_survey().counts_by_condition()
    assert counts == {"good": 1, "worn": 1, "failing": 1, "broken": 1}


def test_the_deficiencies_are_counted_worst_first():
    counts = a_survey().counts_by_deficiency()
    assert list(counts.values()) == sorted(counts.values(), reverse=True)
    assert len(counts) == 3


def test_a_survey_with_nothing_wrong_counts_no_deficiencies():
    survey = Survey("VOS", WALKED)
    survey.record(a_line("VOS-311"))
    assert survey.counts_by_deficiency() == {}


def test_the_doors_nobody_walked_are_reported():
    survey = a_survey()
    assert survey.coverage(("MOO-214", "MOO-260")) == ("MOO-260",)


def test_a_finished_survey_covers_everything():
    survey = a_survey()
    assert survey.coverage(("MOO-101", "MOO-214")) == ()


def test_the_surveyors_are_listed_once_each():
    survey = a_survey()
    survey.record(a_line("MOO-260", surveyor="phamm"))
    assert survey.surveyors() == ("jkoivu", "phamm")


def test_a_survey_is_a_container():
    survey = a_survey()
    assert "MOO-214" in survey
    assert "MOO-999" not in survey
    assert len(survey) == 4

from datetime import date

from campus import a_campus
from hasplib.campus.survey import Condition, Deficiency, Survey, SurveyLine
from hasplib.reports.survey import (
    blocking_deficiencies,
    blocking_work,
    condition_table,
    deficiency_table,
    inventory,
    survey_report,
    unwalked,
    worst_buildings,
)

WALKED = date(2025, 5, 12)


def a_line(opening: str, **overrides) -> SurveyLine:
    settings = {
        "opening": opening,
        "surveyed_on": WALKED,
        "surveyor": "jkoivu",
        "condition": Condition.GOOD,
    }
    settings.update(overrides)
    return SurveyLine(**settings)


def a_survey(building: str = "MOO") -> Survey:
    survey = Survey(building, WALKED)
    survey.record(a_line("MOO-101"))
    survey.record(a_line("MOO-110", condition=Condition.WORN))
    survey.record(
        a_line(
            "MOO-214",
            condition=Condition.FAILING,
            deficiencies=(Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED),
        )
    )
    survey.record(
        a_line("MOO-216", condition=Condition.BROKEN, deficiencies=(Deficiency.NO_STRIKE,))
    )
    return survey


def test_the_condition_table_covers_every_value_worst_first():
    lines = condition_table(a_survey())
    assert lines[0].startswith("condition")
    assert lines[1].startswith("broken")
    assert lines[-1].startswith("good")


def test_the_condition_table_counts_the_doors():
    lines = condition_table(a_survey())
    assert "broken" in lines[1]
    assert lines[1].strip().endswith("1")


def test_the_deficiency_table_is_most_common_first():
    lines = deficiency_table(a_survey())
    assert lines[0] == "deficiencies found"
    assert len(lines) == 4


def test_a_clean_survey_reports_nothing_found():
    survey = Survey("VOS", WALKED)
    survey.record(a_line("VOS-311"))
    assert deficiency_table(survey) == ("nothing to report on any door",)


def test_the_blocking_work_names_the_rooms():
    lines = blocking_work(a_survey(), a_campus())
    assert lines[0] == "2 doors need work before a rekey"
    assert any("MOO 214" in line for line in lines)


def test_a_door_nobody_has_a_record_of_keeps_its_number():
    survey = Survey("MOO", WALKED)
    survey.record(
        a_line("MOO-999", condition=Condition.BROKEN, deficiencies=(Deficiency.NO_CYLINDER,))
    )
    assert any("MOO-999" in line for line in blocking_work(survey, a_campus()))


def test_a_survey_with_nothing_blocking_says_so():
    survey = Survey("VOS", WALKED)
    survey.record(a_line("VOS-311"))
    assert blocking_work(survey, a_campus())[0].startswith("nothing blocks")


def test_the_doors_nobody_walked_are_named():
    lines = unwalked(a_survey(), a_campus())
    assert lines[0].startswith("1 doors not walked")
    assert any("MOO-218" in line for line in lines)


def test_a_full_walk_says_so():
    survey = Survey("VOS", WALKED)
    for number in ["VOS-100", "VOS-311", "VOS-312"]:
        survey.record(a_line(number))
    assert unwalked(survey, a_campus())[0].startswith("every keyed door")


def test_the_inventory_is_worst_first():
    lines = inventory(a_survey())
    assert lines[0].startswith("door")
    assert lines[1].startswith("MOO-216")


def test_a_door_with_nothing_wrong_says_nothing():
    lines = inventory(a_survey())
    assert any(line.endswith("nothing") for line in lines)


def test_the_report_reads_top_to_bottom():
    lines = survey_report(a_survey(), a_campus())
    assert lines[0].startswith("MOO surveyed 2025-05-12")
    assert lines[1] == "walked by jkoivu"
    assert any(line.startswith("2 doors need work") for line in lines)
    assert any(line.startswith("door") for line in lines)


def test_a_survey_with_no_surveyor_named():
    survey = Survey("VOS", WALKED)
    lines = survey_report(survey, a_campus())
    assert lines[1] == "walked by nobody named"


def test_the_worst_buildings_come_first():
    other = Survey("VOS", WALKED)
    other.record(a_line("VOS-311"))
    found = worst_buildings([other, a_survey()])
    assert found[0] == ("MOO", 2)
    assert found[1] == ("VOS", 0)


def test_the_worst_list_can_be_cut_short():
    assert len(worst_buildings([a_survey(), Survey("VOS", WALKED)], limit=1)) == 1


def test_the_blocking_faults_are_counted_across_surveys():
    counts = blocking_deficiencies([a_survey(), a_survey("HEI")])
    assert counts["hardware needs replacing before it is rekeyed"] == 2
    assert "cylinder cannot be identified" not in counts


def test_no_blocking_faults_count_as_nothing():
    survey = Survey("VOS", WALKED)
    survey.record(a_line("VOS-311"))
    assert blocking_deficiencies([survey]) == {}

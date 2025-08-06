"""The survey report, which is the document that gets a budget line approved.

A list of four hundred doors persuades nobody. What persuades somebody is the
short version: eleven doors cannot be rekeyed until the frames are fixed,
these are the rooms, and here is what the fix costs before the rekey even
starts. So the report leads with the blocking work and puts the inventory at
the back where it belongs.

The condition table is the other half. Nobody argues with a count of broken
hardware, and a count from a walk somebody signed for is worth more than a
paragraph from the shop about how old the buildings are.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.campus.openings import OpeningSchedule
from hasplib.campus.survey import Condition, Deficiency, Survey
from hasplib.reports.page import columns

OPENING_WIDTH = 12
CONDITION_WIDTH = 10


def condition_table(survey: Survey) -> tuple[str, ...]:
    """How many doors are in each condition, worst first."""
    counts = survey.counts_by_condition()
    lines = [columns(("condition", "doors"), (CONDITION_WIDTH,))]
    for condition in sorted(Condition, key=lambda item: -int(item)):
        lines.append(columns((condition.label, counts[condition.label]), (CONDITION_WIDTH,)))
    return tuple(lines)


def deficiency_table(survey: Survey) -> tuple[str, ...]:
    """What was found, most common first."""
    counts = survey.counts_by_deficiency()
    if not counts:
        return ("nothing to report on any door",)
    lines = ["deficiencies found"]
    lines.extend(f"  {count:>3}  {name}" for name, count in counts.items())
    return tuple(lines)


def blocking_work(survey: Survey, openings: OpeningSchedule) -> tuple[str, ...]:
    """The doors that need other work before a rekey is worth booking."""
    blocked = survey.blocking()
    if not blocked:
        return ("nothing blocks a rekey of this building",)
    lines = [f"{len(blocked)} doors need work before a rekey"]
    for line in blocked:
        try:
            where = openings.opening(line.opening).label
        except Exception:
            where = line.opening
        faults = ", ".join(item.value for item in line.deficiencies)
        lines.append(f"  {where:<{OPENING_WIDTH}}{faults}")
    return tuple(lines)


def unwalked(survey: Survey, openings: OpeningSchedule) -> tuple[str, ...]:
    """Doors on the list nobody looked at, which is how a survey lies."""
    wanted = tuple(
        opening.number for opening in openings.in_building(survey.building) if opening.is_keyed
    )
    missed = survey.coverage(wanted)
    if not missed:
        return (f"every keyed door in {survey.building} was walked",)
    return (f"{len(missed)} doors not walked", *(f"  {number}" for number in missed))


def inventory(survey: Survey) -> tuple[str, ...]:
    """Every line, worst door first, which is the back of the report."""
    lines = [columns(("door", "condition", "found"), (OPENING_WIDTH, CONDITION_WIDTH))]
    for line in survey.worst_first():
        faults = ", ".join(item.value for item in line.deficiencies) or "nothing"
        lines.append(
            columns(
                (line.opening, line.condition.label, faults),
                (OPENING_WIDTH, CONDITION_WIDTH),
            )
        )
    return tuple(lines)


def survey_report(survey: Survey, openings: OpeningSchedule) -> tuple[str, ...]:
    """The whole document, in the order somebody reads it."""
    head = f"{survey.building} surveyed {survey.walked_on.isoformat()}, {len(survey)} doors"
    walked = ", ".join(survey.surveyors()) or "nobody named"
    return (
        head,
        f"walked by {walked}",
        "",
        *blocking_work(survey, openings),
        "",
        *condition_table(survey),
        "",
        *deficiency_table(survey),
        "",
        *unwalked(survey, openings),
        "",
        *inventory(survey),
    )


def worst_buildings(surveys: Sequence[Survey], limit: int = 5) -> tuple[tuple[str, int], ...]:
    """Buildings with the most blocking doors, for the capital plan."""
    counts = [(survey.building, len(survey.blocking())) for survey in surveys]
    counts.sort(key=lambda item: (-item[1], item[0]))
    return tuple(counts[:limit])


def blocking_deficiencies(surveys: Sequence[Survey]) -> dict[str, int]:
    """Which blocking fault turns up most across a set of surveys."""
    counts: dict[str, int] = {}
    for survey in surveys:
        for line in survey.blocking():
            for item in line.deficiencies:
                if item in {Deficiency.WORN_OUT, Deficiency.NO_STRIKE, Deficiency.NO_CYLINDER}:
                    counts[item.value] = counts.get(item.value, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

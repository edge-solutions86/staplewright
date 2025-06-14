"""Lockout calls and survey lines, between the tables and the objects.

The deficiencies are the only thing worth explaining. They go into one column
as their own text, comma separated, and come back as enum members. A value
the code has never heard of is dropped rather than raising, because a survey
from 2014 carries words this version of the software does not know and losing
one of them is better than losing the whole line.

That is a deliberate asymmetry: everywhere else in this library an unknown
value is a refusal. Here the row is evidence of something a person saw, and
evidence does not get thrown away for being unfashionable.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.campus.lockouts import CallState, Identification, Lockout, LockoutLog
from hasplib.campus.survey import Condition, Deficiency, Survey, SurveyLine
from hasplib.errors import InputError
from hasplib.persistence.models.facilities import LockoutRow, SurveyLineRow, SurveyRow


def _identification(value: str) -> Identification:
    for item in Identification:
        if item.value == value:
            return item
    return Identification.NONE


def lockout_to_row(call: Lockout) -> LockoutRow:
    return LockoutRow(
        number=call.number,
        opening_number=call.opening,
        keyholder=call.keyholder,
        called_at=call.called_at,
        state=call.state.value,
        attended_at=call.attended_at,
        closed_at=call.closed_at,
        technician=call.technician,
        identified_by=call.identified_by.value,
        note=call.note,
    )


def lockout_from_row(row: LockoutRow) -> Lockout:
    try:
        state = CallState(row.state)
    except ValueError:
        raise InputError("no such lockout state", state=row.state) from None
    return Lockout(
        number=row.number,
        opening=row.opening_number,
        keyholder=row.keyholder,
        called_at=row.called_at,
        state=state,
        attended_at=row.attended_at,
        closed_at=row.closed_at,
        technician=row.technician,
        identified_by=_identification(row.identified_by),
        note=row.note,
    )


def log_from_rows(rows: Sequence[LockoutRow]) -> LockoutLog:
    """Every call back in the order it came in, whatever state it reached."""
    log = LockoutLog()
    for row in sorted(rows, key=lambda item: (item.called_at, item.number)):
        log.load(lockout_from_row(row))
    return log


def deficiencies_to_text(items: Sequence[Deficiency]) -> str:
    return ",".join(item.value for item in items)


def deficiencies_from_text(text: str) -> tuple[Deficiency, ...]:
    """Known deficiencies, in file order, with anything unknown dropped."""
    known = {item.value: item for item in Deficiency}
    found: list[Deficiency] = []
    for part in text.split(","):
        item = known.get(part.strip())
        if item is not None and item not in found:
            found.append(item)
    return tuple(found)


def survey_line_to_row(line: SurveyLine, survey_id: int) -> SurveyLineRow:
    return SurveyLineRow(
        survey_id=survey_id,
        opening_number=line.opening,
        surveyor=line.surveyor,
        condition=int(line.condition),
        manufacturer=line.manufacturer,
        cylinder_serial=line.cylinder_serial,
        deficiencies=deficiencies_to_text(line.deficiencies),
        note=line.note,
    )


def survey_line_from_row(row: SurveyLineRow, walked_on: date) -> SurveyLine:
    return SurveyLine(
        opening=row.opening_number,
        surveyed_on=walked_on,
        surveyor=row.surveyor,
        condition=Condition(row.condition),
        manufacturer=row.manufacturer,
        cylinder_serial=row.cylinder_serial,
        deficiencies=deficiencies_from_text(row.deficiencies),
        note=row.note,
    )


def survey_to_rows(survey: Survey) -> tuple[SurveyRow, list[SurveyLineRow]]:
    """A header and its lines, with the id left for the caller to fill in."""
    header = SurveyRow(building_code=survey.building, walked_on=survey.walked_on)
    lines = [survey_line_to_row(line, 0) for line in survey.lines()]
    return header, lines


def survey_from_rows(row: SurveyRow) -> Survey:
    survey = Survey(row.building_code, row.walked_on)
    for line in row.lines:
        survey.record(survey_line_from_row(line, row.walked_on))
    return survey

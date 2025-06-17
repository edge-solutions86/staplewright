"""Lockouts and surveys against the database, one transaction each.

Both of these are written by somebody standing up. A lockout is logged on a
telephone while the caller is still talking, and a survey line is typed on a
tablet in a corridor, so both services are built to take one small write at a
time and to refuse it clearly rather than to batch anything.

The survey service is the one with a rule worth stating. A building is
surveyed once on a day, and a second walk on the same day is a correction to
the first rather than a new survey, so the service revises the line instead
of raising a duplicate.
"""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.campus.lockouts import CallState, Identification, Lockout, LockoutLog
from hasplib.campus.survey import Condition, Deficiency, Survey, SurveyLine
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.persistence.mappers.facilities import (
    lockout_to_row,
    log_from_rows,
    survey_from_rows,
    survey_line_to_row,
)
from hasplib.persistence.models.campus import OpeningRow
from hasplib.persistence.models.facilities import LockoutRow, SurveyLineRow, SurveyRow
from hasplib.persistence.numbering import Numbering

LOCKOUT_SERIES = "lockouts"


class LockoutService:
    """The telephone log, one call at a time."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.numbering = Numbering(session)

    def open_series(self) -> None:
        self.numbering.open_series(LOCKOUT_SERIES, prefix="L-", width=5, start=1)

    def log(self, opening: str, keyholder: str, when: datetime) -> LockoutRow:
        if self.session.get(OpeningRow, opening) is None:
            raise NotFoundError("no such opening", opening=opening)
        if not keyholder.strip():
            raise InputError("a lockout call names somebody", opening=opening)
        number = self.numbering.next(LOCKOUT_SERIES)
        call = Lockout(number, opening, keyholder, when)
        row = lockout_to_row(call)
        self.session.add(row)
        self.session.flush()
        return row

    def attend(self, number: str, technician: str, when: datetime) -> LockoutRow:
        row = self._must_be(number, {CallState.LOGGED})
        if not technician.strip():
            raise InputError("somebody attends a call", number=number)
        if when < row.called_at:
            raise ConflictError("nobody attended before the call", number=number)
        row.state = CallState.ATTENDED.value
        row.technician = technician
        row.attended_at = when
        self.session.flush()
        return row

    def open_door(self, number: str, how: Identification, when: datetime) -> LockoutRow:
        row = self._must_be(number, {CallState.ATTENDED})
        if how is Identification.NONE:
            raise ConflictError("an unidentified person does not get let in", number=number)
        row.state = CallState.OPENED.value
        row.identified_by = how.value
        row.closed_at = when
        self.session.flush()
        return row

    def refuse(self, number: str, when: datetime, note: str) -> LockoutRow:
        row = self._must_be(number, {CallState.ATTENDED})
        if not note.strip():
            raise InputError("a refusal says why", number=number)
        row.state = CallState.REFUSED.value
        row.closed_at = when
        row.note = note
        self.session.flush()
        return row

    def log_book(self) -> LockoutLog:
        rows = self.session.execute(select(LockoutRow)).scalars().all()
        return log_from_rows(rows)

    def _must_be(self, number: str, states: set[CallState]) -> LockoutRow:
        row = self.session.get(LockoutRow, number)
        if row is None:
            raise NotFoundError("no such lockout call", number=number)
        if CallState(row.state) not in states:
            raise ConflictError(
                "the call is not in a state for that", number=number, state=row.state
            )
        return row


class SurveyService:
    """One walk round a building, line by line, with corrections."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def start(self, building: str, walked_on: date) -> SurveyRow:
        held = self._find(building, walked_on)
        if held is not None:
            raise ConflictError(
                "that building was already walked that day",
                building=building,
                walked_on=walked_on.isoformat(),
            )
        row = SurveyRow(building_code=building, walked_on=walked_on)
        self.session.add(row)
        self.session.flush()
        return row

    def record(
        self,
        building: str,
        walked_on: date,
        opening: str,
        surveyor: str,
        condition: Condition,
        deficiencies: tuple[Deficiency, ...] = (),
        note: str = "",
    ) -> SurveyLineRow:
        """Write a line, or correct the one already there for that door."""
        header = self._must_find(building, walked_on)
        line = SurveyLine(
            opening=opening,
            surveyed_on=walked_on,
            surveyor=surveyor,
            condition=condition,
            deficiencies=deficiencies,
            note=note,
        )
        held = self._line(header.id, opening)
        fresh = survey_line_to_row(line, header.id)
        if held is None:
            self.session.add(fresh)
            self.session.flush()
            return fresh
        held.surveyor = fresh.surveyor
        held.condition = fresh.condition
        held.deficiencies = fresh.deficiencies
        held.note = fresh.note
        self.session.flush()
        return held

    def survey(self, building: str, walked_on: date) -> Survey:
        return survey_from_rows(self._must_find(building, walked_on))

    def latest(self, building: str) -> Survey | None:
        statement = (
            select(SurveyRow)
            .where(SurveyRow.building_code == building)
            .order_by(SurveyRow.walked_on.desc())
        )
        row = self.session.execute(statement).scalars().first()
        return None if row is None else survey_from_rows(row)

    def _find(self, building: str, walked_on: date) -> SurveyRow | None:
        statement = select(SurveyRow).where(
            SurveyRow.building_code == building, SurveyRow.walked_on == walked_on
        )
        return self.session.execute(statement).scalar_one_or_none()

    def _must_find(self, building: str, walked_on: date) -> SurveyRow:
        row = self._find(building, walked_on)
        if row is None:
            raise NotFoundError(
                "no survey of that building on that day",
                building=building,
                walked_on=walked_on.isoformat(),
            )
        return row

    def _line(self, survey_id: int, opening: str) -> SurveyLineRow | None:
        statement = select(SurveyLineRow).where(
            SurveyLineRow.survey_id == survey_id,
            SurveyLineRow.opening_number == opening,
        )
        return self.session.execute(statement).scalar_one_or_none()

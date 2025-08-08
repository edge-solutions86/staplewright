"""Lockouts and surveys over http, for the tablet the surveyor carries.

The survey line endpoint is a put rather than a post, and that is the whole
design. A surveyor walking a corridor on a bad signal will send the same line
twice, and a put that overwrites is right both times where a post that creates
would leave two readings of one door and no way to tell which was later.

Lockouts go the other way. Each step is its own post because each step is a
thing that happened at a time somebody has to be able to point at later, and
collapsing them into one patch would let a call be closed without ever being
attended.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from hasplib.campus.lockouts import Identification
from hasplib.campus.survey import Condition, Deficiency
from hasplib.persistence.mappers.facilities import deficiencies_from_text
from hasplib.persistence.models.facilities import LockoutRow, SurveyLineRow
from hasplib.services.facilities import LockoutService, SurveyService


class NewLockout(BaseModel):
    opening: str = Field(min_length=1, max_length=32)
    keyholder: str = Field(min_length=1, max_length=32)
    called_at: datetime


class Attendance(BaseModel):
    technician: str = Field(min_length=1, max_length=128)
    attended_at: datetime


class Entry(BaseModel):
    identified_by: Identification
    closed_at: datetime


class Refusal(BaseModel):
    closed_at: datetime
    note: str = Field(min_length=1, max_length=512)


class NewSurvey(BaseModel):
    building: str = Field(min_length=1, max_length=32)
    walked_on: date


class Line(BaseModel):
    surveyor: str = Field(min_length=1, max_length=128)
    condition: Condition
    deficiencies: list[Deficiency] = Field(default_factory=list)
    note: str = Field(default="", max_length=512)


def lockout_shape(row: LockoutRow) -> dict[str, Any]:
    """One call as the tablet wants it, with the times already strings."""
    return {
        "number": row.number,
        "opening": row.opening_number,
        "keyholder": row.keyholder,
        "state": row.state,
        "technician": row.technician,
        "identified_by": row.identified_by,
        "called_at": row.called_at.isoformat(),
        "attended_at": None if row.attended_at is None else row.attended_at.isoformat(),
        "closed_at": None if row.closed_at is None else row.closed_at.isoformat(),
    }


def line_shape(row: SurveyLineRow) -> dict[str, Any]:
    """One survey line, with the condition back in words."""
    return {
        "opening": row.opening_number,
        "surveyor": row.surveyor,
        "condition": Condition(row.condition).label,
        "deficiencies": [item.value for item in deficiencies_from_text(row.deficiencies)],
        "note": row.note,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(tags=["doors"])

    def session() -> Any:
        yield from shop.open_session()

    def lockouts(db: Session = Depends(session)) -> LockoutService:
        return LockoutService(db)

    def surveys(db: Session = Depends(session)) -> SurveyService:
        return SurveyService(db)

    @router.post("/lockouts", status_code=201)
    def log(body: NewLockout, work: LockoutService = Depends(lockouts)) -> dict[str, Any]:
        return lockout_shape(work.log(body.opening, body.keyholder, body.called_at))

    @router.post("/lockouts/{number}/attendance")
    def attend(
        number: str, body: Attendance, work: LockoutService = Depends(lockouts)
    ) -> dict[str, Any]:
        return lockout_shape(work.attend(number, body.technician, body.attended_at))

    @router.post("/lockouts/{number}/entry")
    def let_in(
        number: str, body: Entry, work: LockoutService = Depends(lockouts)
    ) -> dict[str, Any]:
        return lockout_shape(work.open_door(number, body.identified_by, body.closed_at))

    @router.post("/lockouts/{number}/refusal")
    def refuse(
        number: str, body: Refusal, work: LockoutService = Depends(lockouts)
    ) -> dict[str, Any]:
        return lockout_shape(work.refuse(number, body.closed_at, body.note))

    @router.get("/lockouts")
    def open_calls(work: LockoutService = Depends(lockouts)) -> dict[str, Any]:
        book = work.log_book()
        return {"open": [call.describe() for call in book.open_calls()], "total": len(book)}

    @router.post("/surveys", status_code=201)
    def start(body: NewSurvey, work: SurveyService = Depends(surveys)) -> dict[str, Any]:
        row = work.start(body.building, body.walked_on)
        return {"building": row.building_code, "walked_on": row.walked_on.isoformat()}

    @router.put("/surveys/{building}/{walked_on}/{opening}")
    def record(
        building: str,
        walked_on: date,
        opening: str,
        body: Line,
        work: SurveyService = Depends(surveys),
    ) -> dict[str, Any]:
        row = work.record(
            building,
            walked_on,
            opening,
            body.surveyor,
            body.condition,
            tuple(body.deficiencies),
            body.note,
        )
        return line_shape(row)

    @router.get("/surveys/{building}/{walked_on}")
    def read(
        building: str, walked_on: date, work: SurveyService = Depends(surveys)
    ) -> dict[str, Any]:
        survey = work.survey(building, walked_on)
        return {
            "building": survey.building,
            "walked_on": survey.walked_on.isoformat(),
            "doors": len(survey),
            "blocking": [line.opening for line in survey.blocking()],
            "conditions": survey.counts_by_condition(),
        }

    @router.get("/buildings/{building}/latest-survey")
    def latest(building: str, work: SurveyService = Depends(surveys)) -> dict[str, Any]:
        survey = work.latest(building)
        if survey is None:
            return {"building": building, "walked_on": None, "doors": 0}
        return {
            "building": survey.building,
            "walked_on": survey.walked_on.isoformat(),
            "doors": len(survey),
            "worst": [line.opening for line in survey.worst_first()[:5]],
        }

    return router

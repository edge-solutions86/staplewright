"""Incidents over http: report a loss, read the assessment, raise the work.

The assessment is a read rather than a write, so it can be asked for as often
as anybody likes and always reflects the cylinders as they are today. That
matters: an incident from March reassessed in June gives a different answer
because the campus changed, and the June answer is the one that decides
whether the work still needs doing.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from hasplib.api.wiring import service_dependency
from hasplib.rekey.compromise import Cause
from hasplib.services.incidents import IncidentService


class Report(BaseModel):
    """A key reported missing."""

    tag: str = Field(min_length=1, max_length=32)
    cause: Cause
    reported_on: date
    note: str = Field(default="", max_length=512)
    retire_cut: bool = True


class Booking(BaseModel):
    """A work order raised off an incident."""

    scheduled_for: date | None = None


def incident_shape(row: Any) -> dict[str, Any]:
    return {
        "number": row.number,
        "tag": row.tag,
        "symbol": row.symbol,
        "cause": row.cause,
        "severity": row.severity,
        "reported_on": row.reported_on.isoformat(),
        "keyholder": row.keyholder,
    }


def assessment_shape(found: Any) -> dict[str, Any]:
    return {
        "number": found.incident.number,
        "severity": found.severity.label,
        "openings": list(found.openings),
        "buildings": list(found.buildings),
        "cylinders": list(found.cylinders),
        "exterior_openings": list(found.exterior_openings),
        "keys_to_recut": list(found.keys_to_recut),
        "holders_affected": list(found.holders_affected),
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/incidents", tags=["incidents"])

    service = service_dependency(IncidentService, shop, shop.settings.scope)

    @router.post("", status_code=201)
    def report(body: Report, work: IncidentService = Depends(service)) -> dict[str, Any]:
        row = work.report(body.tag, body.cause, body.reported_on, body.note, body.retire_cut)
        return incident_shape(row)

    @router.get("/{number}")
    def read(number: str, work: IncidentService = Depends(service)) -> dict[str, Any]:
        return incident_shape(work.incidents.get(number))

    @router.get("/{number}/assessment")
    def assessment(number: str, work: IncidentService = Depends(service)) -> dict[str, Any]:
        return assessment_shape(work.assessment(number))

    @router.get("/{number}/plan")
    def plan(number: str, work: IncidentService = Depends(service)) -> dict[str, Any]:
        decision = work.plan(number)
        return {
            "number": number,
            "scope": decision.scope.label,
            "reason": decision.reason,
            "over_cap": decision.over_cap,
            "cylinders": list(decision.cylinders),
            "symbols": [symbol.render() for symbol in decision.symbols],
        }

    @router.post("/{number}/orders", status_code=201)
    def raise_order(
        number: str, body: Booking, work: IncidentService = Depends(service)
    ) -> dict[str, Any]:
        order = work.raise_order(number, body.scheduled_for)
        return {
            "number": order.number,
            "incident": order.incident_number,
            "state": order.state,
            "scheduled_for": order.scheduled_for.isoformat() if order.scheduled_for else None,
            "lines": len(order.lines),
        }

    return router

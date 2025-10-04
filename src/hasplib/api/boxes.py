"""Sealed boxes over http, which is what the walk round app talks to.

The check endpoint is the one that matters and it is a post rather than a
put, because reading a seal is an event and not a state. Two people walking
the same corridor and reading the same tag should leave two records of having
looked, not one record overwritten twice.

Everything else is a state change with its own path, for the same reason the
stock movements are: the rules differ, and a body that carries the name of
what it wants would have to branch on itself.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from hasplib.api.wiring import service_dependency
from hasplib.control.escrow import KeyBox, OpenedBy
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.models.escrow import KeyBoxRow, SealRow
from hasplib.services.escrow import EscrowService


class NewBox(BaseModel):
    number: str = Field(min_length=1, max_length=32)
    building: str = Field(min_length=1, max_length=32)
    opening: str = Field(min_length=1, max_length=32)
    symbol: str = Field(min_length=1, max_length=16)
    seal: str = Field(min_length=1, max_length=32)
    sealed_on: date


class SealReading(BaseModel):
    seal: str = Field(min_length=1, max_length=32)
    read_on: date


class Opening(BaseModel):
    opened_by: OpenedBy
    opened_on: date
    note: str = Field(default="", max_length=512)


class Emptied(BaseModel):
    found_on: date
    note: str = Field(default="", max_length=512)


class Reseal(BaseModel):
    seal: str = Field(min_length=1, max_length=32)
    symbol: str = Field(min_length=1, max_length=16)
    sealed_on: date


class Withdrawal(BaseModel):
    taken_on: date
    note: str = Field(min_length=1, max_length=512)


def row_shape(row: KeyBoxRow) -> dict[str, Any]:
    return {
        "number": row.number,
        "building": row.building_code,
        "opening": row.opening_number,
        "symbol": row.symbol.render(),
        "seal": row.seal,
        "state": row.state,
        "sealed_on": row.sealed_on.isoformat(),
        "checked_on": row.checked_on.isoformat() if row.checked_on else None,
        "opened_on": row.opened_on.isoformat() if row.opened_on else None,
        "opened_by": row.opened_by or None,
        "note": row.note,
    }


def box_shape(box: KeyBox) -> dict[str, Any]:
    return {
        "number": box.number,
        "building": box.building,
        "opening": box.opening,
        "symbol": box.symbol.render(),
        "state": str(box.state),
        "last_seen": box.last_seen().isoformat(),
    }


def seal_shape(row: SealRow, today: date) -> dict[str, Any]:
    return {
        "seal": row.seal,
        "symbol": row.symbol.render(),
        "sealed_on": row.sealed_on.isoformat(),
        "broken_on": row.broken_on.isoformat() if row.broken_on else None,
        "broken_by": row.broken_by or None,
        "days_held": row.days_held(today),
        "current": row.is_current,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/boxes", tags=["boxes"])
    service = service_dependency(EscrowService, shop, shop.settings.boxes)

    @router.post("", status_code=201)
    def install(body: NewBox, work: EscrowService = Depends(service)) -> dict[str, Any]:
        row = work.install(
            body.number,
            body.building,
            body.opening,
            KeySymbol.parse(body.symbol),
            body.seal,
            body.sealed_on,
        )
        return row_shape(row)

    @router.get("")
    def listing(
        building: str = Query(default=""),
        work: EscrowService = Depends(service),
    ) -> dict[str, Any]:
        rows = work.boxes.in_building(building) if building else work.boxes.all()
        return {"boxes": [row_shape(row) for row in rows]}

    @router.get("/open")
    def open_boxes(work: EscrowService = Depends(service)) -> dict[str, Any]:
        return {
            "open": [box_shape(box) for box in work.open_boxes()],
            "exposure": work.exposure(),
        }

    @router.get("/uncovered")
    def uncovered(work: EscrowService = Depends(service)) -> dict[str, Any]:
        return {"buildings": list(work.uncovered())}

    @router.get("/walk-round")
    def walk_round(on: date, work: EscrowService = Depends(service)) -> dict[str, Any]:
        return {
            "on": on.isoformat(),
            "boxes": [box_shape(box) for box in work.walk_round(on)],
            "overdue_reseal": [box.number for box in work.overdue_reseal(on)],
        }

    @router.get("/{number}")
    def read(number: str, work: EscrowService = Depends(service)) -> dict[str, Any]:
        return row_shape(work.row(number))

    @router.get("/{number}/seals")
    def seals(
        number: str, on: date | None = None, work: EscrowService = Depends(service)
    ) -> dict[str, Any]:
        today = on or date.today()
        return {
            "number": number,
            "seals": [seal_shape(row, today) for row in work.history(number)],
        }

    @router.get("/{number}/key-on/{when}")
    def key_on(number: str, when: date, work: EscrowService = Depends(service)) -> dict[str, Any]:
        found = work.key_on(number, when)
        return {
            "number": number,
            "on": when.isoformat(),
            "symbol": found.render() if found is not None else None,
        }

    @router.post("/{number}/checks")
    def check(
        number: str, body: SealReading, work: EscrowService = Depends(service)
    ) -> dict[str, Any]:
        return row_shape(work.check(number, body.seal, body.read_on))

    @router.post("/{number}/openings")
    def opened(
        number: str, body: Opening, work: EscrowService = Depends(service)
    ) -> dict[str, Any]:
        return row_shape(work.report_opened(number, body.opened_by, body.opened_on, body.note))

    @router.post("/{number}/emptied")
    def emptied(
        number: str, body: Emptied, work: EscrowService = Depends(service)
    ) -> dict[str, Any]:
        return row_shape(work.empty(number, body.found_on, body.note))

    @router.post("/{number}/reseal")
    def reseal(number: str, body: Reseal, work: EscrowService = Depends(service)) -> dict[str, Any]:
        return row_shape(
            work.reseal(number, body.seal, KeySymbol.parse(body.symbol), body.sealed_on)
        )

    @router.post("/{number}/withdrawal")
    def withdraw(
        number: str, body: Withdrawal, work: EscrowService = Depends(service)
    ) -> dict[str, Any]:
        return row_shape(work.withdraw(number, body.taken_on, body.note))

    return router

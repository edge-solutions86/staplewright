"""Handovers over http, and the two views of who has a key.

``/keys/{tag}/holder`` is the endpoint everything else should be calling. The
ledger view is still there under the counter routes and it is still correct
about who signed for the key, but it is not the answer to the question people
actually ask, which is who has it now.

The confirmation is a post to its own path and takes no body beyond a date,
because there is nothing to say. Either the person receiving the key signs or
they do not, and a payload with a state in it would let somebody confirm a
handover into any state they liked.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from hasplib.api.wiring import service_dependency
from hasplib.persistence.models.transfer import TransferRow
from hasplib.services.transfer import TransferService


class NewClaim(BaseModel):
    tag: str = Field(min_length=1, max_length=32)
    passed_to: str = Field(min_length=1, max_length=32)
    claimed_on: date


class Confirmation(BaseModel):
    confirmed_on: date
    note: str = Field(default="", max_length=512)


class Refusal(BaseModel):
    refused_on: date
    note: str = Field(min_length=1, max_length=512)


class Cancellation(BaseModel):
    cancelled_on: date
    note: str = Field(default="", max_length=512)


def transfer_shape(row: TransferRow) -> dict[str, Any]:
    return {
        "number": row.number,
        "tag": row.tag,
        "symbol": row.symbol,
        "passed_by": row.passed_by,
        "passed_to": row.passed_to,
        "claimed_on": row.claimed_on.isoformat(),
        "state": row.state,
        "settled_on": row.settled_on.isoformat() if row.settled_on else None,
        "note": row.note,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(tags=["handovers"])
    service = service_dependency(TransferService, shop, shop.settings.transfers)

    @router.post("/handovers", status_code=201)
    def claim(body: NewClaim, work: TransferService = Depends(service)) -> dict[str, Any]:
        return transfer_shape(work.claim(body.tag, body.passed_to, body.claimed_on))

    @router.get("/handovers")
    def listing(
        state: str = Query(
            default="claimed", pattern="^(claimed|confirmed|refused|lapsed|cancelled)$"
        ),
        work: TransferService = Depends(service),
    ) -> dict[str, Any]:
        rows = work.transfers.rows(TransferRow.state == state)
        return {"handovers": [transfer_shape(row) for row in rows]}

    @router.get("/handovers/late")
    def late(on: date, work: TransferService = Depends(service)) -> dict[str, Any]:
        return {
            "on": on.isoformat(),
            "late": [item.describe() for item in work.late(on)],
            "exposure": work.exposure(on),
        }

    @router.get("/handovers/strays")
    def strays(on: date, work: TransferService = Depends(service)) -> dict[str, Any]:
        found = work.strays(on)
        return {
            "on": on.isoformat(),
            "strays": [
                {"tag": tag, "ledger": ledger, "actually": now} for tag, ledger, now in found
            ],
        }

    @router.get("/handovers/{number}")
    def read(number: str, work: TransferService = Depends(service)) -> dict[str, Any]:
        return transfer_shape(work.row(number))

    @router.post("/handovers/{number}/confirmation")
    def confirm(
        number: str, body: Confirmation, work: TransferService = Depends(service)
    ) -> dict[str, Any]:
        return transfer_shape(work.confirm(number, body.confirmed_on, body.note))

    @router.post("/handovers/{number}/refusal")
    def refuse(
        number: str, body: Refusal, work: TransferService = Depends(service)
    ) -> dict[str, Any]:
        return transfer_shape(work.refuse(number, body.refused_on, body.note))

    @router.post("/handovers/{number}/cancellation")
    def cancel(
        number: str, body: Cancellation, work: TransferService = Depends(service)
    ) -> dict[str, Any]:
        return transfer_shape(work.cancel(number, body.cancelled_on, body.note))

    @router.post("/handovers/lapse")
    def sweep(on: date, work: TransferService = Depends(service)) -> dict[str, Any]:
        found = work.lapse_overdue(on)
        return {"lapsed": [row.number for row in found], "count": len(found)}

    @router.get("/keys/{tag}/holder")
    def holder(
        tag: str, on: date | None = None, work: TransferService = Depends(service)
    ) -> dict[str, Any]:
        today = on or date.today()
        return {
            "tag": tag,
            "on": today.isoformat(),
            "holder": work.holder_of(tag, today),
            "chain": [item.passed_to for item in work.chain_for(tag)],
        }

    @router.get("/keyholders/{identifier}/holdings")
    def holdings(
        identifier: str, on: date | None = None, work: TransferService = Depends(service)
    ) -> dict[str, Any]:
        today = on or date.today()
        return {
            "keyholder": identifier,
            "on": today.isoformat(),
            "tags": list(work.holdings(identifier, today)),
            "waiting_on": [row.number for row in work.waiting_on(identifier)],
        }

    return router

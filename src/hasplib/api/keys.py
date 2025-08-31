"""The counter over http: request, decide, issue, take back.

The dates are on the requests rather than read off the clock inside the
service. A shop that is catching up on three weeks of paperwork needs to say
what day something happened on, and a service that quietly used today would
put every one of those entries on the wrong date.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from hasplib.api.wiring import service_dependency, session_dependency
from hasplib.persistence.loaders import load_deposits, load_ledger
from hasplib.services.counter import CounterService


class NewRequest(BaseModel):
    keyholder: str = Field(min_length=1, max_length=32)
    symbol: str = Field(min_length=1, max_length=16)
    building: str = Field(min_length=1, max_length=32)
    reason: str = Field(min_length=1, max_length=512)
    raised_on: date
    is_master: bool = False


class Decision(BaseModel):
    signer: str = Field(min_length=1, max_length=32)
    decided_on: date
    note: str = Field(default="", max_length=512)


class Issue(BaseModel):
    issued_on: date
    due_on: date | None = None
    deposit_cents: int | None = Field(default=None, ge=0)


class Close(BaseModel):
    closed_on: date
    note: str = Field(default="", max_length=512)


def request_shape(row: Any) -> dict[str, Any]:
    return {
        "number": row.number,
        "keyholder": row.keyholder,
        "symbol": row.symbol,
        "building": row.building,
        "state": row.state,
        "is_master": row.is_master,
        "decided_by": row.decided_by,
    }


def key_shape(row: Any) -> dict[str, Any]:
    return {
        "tag": row.tag,
        "symbol": row.symbol,
        "keyholder": row.keyholder,
        "state": row.state,
        "issued_on": row.issued_on.isoformat(),
        "due_on": row.due_on.isoformat() if row.due_on else None,
        "deposit_cents": row.deposit_cents,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(tags=["counter"])

    session = session_dependency(shop)

    service = service_dependency(CounterService, shop, shop.settings.deposits)

    @router.post("/requests", status_code=201)
    def raise_request(body: NewRequest, work: CounterService = Depends(service)) -> dict[str, Any]:
        row = work.raise_request(
            body.keyholder,
            body.symbol,
            body.building,
            body.reason,
            body.raised_on,
            body.is_master,
        )
        return request_shape(row)

    @router.get("/requests/{number}")
    def read_request(number: str, work: CounterService = Depends(service)) -> dict[str, Any]:
        return request_shape(work.requests.get(number))

    @router.post("/requests/{number}/approval")
    def approve(
        number: str, body: Decision, work: CounterService = Depends(service)
    ) -> dict[str, Any]:
        return request_shape(work.approve(number, body.signer, body.decided_on, body.note))

    @router.post("/requests/{number}/denial")
    def deny(
        number: str, body: Decision, work: CounterService = Depends(service)
    ) -> dict[str, Any]:
        return request_shape(work.deny(number, body.signer, body.decided_on, body.note))

    @router.post("/requests/{number}/key", status_code=201)
    def issue(number: str, body: Issue, work: CounterService = Depends(service)) -> dict[str, Any]:
        row = work.issue(number, body.issued_on, body.due_on, body.deposit_cents)
        return key_shape(row)

    @router.get("/keys/{tag}")
    def read_key(tag: str, work: CounterService = Depends(service)) -> dict[str, Any]:
        return key_shape(work.keys.get(tag))

    @router.post("/keys/{tag}/return")
    def take_back(tag: str, body: Close, work: CounterService = Depends(service)) -> dict[str, Any]:
        return key_shape(work.take_back(tag, body.closed_on, body.note))

    @router.post("/keys/{tag}/loss")
    def report_lost(
        tag: str, body: Close, work: CounterService = Depends(service)
    ) -> dict[str, Any]:
        return key_shape(work.report_lost(tag, body.closed_on, body.note))

    @router.get("/keyholders/{identifier}/keys")
    def holdings(identifier: str, db: Session = Depends(session)) -> dict[str, Any]:
        ledger = load_ledger(db)
        book = load_deposits(db)
        keys = ledger.held_by(identifier)
        return {
            "keyholder": identifier,
            "keys": [
                {"tag": key.tag, "symbol": key.symbol, "deposit": book.held_on(key.tag)}
                for key in keys
            ],
            "deposits_held": book.balance_of(identifier),
        }

    return router

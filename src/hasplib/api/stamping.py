"""Stamping over http, with the one endpoint that hands back the whole table.

``/stamping/book`` exists because something has to be able to restore it, and
it is the only route in this library that would be worth an attacker's time on
its own. It is separated from everything else so that whatever sits in front
of this service can be told about exactly one path.

``/stamping/identify/{code}`` is the opposite: one code, one symbol, nothing
to iterate. A caller that wants the table by guessing codes has to ask four
thousand times, and that is a shape a log notices.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from hasplib.api.wiring import PLAIN, page, service_dependency
from hasplib.keys.stamping import Stamp
from hasplib.keys.symbol import KeySymbol
from hasplib.services.stamping import StampingService, issued_symbols, unstamped_out


class Assignment(BaseModel):
    symbol: str = Field(min_length=1, max_length=16)
    assigned_on: date
    assigned_by: str = Field(default="", max_length=128)


class Run(BaseModel):
    tags: list[str] = Field(min_length=1)
    stamped_on: date
    stamped_by: str = Field(default="", max_length=128)


def stamp_shape(stamp: Stamp) -> dict[str, Any]:
    return {
        "tag": stamp.tag,
        "symbol": stamp.symbol,
        "text": stamp.text,
        "marking": stamp.marking.value,
        "blind": stamp.is_blind,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/stamping", tags=["stamping"])
    service = service_dependency(StampingService, shop, shop.settings.stamping)

    @router.get("/identify/{code}")
    def identify(code: str, work: StampingService = Depends(service)) -> dict[str, Any]:
        return {"code": code, "symbol": work.identify(code)}

    @router.get("/symbols")
    def symbols(work: StampingService = Depends(service)) -> dict[str, Any]:
        return {"out": list(issued_symbols(work.session))}

    @router.get("/blank")
    def blank(work: StampingService = Depends(service)) -> dict[str, Any]:
        return {"tags": list(unstamped_out(work.session, work.policy))}

    @router.get("/keys/{tag}")
    def one_key(
        tag: str, on: date | None = None, work: StampingService = Depends(service)
    ) -> dict[str, Any]:
        return stamp_shape(work.stamp_key(tag, on or date.today()))

    @router.post("/runs")
    def run(body: Run, work: StampingService = Depends(service)) -> dict[str, Any]:
        found = work.stamp_batch(body.tags, body.stamped_on, body.stamped_by)
        return {"stamps": [stamp_shape(stamp) for stamp in found]}

    @router.get("/sheet")
    def sheet(on: date, plain: bool = PLAIN, work: StampingService = Depends(service)) -> Any:
        tags = [row.tag for row in work.keys.out()]
        return page(work.sheet(tags, on), plain)

    @router.post("/codes", status_code=201)
    def assign(body: Assignment, work: StampingService = Depends(service)) -> dict[str, Any]:
        row = work.assign(KeySymbol.parse(body.symbol), body.assigned_on, body.assigned_by)
        return {"symbol": row.symbol, "code": row.code}

    @router.get("/codes/{symbol}")
    def read_code(symbol: str, work: StampingService = Depends(service)) -> dict[str, Any]:
        return {"symbol": symbol, "code": work.code_for(KeySymbol.parse(symbol))}

    @router.delete("/codes/{symbol}")
    def retire(symbol: str, work: StampingService = Depends(service)) -> dict[str, Any]:
        row = work.retire_code(KeySymbol.parse(symbol))
        return {"symbol": row.symbol, "retired": True}

    @router.get("/book")
    def book(work: StampingService = Depends(service)) -> dict[str, Any]:
        """The whole table. One route, so one thing to put a rule in front of."""
        return {
            "warning": "this table turns every stamped number into a symbol",
            "codes": [{"symbol": symbol, "code": code} for symbol, code in work.the_whole_book()],
        }

    return router

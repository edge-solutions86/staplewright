"""The return over http, for whatever risk management is pasting it into.

Everything here is a read except the filing, and the filing is a post that
takes the day rather than the numbers. A caller that could send the numbers
could file whatever it liked, and the point of the return is that the numbers
came off the ledger on a day somebody can name.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from hasplib.api.wiring import PLAIN, page, service_dependency
from hasplib.persistence.models.annual import AnnualReturnRow
from hasplib.services.annual import AnnualService


class Filing(BaseModel):
    measured_on: date
    filed_by: str = Field(default="", max_length=128)
    note: str = Field(default="", max_length=512)


class Withdrawal(BaseModel):
    note: str = Field(min_length=1, max_length=512)


def return_shape(row: AnnualReturnRow) -> dict[str, Any]:
    return {
        "year": row.year,
        "measured_on": row.measured_on.isoformat(),
        "keys_out": row.keys_out,
        "masters_out": row.masters_out,
        "unaccounted": row.unaccounted,
        "keyholders": row.keyholders,
        "keys_per_holder": round(row.keys_per_holder, 2),
        "filed_by": row.filed_by or None,
        "note": row.note,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/returns", tags=["returns"])
    service = service_dependency(AnnualService, shop)

    @router.get("")
    def listing(work: AnnualService = Depends(service)) -> dict[str, Any]:
        return {"returns": [return_shape(row) for row in work.returns.all()]}

    @router.get("/measure")
    def measure(on: date, work: AnnualService = Depends(service)) -> dict[str, Any]:
        found = work.measure(on)
        return {
            "year": found.year,
            "keys_out": found.keys_out,
            "masters_out": found.masters_out,
            "unaccounted": found.unaccounted,
            "keyholders": found.keyholders,
            "keys_per_holder": round(found.keys_per_holder, 2),
        }

    @router.get("/sheet")
    def sheet(on: date, plain: bool = PLAIN, work: AnnualService = Depends(service)) -> Any:
        return page(work.sheet(on), plain)

    @router.get("/trend")
    def trend(plain: bool = PLAIN, work: AnnualService = Depends(service)) -> Any:
        return page(work.trend(), plain)

    @router.get("/masters")
    def masters(plain: bool = PLAIN, work: AnnualService = Depends(service)) -> Any:
        return page(work.masters(), plain)

    @router.get("/concentration")
    def concentration(
        top: int = Query(default=5, ge=1, le=50), work: AnnualService = Depends(service)
    ) -> dict[str, Any]:
        found = work.concentration(top)
        return {"holders": [{"keyholder": name, "keys": count} for name, count in found]}

    @router.post("", status_code=201)
    def file_it(body: Filing, work: AnnualService = Depends(service)) -> dict[str, Any]:
        return return_shape(work.file_return(body.measured_on, body.filed_by, body.note))

    @router.get("/{year}")
    def read(year: int, work: AnnualService = Depends(service)) -> dict[str, Any]:
        return return_shape(work.filed(year))

    @router.post("/{year}/withdrawal")
    def withdraw(
        year: int, body: Withdrawal, work: AnnualService = Depends(service)
    ) -> dict[str, Any]:
        return return_shape(work.withdraw(year, body.note))

    return router

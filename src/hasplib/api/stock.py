"""Blank stock over http, for the tablet in the store room.

Every movement is a post to its own path rather than a patch that carries a
movement name, because the rules differ per movement and a single endpoint
would have to branch on the body to decide which of them to apply. A cut
needs a reference on a restricted part, a scrapping needs a reason, and a
count needs neither and means something else entirely.

The count endpoint gives back what it found against what the ledger thought,
which is the one number anybody wants from a stocktake.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from hasplib.api.wiring import service_dependency
from hasplib.persistence.models.reference import BlankRow
from hasplib.persistence.models.stock import PurchaseOrderRow, StockLineRow
from hasplib.services.stock import StockService


class Movement(BaseModel):
    quantity: int = Field(ge=1, le=100000)
    moved_on: date
    reference: str = Field(default="", max_length=32)


class Scrapping(BaseModel):
    quantity: int = Field(ge=1, le=100000)
    moved_on: date
    note: str = Field(min_length=1, max_length=512)


class Count(BaseModel):
    counted: int = Field(ge=0, le=1000000)
    moved_on: date
    note: str = Field(default="", max_length=512)


class OrderLine(BaseModel):
    part_number: str = Field(min_length=1, max_length=32)
    quantity: int = Field(ge=1, le=100000)


class NewOrder(BaseModel):
    number: str = Field(min_length=1, max_length=32)
    supplier: str = Field(min_length=1, max_length=128)
    placed_on: date
    lines: list[OrderLine] = Field(min_length=1)


class Delivery(BaseModel):
    part_number: str = Field(min_length=1, max_length=32)
    quantity: int = Field(ge=1, le=100000)
    arrived_on: date


class Cancellation(BaseModel):
    note: str = Field(min_length=1, max_length=512)


def blank_shape(row: BlankRow) -> dict[str, Any]:
    return {
        "part_number": row.part_number,
        "manufacturer": row.manufacturer,
        "chambers": row.chambers,
        "restricted": row.restricted,
        "stock": row.stock,
        "reorder_at": row.reorder_at,
    }


def line_shape(row: StockLineRow) -> dict[str, Any]:
    return {
        "id": row.id,
        "part_number": row.part_number,
        "movement": row.movement,
        "quantity": row.quantity,
        "signed": row.signed,
        "moved_on": row.moved_on.isoformat(),
        "reference": row.reference,
        "note": row.note,
    }


def order_shape(row: PurchaseOrderRow) -> dict[str, Any]:
    return {
        "number": row.number,
        "supplier": row.supplier,
        "placed_on": row.placed_on.isoformat(),
        "state": row.state,
        "lines": [
            {
                "part_number": line.part_number,
                "ordered": line.ordered,
                "received": line.received,
                "outstanding": line.outstanding,
            }
            for line in row.lines
        ],
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/stock", tags=["stock"])
    service = service_dependency(StockService, shop)

    @router.get("/blanks")
    def blanks(
        restricted: bool = Query(default=False, description="only the restricted parts"),
        work: StockService = Depends(service),
    ) -> dict[str, Any]:
        found = work.blanks.restricted() if restricted else work.blanks.all()
        return {"blanks": [blank_shape(row) for row in found]}

    @router.get("/blanks/{part_number}")
    def blank(part_number: str, work: StockService = Depends(service)) -> dict[str, Any]:
        row = work.blank(part_number)
        return blank_shape(row) | {"ledger_total": work.ledger_total(part_number)}

    @router.get("/blanks/{part_number}/movements")
    def movements(part_number: str, work: StockService = Depends(service)) -> dict[str, Any]:
        return {
            "part_number": part_number,
            "movements": [line_shape(row) for row in work.movements(part_number)],
        }

    @router.post("/blanks/{part_number}/receipts", status_code=201)
    def receive(
        part_number: str, body: Movement, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        return line_shape(work.receive(part_number, body.quantity, body.moved_on, body.reference))

    @router.post("/blanks/{part_number}/cuts", status_code=201)
    def cut(
        part_number: str, body: Movement, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        return line_shape(work.cut(part_number, body.quantity, body.moved_on, body.reference))

    @router.post("/blanks/{part_number}/scrappings", status_code=201)
    def scrap(
        part_number: str, body: Scrapping, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        return line_shape(work.scrap(part_number, body.quantity, body.moved_on, body.note))

    @router.post("/blanks/{part_number}/returns", status_code=201)
    def put_back(
        part_number: str, body: Movement, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        return line_shape(work.put_back(part_number, body.quantity, body.moved_on, body.reference))

    @router.post("/blanks/{part_number}/count")
    def count(
        part_number: str, body: Count, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        found = work.count(part_number, body.counted, body.moved_on, body.note)
        return {
            "part_number": found.part_number,
            "expected": found.expected,
            "counted": found.counted,
            "difference": found.difference,
            "agrees": found.agrees,
            "note": found.describe(),
        }

    @router.get("/reorder")
    def reorder(work: StockService = Depends(service)) -> dict[str, Any]:
        return {
            "below": [
                {"part_number": part, "stock": stock, "reorder_at": level}
                for part, stock, level in work.below_reorder()
            ]
        }

    @router.get("/cut-against/{reference}")
    def against(reference: str, work: StockService = Depends(service)) -> dict[str, Any]:
        return {
            "reference": reference,
            "cuts": [line_shape(row) for row in work.cut_against(reference)],
        }

    @router.post("/orders", status_code=201)
    def place(body: NewOrder, work: StockService = Depends(service)) -> dict[str, Any]:
        wanted = [(line.part_number, line.quantity) for line in body.lines]
        return order_shape(work.place_order(body.number, body.supplier, body.placed_on, wanted))

    @router.get("/orders")
    def outstanding(work: StockService = Depends(service)) -> dict[str, Any]:
        return {"orders": [order_shape(row) for row in work.orders.outstanding()]}

    @router.get("/orders/{number}")
    def read_order(number: str, work: StockService = Depends(service)) -> dict[str, Any]:
        return order_shape(work.order(number))

    @router.post("/orders/{number}/deliveries")
    def book_in(
        number: str, body: Delivery, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        work.book_in(number, body.part_number, body.quantity, body.arrived_on)
        return order_shape(work.order(number))

    @router.post("/orders/{number}/cancellation")
    def cancel(
        number: str, body: Cancellation, work: StockService = Depends(service)
    ) -> dict[str, Any]:
        return order_shape(work.cancel_order(number, body.note))

    return router

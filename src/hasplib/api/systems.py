"""Keying systems over http: draw a symbol, cut it, read the schedule back.

The bodies are pydantic models rather than raw dictionaries so that a request
carrying a bitting with a letter in it is refused before it reaches the
domain, and refused with the field name attached. Everything past that point
is a service call and a shape.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.services.systems import SystemService


class NewSystem(BaseModel):
    """What it takes to open a keying system."""

    name: str = Field(min_length=1, max_length=128)
    chart_id: int = Field(gt=0)
    family_name: str = Field(min_length=1, max_length=128)
    top_section: str = Field(min_length=1, max_length=32)
    progression_step: int = Field(default=2, ge=1, le=6)


class NewSymbol(BaseModel):
    """A symbol going on the schedule, cut or not."""

    symbol: str = Field(min_length=1, max_length=16)
    section: str | None = Field(default=None, max_length=32)


class Cut(BaseModel):
    """A bitting going onto a symbol."""

    bitting: str = Field(min_length=1, max_length=48)


def entry_shape(entry: Any) -> dict[str, Any]:
    return {
        "symbol": entry.symbol.render(),
        "level": entry.symbol.level,
        "section": entry.section,
        "bitting": entry.bitting.render() if entry.bitting else None,
        "note": entry.note,
    }


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/systems", tags=["systems"])

    def session() -> Any:
        yield from shop.open_session()

    def service(db: Session = Depends(session)) -> SystemService:
        return SystemService(db, shop.settings.cutting)

    @router.post("", status_code=201)
    def open_system(body: NewSystem, work: SystemService = Depends(service)) -> dict[str, Any]:
        row = work.open_system(
            body.name,
            body.chart_id,
            body.family_name,
            body.top_section,
            body.progression_step,
        )
        return {"name": row.name, "id": row.id, "step": row.progression_step}

    @router.get("/{name}")
    def read_system(name: str, work: SystemService = Depends(service)) -> dict[str, Any]:
        row = work.system(name)
        schedule = work.schedule(name)
        return {
            "name": row.name,
            "family": row.family_name,
            "top_section": row.top_section,
            "archived": row.archived,
            "symbols": len(schedule),
            "open_symbols": len(schedule.open_symbols()),
        }

    @router.get("/{name}/schedule")
    def read_schedule(name: str, work: SystemService = Depends(service)) -> dict[str, Any]:
        schedule = work.schedule(name)
        return {"system": name, "entries": [entry_shape(e) for e in schedule.entries()]}

    @router.post("/{name}/symbols", status_code=201)
    def draw_symbol(
        name: str, body: NewSymbol, work: SystemService = Depends(service)
    ) -> dict[str, Any]:
        row = work.draw(name, KeySymbol.parse(body.symbol), body.section)
        return {"symbol": row.symbol.render(), "section": row.section}

    @router.put("/{name}/symbols/{symbol}/cut")
    def cut_symbol(
        name: str, symbol: str, body: Cut, work: SystemService = Depends(service)
    ) -> dict[str, Any]:
        cut = Bitting.parse(body.bitting)
        row = work.cut(name, KeySymbol.parse(symbol), cut)
        return {"symbol": row.symbol.render(), "bitting": cut.render()}

    @router.delete("/{name}/symbols/{symbol}/cut")
    def uncut_symbol(
        name: str, symbol: str, work: SystemService = Depends(service)
    ) -> dict[str, Any]:
        row = work.uncut(name, KeySymbol.parse(symbol))
        return {"symbol": row.symbol.render(), "bitting": None}

    @router.get("/{name}/review")
    def review_system(name: str, work: SystemService = Depends(service)) -> dict[str, Any]:
        findings = work.review(name)
        return {"system": name, "findings": [finding.as_dict() for finding in findings]}

    return router

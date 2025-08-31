"""Bench work over http, which is the one part of this a phone actually reads.

The chart endpoint gives back lines because that is what gets propped against
the vice. The stack endpoint gives back numbers because that is what a second
program checks the first one with. Both come off the same pinning, so they
cannot drift.

Drawing brass is a post and not a get for the obvious reason: it takes parts
out of a cabinet somebody else is drawing from too. It is all or nothing, so
a kit that cannot fill the order comes back a 409 naming the bins that were
short, and the cabinet is untouched.

The family comes off the catalogue rather than off the request. A caller who
could name a column height could pin a cylinder that never reaches the shear
line, and the first anybody would hear of it is a key that will not turn.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Query
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from hasplib.api.wiring import session_dependency
from hasplib.pinning.cylinder import CylinderPinning
from hasplib.pinning.segments import DriverProfile, SegmentKind, standard_kit
from hasplib.pinning.stack import PinningSpec
from hasplib.reports.page import as_text
from hasplib.services.pinning import PinningService
from hasplib.spec.catalog import family

BENCH_KIT = "bench cabinet"
DEFAULT_MAKER = "Schlage"
DEFAULT_FAMILY = "C keyway six pin"


class KitStock(BaseModel):
    """What a cabinet was stocked with, for a caller doing a dry run."""

    bottoms: list[int] = Field(min_length=1)
    wafers: list[int] = Field(min_length=1)
    drivers: list[int] = Field(min_length=1)
    count: int = Field(default=100, ge=1, le=10000)


class DrawOrder(BaseModel):
    system: str = Field(min_length=1, max_length=64)
    group: str = Field(min_length=1, max_length=64)
    profile: DriverProfile = DriverProfile.STANDARD
    kit: KitStock


def stack_shape(pinning: CylinderPinning) -> list[dict[str, Any]]:
    """Every chamber, floor first, in the order it gets loaded."""
    found = []
    for chamber in range(1, pinning.chambers + 1):
        stack = pinning.stack(chamber)
        found.append(
            {
                "chamber": chamber,
                "bottom": stack.bottom_mils,
                "wafers": list(stack.wafers_mils),
                "driver": stack.driver_mils,
                "profile": stack.driver_profile.value,
                "breaks": list(stack.break_heights),
                "height": stack.height_mils,
            }
        )
    return found


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/pinning", tags=["pinning"])

    session = session_dependency(shop)

    def spec(
        maker: str = Query(default=DEFAULT_MAKER, alias="manufacturer"),
        name: str = Query(default=DEFAULT_FAMILY, alias="family"),
    ) -> PinningSpec:
        return family(maker, name).pinning

    def service(
        db: Session = Depends(session), pinning: PinningSpec = Depends(spec)
    ) -> PinningService:
        return PinningService(db, pinning)

    @router.get("/{system}/{group}")
    def stacks(
        system: str,
        group: str,
        profile: DriverProfile = Query(default=DriverProfile.STANDARD),
        work: PinningService = Depends(service),
    ) -> dict[str, Any]:
        pinning = work.pin(system, group, profile)
        return {
            "system": system,
            "group": group,
            "chambers": pinning.chambers,
            "wafers": pinning.wafer_count,
            "pieces": pinning.piece_count,
            "mastered": list(pinning.mastered_chambers),
            "operating_keys": pinning.operating_key_count,
            "incidental_keys": pinning.incidental_key_count,
            "stacks": stack_shape(pinning),
        }

    @router.get("/{system}/{group}/chart")
    def chart(
        system: str,
        group: str,
        serial: str = Query(default=""),
        plain: bool = Query(default=False),
        work: PinningService = Depends(service),
    ) -> Any:
        lines = work.chart_for(system, group, serial)
        if plain:
            return PlainTextResponse(as_text(lines))
        return {"lines": list(lines)}

    @router.get("/{system}/{group}/keys")
    def keys(system: str, group: str, work: PinningService = Depends(service)) -> dict[str, Any]:
        return {
            "symbols": [symbol.render() for symbol in work.symbols_for(group)],
            "bittings": [cut.render() for cut in work.bittings_for(system, group)],
        }

    @router.get("/groups/{group}/cylinders")
    def cylinders(group: str, work: PinningService = Depends(service)) -> dict[str, Any]:
        return {
            "group": group,
            "cylinders": [
                {
                    "serial": row.serial,
                    "format": row.format,
                    "section": row.section,
                    "chambers": row.chambers,
                    "installed_at": row.installed_at,
                }
                for row in work.cylinders_for(group)
            ],
        }

    @router.post("/draw")
    def draw(body: DrawOrder, work: PinningService = Depends(service)) -> dict[str, Any]:
        kit = standard_kit(
            BENCH_KIT, body.kit.bottoms, body.kit.wafers, body.kit.drivers, body.kit.count
        )
        before = kit.total()
        pinning, summary = work.draw_from(kit, body.system, body.group)
        parts = pinning.parts()
        return {
            "drawn": before - kit.total(),
            "left": kit.total(),
            "pieces": pinning.piece_count,
            "summary": list(summary),
            "bins": {
                kind.value: sum(1 for part in parts if part.kind is kind) for kind in SegmentKind
            },
        }

    return router

"""Where the routers get attached. One import, so app.py stays readable."""

from __future__ import annotations

from fastapi import FastAPI

from hasplib.api.annual import build_router as annual_router
from hasplib.api.boxes import build_router as boxes_router
from hasplib.api.doors import build_router as doors_router
from hasplib.api.incidents import build_router as incidents_router
from hasplib.api.keys import build_router as keys_router
from hasplib.api.pinning import build_router as pinning_router
from hasplib.api.reports import build_router as reports_router
from hasplib.api.stamping import build_router as stamping_router
from hasplib.api.stock import build_router as stock_router
from hasplib.api.systems import build_router as systems_router
from hasplib.api.transfer import build_router as transfer_router


def register_routes(app: FastAPI, shop: object) -> FastAPI:
    """Attach every router this service exposes."""
    app.include_router(systems_router(shop))
    app.include_router(keys_router(shop))
    app.include_router(incidents_router(shop))
    app.include_router(doors_router(shop))
    app.include_router(reports_router(shop))
    app.include_router(pinning_router(shop))
    app.include_router(stock_router(shop))
    app.include_router(boxes_router(shop))
    app.include_router(transfer_router(shop))
    app.include_router(annual_router(shop))
    app.include_router(stamping_router(shop))
    return app

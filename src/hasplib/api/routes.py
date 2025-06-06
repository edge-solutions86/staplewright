"""Where the routers get attached. One import, so app.py stays readable."""

from __future__ import annotations

from fastapi import FastAPI

from hasplib.api.incidents import build_router as incidents_router
from hasplib.api.keys import build_router as keys_router
from hasplib.api.systems import build_router as systems_router


def register_routes(app: FastAPI, shop: object) -> FastAPI:
    """Attach every router this service exposes."""
    app.include_router(systems_router(shop))
    app.include_router(keys_router(shop))
    app.include_router(incidents_router(shop))
    return app

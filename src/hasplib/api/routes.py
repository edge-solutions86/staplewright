"""Where the routers get attached, which is one list and one loop.

Every router was two lines: an aliased import at the top and an include
at the bottom. Eleven routers in, two of them had been imported and never
attached, and nothing failed because an unattached router is just a
function nobody calls.
"""

from __future__ import annotations

from collections.abc import Sequence
from types import ModuleType

from fastapi import FastAPI

from hasplib.api import (
    annual,
    boxes,
    doors,
    health,
    incidents,
    keys,
    pinning,
    reports,
    stamping,
    stock,
    systems,
    transfer,
)

ROUTERS: Sequence[ModuleType] = (
    health,
    annual,
    boxes,
    doors,
    incidents,
    keys,
    pinning,
    reports,
    stamping,
    stock,
    systems,
    transfer,
)
"""Every router, in the order they are attached.

Order matters in one place only. Health comes first because nothing else
claims a path under it, and a router that could shadow another one has to
come after it.
"""


def register_routes(app: FastAPI, shop: object) -> FastAPI:
    """Attach every router this service exposes."""
    for module in ROUTERS:
        app.include_router(module.build_router(shop))
    return app

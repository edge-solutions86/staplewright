"""The application, and the one thing it does that is not routing.

Every refusal in this library is a HaspError with a code, and every one of
them has an obvious http status. Turning them into responses in one handler
means a route never writes a status code by hand, and the shape of an error
body is the same wherever it came from: the code, the message, and the facts
that produced it.

The status map is deliberately small. Not found is a 404, a conflict is a
409, bad input is a 422, and everything else is a 400, because a client that
gets a 500 from a business rule has been told the wrong thing.
"""

from __future__ import annotations

from collections.abc import Iterator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from hasplib import __version__
from hasplib.errors import (
    CapacityError,
    ConflictError,
    HaspError,
    InputError,
    NotFoundError,
)
from hasplib.persistence.engine import build_sessions
from hasplib.settings import ShopSettings, from_env

STATUS = {
    NotFoundError: 404,
    ConflictError: 409,
    InputError: 422,
    CapacityError: 409,
}
DEFAULT_STATUS = 400


def status_for(error: HaspError) -> int:
    """The status one refusal gets, by the closest class that names one."""
    for kind in type(error).__mro__:
        found = STATUS.get(kind)  # type: ignore[arg-type]
        if found is not None:
            return found
    return DEFAULT_STATUS


class Shop:
    """What the routes reach for: sessions and settings, and nothing else."""

    def __init__(self, engine: Engine, settings: ShopSettings | None = None) -> None:
        self.engine = engine
        self.sessions: sessionmaker[Session] = build_sessions(engine)
        self.settings = settings or from_env()

    def open_session(self) -> Iterator[Session]:
        session = self.sessions()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()


def build_app(engine: Engine, settings: ShopSettings | None = None) -> FastAPI:
    """The application, wired to one database."""
    shop = Shop(engine, settings)
    app = FastAPI(
        title="hasplib",
        version=__version__,
        description="key control and master key system design for the lock shop",
    )
    app.state.shop = shop

    @app.exception_handler(HaspError)
    async def refused(request: Request, error: HaspError) -> JSONResponse:
        return JSONResponse(status_code=status_for(error), content=error.as_dict())

    @app.get("/settings", tags=["service"])
    def settings_lines() -> dict[str, list[str]]:
        return {"settings": list(shop.settings.describe())}

    from hasplib.api.routes import register_routes

    register_routes(app, shop)
    return app

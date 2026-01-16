"""The application, and the one thing it does that is not routing.

Every refusal in this library is a HaspError with a code, and every one of
them has an obvious http status. Turning them into responses in one handler
means a route never writes a status code by hand, and the shape of an error
body is the same wherever it came from: the code, the message, and the facts
that produced it.

The status comes off the refusal catalogue rather than a map declared here.
There were two maps for a while, one in this file and one in the interface
note, and they disagreed about what a capacity refusal is.
"""

from __future__ import annotations

from collections.abc import Iterator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from hasplib import __version__
from hasplib.catalogue import status_for
from hasplib.errors import HaspError
from hasplib.persistence.engine import build_sessions
from hasplib.settings import ShopSettings, from_env


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

    @app.get("/refusals", tags=["service"])
    def refusals() -> dict[str, list[dict[str, object]]]:
        from hasplib.catalogue import CATALOGUE

        return {
            "refusals": [
                {
                    "code": item.code,
                    "status": item.status,
                    "meaning": item.meaning,
                    "retry": item.retry,
                }
                for item in CATALOGUE
            ]
        }

    @app.get("/settings", tags=["service"])
    def settings_lines() -> dict[str, list[str]]:
        return {"settings": list(shop.settings.describe())}

    from hasplib.api.routes import register_routes

    register_routes(app, shop)
    return app

"""The wiring every router had written out for itself.

Six routers each declared the same session dependency, and five of them then
declared a one line function to turn a service class into a dependency. The
copies had already drifted once: one of them opened a session per request and
one of them opened a session per dependency, so a request that used two
services in the same handler committed one of them and not the other.

``PLAIN`` is here for the same reason. Eight report endpoints each declared
their own text flag, three of them with a different description, and one of
them had it as a path parameter by accident.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from contextlib import suppress
from typing import Any, TypeVar

from fastapi import Depends, Query
from fastapi.responses import PlainTextResponse
from sqlalchemy.orm import Session

from hasplib.reports.page import as_text

ServiceType = TypeVar("ServiceType")

PLAIN = Query(default=False, description="give it back as plain text")


HELD = "_hasplib_session_dependency"


def session_dependency(shop: Any) -> Callable[[], Iterator[Session]]:
    """One session per request, committed on the way out or rolled back.

    The same callable comes back every time for a given shop, and that is
    load bearing rather than an optimisation. The framework caches a resolved
    dependency by the function object, so two services built from two
    separate closures would each open their own session inside one request,
    and a handler that used both would commit one of them and not the other.
    """
    held = getattr(shop, HELD, None)
    if held is not None:
        return held

    def session() -> Iterator[Session]:
        yield from shop.open_session()

    with suppress(AttributeError):  # a shop defined with slots keeps none
        setattr(shop, HELD, session)
    return session


def service_dependency(
    kind: Callable[..., ServiceType], shop: Any, *args: Any
) -> Callable[..., ServiceType]:
    """A service built on the request's session, ready to hand to Depends."""
    opener = session_dependency(shop)

    def build(db: Session = Depends(opener)) -> ServiceType:
        return kind(db, *args)

    return build


def page(lines: tuple[str, ...], plain: bool) -> Any:
    """A report, as a list for a program or as text for a person."""
    if plain:
        return PlainTextResponse(as_text(lines))
    return {"lines": list(lines)}

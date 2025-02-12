"""Column types that carry domain objects in and out without a converter.

A bitting is a tuple of small integers and it wants to be readable in the
database, because the first thing anybody does with the key table is open it
in a client and look. So it goes in as the shop writes it, dotted when the
chart goes past nine depths and packed when it does not, and comes back as a
Bitting rather than as a string somebody has to remember to parse.

The same goes for key symbols. Storing AB3 and parsing it on the way out
keeps the queries legible and keeps the parsing rules in one place.
"""

from __future__ import annotations

from typing import Any

from sqlalchemy import Dialect, String, TypeDecorator

from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol


class BittingColumn(TypeDecorator[Bitting]):
    """A cut sequence, stored the way the shop writes it."""

    impl = String(48)
    cache_ok = True

    def process_bind_param(self, value: Any, dialect: Dialect) -> str | None:
        if value is None:
            return None
        if isinstance(value, Bitting):
            return value.render()
        if isinstance(value, str):
            return Bitting.parse(value).render()
        raise TypeError(f"cannot store {type(value).__name__} as a bitting")

    def process_result_value(self, value: Any, dialect: Dialect) -> Bitting | None:
        if value is None:
            return None
        return Bitting.parse(value)


class SymbolColumn(TypeDecorator[KeySymbol]):
    """A key symbol, stored as it is printed."""

    impl = String(16)
    cache_ok = True

    def process_bind_param(self, value: Any, dialect: Dialect) -> str | None:
        if value is None:
            return None
        if isinstance(value, KeySymbol):
            return value.render()
        if isinstance(value, str):
            return KeySymbol.parse(value).render()
        raise TypeError(f"cannot store {type(value).__name__} as a key symbol")

    def process_result_value(self, value: Any, dialect: Dialect) -> KeySymbol | None:
        if value is None:
            return None
        return KeySymbol.parse(value)


class DepthListColumn(TypeDecorator[tuple]):
    """A run of chamber numbers or depths, stored comma separated.

    Used for the small ordered lists that are always read whole and never
    queried into: progressed chambers, ball chambers, wafer thicknesses.
    Anything that wants a where clause gets its own table instead.
    """

    impl = String(96)
    cache_ok = True

    def process_bind_param(self, value: Any, dialect: Dialect) -> str | None:
        if value is None:
            return None
        return ",".join(str(int(item)) for item in value)

    def process_result_value(self, value: Any, dialect: Dialect) -> tuple[int, ...] | None:
        if value is None:
            return None
        if not value:
            return ()
        return tuple(int(part) for part in value.split(","))

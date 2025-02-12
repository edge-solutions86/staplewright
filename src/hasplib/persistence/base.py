"""The declarative base, and the naming convention every constraint follows.

Constraint names are set here rather than left to the database, because
alembic writes migrations that drop constraints by name and sqlite makes up
different ones than postgres does. Naming them in one place is the difference
between a downgrade that works and one that has to be hand edited.

Column widths are declared once and used through annotations, so that a plain
``str`` on a model does not silently become an unbounded text column. The
widths are short on purpose: a key symbol is eight characters and a room
number is sixteen, and a column wide enough to hold a paragraph invites
somebody to put a paragraph in it.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated

from sqlalchemy import DateTime, MetaData, String
from sqlalchemy.orm import DeclarativeBase, mapped_column, registry

NAMING_CONVENTION = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

SHORT = 32
"""Codes, symbols, part numbers. Everything the shop writes by hand."""

MEDIUM = 128
"""Names of people and buildings."""

LONG = 512
"""Notes. Long enough for a sentence, short enough to discourage an essay."""

Code = Annotated[str, mapped_column(String(SHORT))]
"""An identifier somebody types, like MOO-214 or AB3."""

Name = Annotated[str, mapped_column(String(MEDIUM))]
"""A human name or a building name."""

Note = Annotated[str, mapped_column(String(LONG), default="")]
"""Free text that is deliberately not very free."""


class Base(DeclarativeBase):
    """Every table in the schema hangs off this."""

    metadata = MetaData(naming_convention=NAMING_CONVENTION)

    registry = registry(
        type_annotation_map={
            str: String(MEDIUM),
            datetime: DateTime(timezone=True),
        }
    )


def table_names() -> tuple[str, ...]:
    """Every table the metadata knows about, in creation order."""
    return tuple(table.name for table in Base.metadata.sorted_tables)

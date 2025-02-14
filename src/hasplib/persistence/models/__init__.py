"""Every table in the schema, imported so the metadata knows about them.

A table that nobody has imported is not on the metadata, so create_all does
not build it and alembic does not see it. Importing them here means one
import of this package is enough, and the star imports are the point rather
than an accident.
"""

from hasplib.persistence.models.reference import (  # noqa: F401
    BlankRow,
    ChartRow,
    KeywaySectionRow,
)

__all__ = ["BlankRow", "ChartRow", "KeywaySectionRow"]

"""Key control and master key system design for the campus lock shop.

This package is a library first and a service second. Everything below
``hasplib.persistence`` knows there is a database; nothing above it does, and
that line is the reason a keying system can be worked out on a bench with no
database anywhere near it.

The names re-exported here are the ones a caller outside the library is meant
to reach for. Everything else is reachable and none of it is promised: the
mappers, the repositories and the row classes move whenever the schema does.

Where to start, by what you are holding:

* a chart and a set of symbols, and no database at all
  ``hasplib.spec``, ``hasplib.keys`` and ``hasplib.progression``
* a database and a question about it
  ``hasplib.services`` and ``hasplib.persistence.workspace``
* a printed page somebody has asked for
  ``hasplib.reports``
* a terminal
  ``hasplib.cli.main``, or the ``hasplib`` console script
"""

from __future__ import annotations

from hasplib.errors import (
    BittingError,
    CapacityError,
    ConflictError,
    HaspError,
    HierarchyError,
    InputError,
    KeywayError,
    NotFoundError,
    PinningError,
    ProgressionError,
    SpecificationError,
)
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.settings import ShopSettings, from_env, from_mapping
from hasplib.spec.chart import DepthAndSpaceChart
from hasplib.spec.keyway import KeywayFamily, KeywaySection
from hasplib.spec.rules import CuttingRules

__version__ = "0.9.0"

__all__ = [
    "Bitting",
    "BittingError",
    "CapacityError",
    "ConflictError",
    "CuttingRules",
    "DepthAndSpaceChart",
    "HaspError",
    "HierarchyError",
    "InputError",
    "KeySymbol",
    "KeywayError",
    "KeywayFamily",
    "KeywaySection",
    "NotFoundError",
    "PinningError",
    "ProgressionError",
    "ShopSettings",
    "SpecificationError",
    "__version__",
    "from_env",
    "from_mapping",
]

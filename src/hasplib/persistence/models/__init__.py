"""Every table in the schema, imported so the metadata knows about them.

A table that nobody has imported is not on the metadata, so create_all does
not build it and alembic does not see it. Importing them here means one
import of this package is enough, and the re-exports are the point rather
than an accident.
"""

from hasplib.persistence.models.campus import (
    AccessGroupRow,
    AccessRequirementRow,
    BuildingRow,
    CylinderRow,
    OpeningRow,
)
from hasplib.persistence.models.control import (
    AuthorizerRow,
    DepositLineRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
)
from hasplib.persistence.models.counters import CounterRow
from hasplib.persistence.models.keying import (
    GroupMemberRow,
    GroupRow,
    ScheduleEntryRow,
    SystemRow,
)
from hasplib.persistence.models.reference import BlankRow, ChartRow, KeywaySectionRow
from hasplib.persistence.models.rekey import (
    IncidentRow,
    RetiredBittingRow,
    WorkOrderLineRow,
    WorkOrderRow,
)

__all__ = [
    "AccessGroupRow",
    "AccessRequirementRow",
    "AuthorizerRow",
    "BlankRow",
    "BuildingRow",
    "ChartRow",
    "CounterRow",
    "CylinderRow",
    "DepositLineRow",
    "GroupMemberRow",
    "GroupRow",
    "IncidentRow",
    "IssuedKeyRow",
    "KeyRequestRow",
    "KeyholderRow",
    "KeywaySectionRow",
    "OpeningRow",
    "RetiredBittingRow",
    "ScheduleEntryRow",
    "SystemRow",
    "WorkOrderLineRow",
    "WorkOrderRow",
]

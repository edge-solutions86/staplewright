"""Every table in the schema, imported so the metadata knows about them.

A table that nobody has imported is not on the metadata, so create_all does
not build it and alembic does not see it. Importing them here means one
import of this package is enough, and the re-exports are the point rather
than an accident.
"""

from hasplib.persistence.models.access import (
    KeyedAlikeMemberRow,
    KeyedAlikeSetRow,
    ReviewLineRow,
    ReviewRow,
)
from hasplib.persistence.models.annual import AnnualReturnRow
from hasplib.persistence.models.audit import AuditEntryRow
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
from hasplib.persistence.models.escrow import KeyBoxRow, SealRow
from hasplib.persistence.models.facilities import LockoutRow, SurveyLineRow, SurveyRow
from hasplib.persistence.models.interchange import ControlKeyRow, CoreRow
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
from hasplib.persistence.models.stamping import BlindCodeRow
from hasplib.persistence.models.stock import (
    PurchaseLineRow,
    PurchaseOrderRow,
    StockLineRow,
)
from hasplib.persistence.models.transfer import TransferRow

__all__ = [
    "AccessGroupRow",
    "AccessRequirementRow",
    "AnnualReturnRow",
    "AuditEntryRow",
    "AuthorizerRow",
    "BlankRow",
    "BlindCodeRow",
    "BuildingRow",
    "ChartRow",
    "ControlKeyRow",
    "CoreRow",
    "CounterRow",
    "CylinderRow",
    "DepositLineRow",
    "GroupMemberRow",
    "GroupRow",
    "IncidentRow",
    "IssuedKeyRow",
    "KeyRequestRow",
    "KeyedAlikeMemberRow",
    "KeyedAlikeSetRow",
    "KeyBoxRow",
    "KeyholderRow",
    "KeywaySectionRow",
    "LockoutRow",
    "OpeningRow",
    "PurchaseLineRow",
    "PurchaseOrderRow",
    "RetiredBittingRow",
    "ReviewLineRow",
    "ReviewRow",
    "ScheduleEntryRow",
    "SealRow",
    "SurveyLineRow",
    "SurveyRow",
    "StockLineRow",
    "SystemRow",
    "TransferRow",
    "WorkOrderLineRow",
    "WorkOrderRow",
]

"""the adjustment a stocktake found

Revision ID: 0006
Revises: 0005
Created: 2025-09-18 10:22:41.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

CONSTRAINT = "only_a_count_carries_an_adjustment"
CONDITION = "(movement = 'counted') = (adjustment is not null)"


def upgrade() -> None:
    with op.batch_alter_table("blank_stock_lines", schema=None) as batch_op:
        batch_op.add_column(sa.Column("adjustment", sa.Integer(), nullable=True))

    # A count already in the ledger recorded what was in the drawer and not
    # how far the drawer had moved to get there, and nothing left in the
    # ledger can recover that. So the old counts are written down as having
    # found nothing, which is what every report was assuming anyway.
    op.execute("update blank_stock_lines set adjustment = 0 where movement = 'counted'")

    with op.batch_alter_table("blank_stock_lines", schema=None) as batch_op:
        batch_op.create_check_constraint(CONSTRAINT, CONDITION)


def downgrade() -> None:
    with op.batch_alter_table("blank_stock_lines", schema=None) as batch_op:
        batch_op.drop_constraint(CONSTRAINT, type_="check")
        batch_op.drop_column("adjustment")

"""Blank stock, kept as a ledger rather than as a number in a column.

``key_blanks.stock`` was the only record of how many blanks were in the
drawer, and a single number cannot answer the question anybody actually
asks, which is where thirty of them went. So the movements get their own
table and the column becomes a running total that the ledger writes.

Every line points at the part number and says what it did: received against
a purchase order, cut against a key tag, scrapped with a reason, put back,
or counted. A count line is the one that carries a delta the shop did not
choose, which is exactly why it has to be a line and not an edit.

The reference column is deliberately loose. It holds a purchase order on a
receipt, a key tag on a cut, and a work order on a return, and giving each of
those its own nullable foreign key would be three columns that are null on
every row but one.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Index, Integer
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note
from hasplib.persistence.models.reference import BlankRow


class StockLineRow(Base):
    """One movement of blank stock, in or out of the drawer."""

    __tablename__ = "blank_stock_lines"
    __table_args__ = (
        CheckConstraint("quantity > 0", name="quantity_positive"),
        CheckConstraint(
            "movement in ('received', 'cut', 'scrapped', 'returned', 'counted')",
            name="movement_is_known",
        ),
        CheckConstraint("movement <> 'scrapped' or note <> ''", name="a_scrapping_says_why"),
        CheckConstraint(
            "(movement = 'counted') = (adjustment is not null)",
            name="only_a_count_carries_an_adjustment",
        ),
        Index("ix_blank_stock_lines_part_day", "part_number", "moved_on"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    part_number: Mapped[str] = mapped_column(
        ForeignKey("key_blanks.part_number", ondelete="RESTRICT")
    )
    movement: Mapped[Code]
    quantity: Mapped[int]
    moved_on: Mapped[date]
    reference: Mapped[Code] = mapped_column(default="")
    adjustment: Mapped[int | None] = mapped_column(default=None)
    note: Mapped[Note]

    blank: Mapped[BlankRow] = relationship()

    @property
    def signed(self) -> int:
        """What this line does to the running total.

        A count is the odd one out. Its quantity is what was in the drawer,
        not what moved, so the movement is the adjustment beside it. Without
        that column the difference a stocktake found was unrecoverable: the
        drawer went to the counted number and the ledger had no record of how
        far it had moved to get there.
        """
        if self.movement == "counted":
            return self.adjustment or 0
        return -self.quantity if self.movement in ("cut", "scrapped") else self.quantity

    def __repr__(self) -> str:
        return f"<StockLineRow {self.part_number} {self.movement} {self.quantity}>"


class PurchaseOrderRow(Base):
    """One order to a supplier, which blanks arrive against."""

    __tablename__ = "blank_purchase_orders"
    __table_args__ = (
        CheckConstraint(
            "state in ('placed', 'part received', 'received', 'cancelled')",
            name="order_state_is_known",
        ),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    supplier: Mapped[Name]
    placed_on: Mapped[date]
    state: Mapped[Code] = mapped_column(default="placed")
    note: Mapped[Note]

    lines: Mapped[list[PurchaseLineRow]] = relationship(
        back_populates="order", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<PurchaseOrderRow {self.number}>"


class PurchaseLineRow(Base):
    """One part number on one order, and how much of it has landed."""

    __tablename__ = "blank_purchase_lines"
    __table_args__ = (
        CheckConstraint("ordered > 0", name="ordered_positive"),
        CheckConstraint("received >= 0", name="received_not_negative"),
        CheckConstraint("received <= ordered", name="not_more_than_ordered"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    order_number: Mapped[str] = mapped_column(
        ForeignKey("blank_purchase_orders.number", ondelete="CASCADE")
    )
    part_number: Mapped[str] = mapped_column(
        ForeignKey("key_blanks.part_number", ondelete="RESTRICT")
    )
    ordered: Mapped[int]
    received: Mapped[int] = mapped_column(default=0)

    order: Mapped[PurchaseOrderRow] = relationship(back_populates="lines")
    blank: Mapped[BlankRow] = relationship()

    @property
    def outstanding(self) -> int:
        return self.ordered - self.received

    def __repr__(self) -> str:
        return f"<PurchaseLineRow {self.order_number} {self.part_number}>"

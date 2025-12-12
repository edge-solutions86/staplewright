"""People, requests, keys that are out, and the money held against them.

The key ledger is the table the shop would rescue from a fire. Everything
else can be rebuilt from a survey and a filing cabinet, but who is holding
what only exists here, and it exists as a row per stamped key rather than a
count, because a count cannot be handed back.

Deposit lines are append only by construction. There is no balance column
anywhere: the balance is the sum of the lines, and the day somebody adds a
cached total is the day it disagrees with the lines.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, ForeignKey, Index, Integer
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note


class KeyholderRow(Base):
    """One person the shop will hand metal to."""

    __tablename__ = "keyholders"
    __table_args__ = (
        CheckConstraint(
            "standing <> 'separated' or separated_on is not null", name="separated_has_a_date"
        ),
    )

    identifier: Mapped[Code] = mapped_column(primary_key=True)
    name: Mapped[Name]
    affiliation: Mapped[Code]
    department: Mapped[Name]
    standing: Mapped[Code] = mapped_column(default="active")
    ends_on: Mapped[date | None] = mapped_column(default=None)
    separated_on: Mapped[date | None] = mapped_column(default=None)

    authorizer: Mapped[AuthorizerRow | None] = relationship(
        back_populates="person", cascade="all, delete-orphan", passive_deletes=True
    )
    keys: Mapped[list[IssuedKeyRow]] = relationship(back_populates="holder")

    def __repr__(self) -> str:
        return f"<KeyholderRow {self.identifier}>"


class AuthorizerRow(Base):
    """Somebody who may sign for keys, and how far the signature reaches."""

    __tablename__ = "authorizers"

    identifier: Mapped[str] = mapped_column(
        ForeignKey("keyholders.identifier", ondelete="CASCADE"), primary_key=True
    )
    department: Mapped[Name]
    buildings: Mapped[Note] = mapped_column(default="")
    may_approve_masters: Mapped[bool] = mapped_column(Boolean, default=False)

    person: Mapped[KeyholderRow] = relationship(back_populates="authorizer")

    @property
    def building_codes(self) -> tuple[str, ...]:
        if not self.buildings:
            return ()
        return tuple(part for part in self.buildings.split(",") if part)

    def __repr__(self) -> str:
        return f"<AuthorizerRow {self.identifier}>"


class KeyRequestRow(Base):
    """One person asking for one key."""

    __tablename__ = "key_requests"
    __table_args__ = (
        CheckConstraint(
            "state not in ('approved', 'denied') or decided_on is not null",
            name="decided_has_a_date",
        ),
        Index("ix_key_requests_state", "state"),
        Index("ix_key_requests_keyholder", "keyholder"),
    )

    number: Mapped[Code] = mapped_column(primary_key=True)
    keyholder: Mapped[str] = mapped_column(ForeignKey("keyholders.identifier", ondelete="RESTRICT"))
    symbol: Mapped[Code]
    building: Mapped[Code]
    reason: Mapped[Note]
    raised_on: Mapped[date]
    state: Mapped[Code] = mapped_column(default="raised")
    is_master: Mapped[bool] = mapped_column(Boolean, default=False)
    decided_on: Mapped[date | None] = mapped_column(default=None)
    decided_by: Mapped[Code] = mapped_column(default="")
    note: Mapped[Note]

    def __repr__(self) -> str:
        return f"<KeyRequestRow {self.number}>"


class IssuedKeyRow(Base):
    """One stamped key and whoever signed for it."""

    __tablename__ = "issued_keys"
    __table_args__ = (
        CheckConstraint("deposit_cents >= 0", name="deposit_not_negative"),
        CheckConstraint("due_on is null or due_on >= issued_on", name="due_after_issue"),
        # Every counter question starts with one of these two. Who has what,
        # and what is late, and both of them were reading the whole table.
        Index("ix_issued_keys_holder_state", "keyholder", "state"),
        Index("ix_issued_keys_state_due", "state", "due_on"),
        Index("ix_issued_keys_symbol_state", "symbol", "state"),
    )

    tag: Mapped[Code] = mapped_column(primary_key=True)
    symbol: Mapped[Code]
    keyholder: Mapped[str] = mapped_column(ForeignKey("keyholders.identifier", ondelete="RESTRICT"))
    issued_on: Mapped[date]
    due_on: Mapped[date | None] = mapped_column(default=None)
    deposit_cents: Mapped[int] = mapped_column(default=0)
    state: Mapped[Code] = mapped_column(default="out")
    closed_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    holder: Mapped[KeyholderRow] = relationship(back_populates="keys")
    deposits: Mapped[list[DepositLineRow]] = relationship(back_populates="key")

    @property
    def is_out(self) -> bool:
        return self.state == "out"

    def __repr__(self) -> str:
        return f"<IssuedKeyRow {self.tag}>"


class DepositLineRow(Base):
    """One movement of deposit money. There is no balance column anywhere."""

    __tablename__ = "deposit_lines"
    __table_args__ = (
        CheckConstraint("cents > 0", name="cents_positive"),
        Index("ix_deposit_lines_keyholder", "keyholder"),
        Index("ix_deposit_lines_tag", "tag"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    tag: Mapped[str] = mapped_column(ForeignKey("issued_keys.tag", ondelete="RESTRICT"))
    keyholder: Mapped[Code]
    kind: Mapped[Code]
    cents: Mapped[int]
    happened_on: Mapped[date]
    note: Mapped[Note]

    key: Mapped[IssuedKeyRow] = relationship(back_populates="deposits")

    @property
    def signed_cents(self) -> int:
        if self.kind in {"received", "charge"}:
            return self.cents
        return -self.cents

    def __repr__(self) -> str:
        return f"<DepositLineRow {self.tag} {self.kind}>"

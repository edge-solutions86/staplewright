"""Access reviews and keyed alike sets, both of which have a rule to enforce.

The keyed alike membership table carries a unique constraint on the opening
alone rather than on the pair. That is the domain rule written into the
schema: a door belongs to one set or to none, and the second set that tries
to claim it is refused by the database rather than by whichever service
happened to check first.

The review tables are duller. A department is reviewed once per opening date,
a key appears once on a review, and an answered line has a date. Everything
else about a review is policy and lives in the code.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, ForeignKey, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from hasplib.persistence.base import Base, Code, Name, Note


class ReviewRow(Base):
    """One department, one year, one list of keys."""

    __tablename__ = "access_reviews"
    __table_args__ = (
        UniqueConstraint("department", "opened_on"),
        CheckConstraint("due_on >= opened_on", name="due_after_opened"),
        CheckConstraint("state <> 'closed' or closed_on is not null", name="closed_has_a_date"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    department: Mapped[Name]
    opened_on: Mapped[date]
    due_on: Mapped[date]
    state: Mapped[Code] = mapped_column(default="open")
    closed_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    lines: Mapped[list[ReviewLineRow]] = relationship(
        back_populates="review", cascade="all, delete-orphan"
    )

    @property
    def is_open(self) -> bool:
        return self.state == "open"

    def __repr__(self) -> str:
        return f"<ReviewRow {self.department} {self.opened_on}>"


class ReviewLineRow(Base):
    """One key on one department's list."""

    __tablename__ = "access_review_lines"
    __table_args__ = (
        UniqueConstraint("review_id", "tag"),
        CheckConstraint(
            "answer = 'unanswered' or answered_on is not null", name="answered_has_a_date"
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    review_id: Mapped[int] = mapped_column(ForeignKey("access_reviews.id", ondelete="CASCADE"))
    tag: Mapped[Code]
    keyholder: Mapped[Code]
    symbol: Mapped[Code]
    answer: Mapped[Code] = mapped_column(default="unanswered")
    answered_on: Mapped[date | None] = mapped_column(default=None)
    note: Mapped[Note]

    review: Mapped[ReviewRow] = relationship(back_populates="lines")

    @property
    def is_answered(self) -> bool:
        return self.answer != "unanswered"

    def __repr__(self) -> str:
        return f"<ReviewLineRow {self.tag} {self.answer}>"


class KeyedAlikeSetRow(Base):
    """A run of openings that carry one key between them."""

    __tablename__ = "keyed_alike_sets"

    name: Mapped[Name] = mapped_column(primary_key=True)
    note: Mapped[Note]

    members: Mapped[list[KeyedAlikeMemberRow]] = relationship(
        back_populates="alike", cascade="all, delete-orphan"
    )

    @property
    def openings(self) -> tuple[str, ...]:
        return tuple(sorted(member.opening_number for member in self.members))

    def __repr__(self) -> str:
        return f"<KeyedAlikeSetRow {self.name}>"


class KeyedAlikeMemberRow(Base):
    """One door in one set, and in no other."""

    __tablename__ = "keyed_alike_members"
    __table_args__ = (UniqueConstraint("opening_number"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    set_name: Mapped[str] = mapped_column(ForeignKey("keyed_alike_sets.name", ondelete="CASCADE"))
    opening_number: Mapped[str] = mapped_column(ForeignKey("openings.number", ondelete="RESTRICT"))

    alike: Mapped[KeyedAlikeSetRow] = relationship(back_populates="members")

    def __repr__(self) -> str:
        return f"<KeyedAlikeMemberRow {self.opening_number}>"

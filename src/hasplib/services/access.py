"""Reviews and keyed alike sets against the database.

The review service does one thing worth explaining. Opening a review reads
the ledger and writes a line for every key the department is holding at that
moment, and those lines do not move afterwards. A key issued the following
week is not on this year's review, which is correct: the review is a
statement about a date, and a list that kept changing under the department
would never be answerable.

The keyed alike service leans on the database for its one rule. A door
belongs to one set, the unique constraint says so, and the service does not
check first: it writes and lets the constraint refuse, because checking first
is a race and the constraint is not.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from hasplib.campus.keyed_alike import KeyedAlikeSet
from hasplib.control.review import Answer, Review, ReviewState
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.lifecycle import require_state
from hasplib.persistence.loaders import load_ledger
from hasplib.persistence.mappers.access import (
    alike_from_row,
    alike_to_rows,
    review_from_rows,
    review_line_to_row,
)
from hasplib.persistence.models.access import (
    KeyedAlikeMemberRow,
    KeyedAlikeSetRow,
    ReviewLineRow,
    ReviewRow,
)


class ReviewService:
    """One department's annual review, from opening it to closing it."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def open_review(self, department: str, opened_on: date, due_on: date) -> ReviewRow:
        """Open a review and freeze the department's holdings onto it."""
        if self._find(department, opened_on) is not None:
            raise ConflictError(
                "that department was already reviewed on that day",
                department=department,
                opened_on=opened_on.isoformat(),
            )
        review = Review(department, opened_on, due_on)
        review.build_from(load_ledger(self.session))
        header, lines = review_to_header(review)
        self.session.add(header)
        self.session.flush()
        for row in lines:
            row.review_id = header.id
            self.session.add(row)
        self.session.flush()
        return header

    def review(self, department: str, opened_on: date) -> Review:
        return review_from_rows(self._must_find(department, opened_on))

    def answer(
        self,
        department: str,
        opened_on: date,
        tag: str,
        answer: Answer,
        when: date,
        note: str = "",
    ) -> ReviewLineRow:
        header = self._must_find(department, opened_on)
        require_state(
            header.state,
            {ReviewState.OPEN.value},
            "that review is closed",
            department=department,
        )
        if answer is Answer.UNANSWERED:
            raise InputError("an answer says something", tag=tag)
        line = self._line(header.id, tag)
        if line.is_answered:
            raise ConflictError("that key has already been answered", tag=tag)
        line.answer = answer.value
        line.answered_on = when
        line.note = note
        self.session.flush()
        return line

    def close(self, department: str, opened_on: date, when: date) -> ReviewRow:
        header = self._must_find(department, opened_on)
        require_state(
            header.state, {ReviewState.OPEN.value}, "that review is closed", department=department
        )
        header.state = ReviewState.CLOSED.value
        header.closed_on = when
        self.session.flush()
        return header

    def open_reviews(self) -> Sequence[ReviewRow]:
        statement = select(ReviewRow).where(ReviewRow.state == ReviewState.OPEN.value)
        return self.session.execute(statement.order_by(ReviewRow.department)).scalars().all()

    def _find(self, department: str, opened_on: date) -> ReviewRow | None:
        statement = select(ReviewRow).where(
            ReviewRow.department == department, ReviewRow.opened_on == opened_on
        )
        return self.session.execute(statement).scalar_one_or_none()

    def _must_find(self, department: str, opened_on: date) -> ReviewRow:
        row = self._find(department, opened_on)
        if row is None:
            raise NotFoundError(
                "no review of that department on that day",
                department=department,
                opened_on=opened_on.isoformat(),
            )
        return row

    def _line(self, review_id: int, tag: str) -> ReviewLineRow:
        statement = select(ReviewLineRow).where(
            ReviewLineRow.review_id == review_id, ReviewLineRow.tag == tag
        )
        row = self.session.execute(statement).scalar_one_or_none()
        if row is None:
            raise NotFoundError("that key is not on this review", tag=tag)
        return row


def review_to_header(review: Review) -> tuple[ReviewRow, list[ReviewLineRow]]:
    """The header and the lines, before either has an id."""
    header = ReviewRow(
        department=review.department,
        opened_on=review.opened_on,
        due_on=review.due_on,
        state=review.state.value,
        closed_on=review.closed_on,
    )
    return header, [review_line_to_row(line, 0) for line in review.lines()]


class KeyedAlikeService:
    """Sets of doors that carry one key, with the database as the referee."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def create(self, alike: KeyedAlikeSet) -> KeyedAlikeSetRow:
        """Write a set, letting the unique constraint refuse a shared door."""
        header, members = alike_to_rows(alike)
        self.session.add(header)
        for row in members:
            self.session.add(row)
        try:
            self.session.flush()
        except IntegrityError as clash:
            self.session.rollback()
            raise ConflictError(
                "one of those doors is already in a set", name=alike.name
            ) from clash
        return header

    def get(self, name: str) -> KeyedAlikeSet:
        row = self.session.get(KeyedAlikeSetRow, name)
        if row is None:
            raise NotFoundError("no such keyed alike set", name=name)
        return alike_from_row(row)

    def set_holding(self, opening: str) -> KeyedAlikeSet | None:
        statement = select(KeyedAlikeMemberRow).where(KeyedAlikeMemberRow.opening_number == opening)
        member = self.session.execute(statement).scalar_one_or_none()
        if member is None:
            return None
        return self.get(member.set_name)

    def dissolve(self, name: str) -> None:
        row = self.session.get(KeyedAlikeSetRow, name)
        if row is None:
            raise NotFoundError("no such keyed alike set", name=name)
        self.session.delete(row)
        self.session.flush()

    def sets(self) -> tuple[KeyedAlikeSet, ...]:
        statement = select(KeyedAlikeSetRow).order_by(KeyedAlikeSetRow.name)
        rows = self.session.execute(statement).scalars().all()
        return tuple(alike_from_row(row) for row in rows)

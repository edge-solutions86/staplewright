"""Reviews and keyed alike sets, between the tables and the objects.

The review is the awkward one. A Review is built by naming a department and
two dates and then having lines put into it, so rebuilding one means walking
the rows twice: once to make the header and once to load the answers. There
is no constructor that takes everything at once, on purpose, because the
front door is the only way a live review is ever built and a second one would
drift from it.

The answers go in and out as their own text, and a word this version does not
recognise reads as unanswered. That loses information, and the alternative is
refusing to open a review from 2019 because somebody once typed a fifth kind
of answer into it.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.campus.keyed_alike import KeyedAlikeRegister, KeyedAlikeSet
from hasplib.control.review import Answer, Review, ReviewLine, ReviewState
from hasplib.persistence.models.access import (
    KeyedAlikeMemberRow,
    KeyedAlikeSetRow,
    ReviewLineRow,
    ReviewRow,
)


def _answer(value: str) -> Answer:
    for item in Answer:
        if item.value == value:
            return item
    return Answer.UNANSWERED


def _state(value: str) -> ReviewState:
    for item in ReviewState:
        if item.value == value:
            return item
    return ReviewState.OPEN


def review_line_to_row(line: ReviewLine, review_id: int) -> ReviewLineRow:
    return ReviewLineRow(
        review_id=review_id,
        tag=line.tag,
        keyholder=line.keyholder,
        symbol=line.symbol,
        answer=line.answer.value,
        answered_on=line.answered_on,
        note=line.note,
    )


def review_line_from_row(row: ReviewLineRow) -> ReviewLine:
    return ReviewLine(
        tag=row.tag,
        keyholder=row.keyholder,
        symbol=row.symbol,
        answer=_answer(row.answer),
        answered_on=row.answered_on,
        note=row.note,
    )


def review_to_rows(review: Review) -> tuple[ReviewRow, list[ReviewLineRow]]:
    """A header and its lines, with the id left for the caller to fill in."""
    header = ReviewRow(
        department=review.department,
        opened_on=review.opened_on,
        due_on=review.due_on,
        state=review.state.value,
        closed_on=review.closed_on,
    )
    lines = [review_line_to_row(line, 0) for line in review.lines()]
    return header, lines


def review_from_rows(row: ReviewRow) -> Review:
    """Build the header, then put the lines back through the loading door."""
    review = Review(row.department, row.opened_on, row.due_on)
    for line in row.lines:
        review.load(review_line_from_row(line))
    review.state = _state(row.state)
    review.closed_on = row.closed_on
    return review


def alike_to_rows(alike: KeyedAlikeSet) -> tuple[KeyedAlikeSetRow, list[KeyedAlikeMemberRow]]:
    header = KeyedAlikeSetRow(name=alike.name, note=alike.note)
    members = [
        KeyedAlikeMemberRow(set_name=alike.name, opening_number=number)
        for number in sorted(alike.openings)
    ]
    return header, members


def alike_from_row(row: KeyedAlikeSetRow) -> KeyedAlikeSet:
    return KeyedAlikeSet(row.name, row.openings, row.note)


def alike_register_from_rows(rows: Sequence[KeyedAlikeSetRow]) -> KeyedAlikeRegister:
    register = KeyedAlikeRegister()
    for row in sorted(rows, key=lambda item: item.name):
        register.add(alike_from_row(row))
    return register

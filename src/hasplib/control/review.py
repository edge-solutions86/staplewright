"""The annual review, where a department says who should still have a key.

Once a year every department is sent a list of the keys its people are
holding and asked to tick each one. A key nobody ticks is not proof of
anything either: it usually means the head of department was on leave. So
the useful output is three lists rather than one verdict. Confirmed, asked
back, and never answered, and the third is the one that gets chased.

The rule that earns its place is that a decision cannot be changed after the
review closes. A department that ticks a key in November and asks for it back
in December is raising a return, not amending a review, and conflating the
two loses the date somebody agreed the key was needed.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.control.issue import KeyLedger
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.lifecycle import require_state
from hasplib.store import Registry


class ReviewState(StrEnum):
    """Where a review cycle has got to."""

    OPEN = "open"
    CLOSED = "closed"
    ABANDONED = "abandoned"


class Answer(StrEnum):
    """What a department said about one key."""

    UNANSWERED = "unanswered"
    CONFIRMED = "confirmed"
    RETURN = "asked back"
    UNKNOWN = "department does not recognise it"


@dataclass(frozen=True)
class ReviewLine:
    """One key on one department's list."""

    tag: str
    keyholder: str
    symbol: str
    answer: Answer = Answer.UNANSWERED
    answered_on: date | None = None
    note: str = ""

    @property
    def is_answered(self) -> bool:
        return self.answer is not Answer.UNANSWERED

    @property
    def wants_return(self) -> bool:
        return self.answer in {Answer.RETURN, Answer.UNKNOWN}


class Review:
    """One department, one year, one list of keys."""

    def __init__(self, department: str, opened_on: date, due_on: date) -> None:
        if not department.strip():
            raise InputError("a review covers a department")
        if due_on < opened_on:
            raise ConflictError("a review is due after it opens", department=department)
        self.department = department
        self.opened_on = opened_on
        self.due_on = due_on
        self.state = ReviewState.OPEN
        self.closed_on: date | None = None
        self._lines: Registry[ReviewLine] = Registry("review line", "tag")

    def build_from(self, ledger: KeyLedger) -> int:
        """Put every key the department is holding on the list."""
        require_state(self.state, {ReviewState.OPEN}, "that review is closed")
        added = 0
        for person in ledger.directory.in_department(self.department):
            for key in ledger.held_by(person.identifier):
                if self._lines.has(key.tag):
                    continue
                self._lines.add(key.tag, ReviewLine(key.tag, key.keyholder, key.symbol))
                added += 1
        return added

    def line(self, tag: str) -> ReviewLine:
        return self._lines.get(tag)

    def answer(self, tag: str, answer: Answer, when: date, note: str = "") -> ReviewLine:
        """Record what the department said about one key."""
        require_state(self.state, {ReviewState.OPEN}, "that review is closed", tag=tag)
        line = self._lines.get(tag)
        if answer is Answer.UNANSWERED:
            raise InputError("an answer says something", tag=tag)
        if line.is_answered:
            raise ConflictError(
                "that key has already been answered", tag=tag, answer=str(line.answer)
            )
        return self._lines.put(tag, replace(line, answer=answer, answered_on=when, note=note))

    def close(self, when: date) -> None:
        require_state(self.state, {ReviewState.OPEN}, "that review is closed")
        self.state = ReviewState.CLOSED
        self.closed_on = when

    def abandon(self, when: date, note: str) -> None:
        """A reorganisation ate the department halfway through."""
        require_state(self.state, {ReviewState.OPEN}, "that review is closed")
        if not note.strip():
            raise InputError("an abandoned review says why", department=self.department)
        self.state = ReviewState.ABANDONED
        self.closed_on = when

    def lines(self) -> tuple[ReviewLine, ...]:
        return self._lines.by_key()

    def confirmed(self) -> tuple[ReviewLine, ...]:
        return tuple(line for line in self.lines() if line.answer is Answer.CONFIRMED)

    def to_return(self) -> tuple[ReviewLine, ...]:
        return tuple(line for line in self.lines() if line.wants_return)

    def unanswered(self) -> tuple[ReviewLine, ...]:
        return tuple(line for line in self.lines() if not line.is_answered)

    def is_complete(self) -> bool:
        return bool(self.lines()) and not self.unanswered()

    def overdue_on(self, today: date) -> bool:
        return self.state is ReviewState.OPEN and today > self.due_on

    def progress(self) -> float:
        total = len(self.lines())
        if not total:
            return 1.0
        return round((total - len(self.unanswered())) / total, 3)

    def summary(self) -> str:
        return (
            f"{self.department}: {len(self.confirmed())} confirmed, "
            f"{len(self.to_return())} back, {len(self.unanswered())} unanswered"
        )

    def __contains__(self, tag: object) -> bool:
        return tag in self._lines

    def __iter__(self) -> Iterator[ReviewLine]:
        return iter(self.lines())

    def __len__(self) -> int:
        return len(self._lines)


def chase_list(reviews: Sequence[Review], today: date) -> tuple[str, ...]:
    """Departments that are open, overdue and still owe answers."""
    found = [
        review.department for review in reviews if review.overdue_on(today) and review.unanswered()
    ]
    return tuple(sorted(found))


def keys_to_collect(reviews: Sequence[Review]) -> tuple[str, ...]:
    """Every tag a department asked to give back, across every review."""
    found = {line.tag for review in reviews for line in review.to_return()}
    return tuple(sorted(found))


def review_for(reviews: Sequence[Review], department: str) -> Review:
    for review in reviews:
        if review.department == department:
            return review
    raise NotFoundError("no review for that department", department=department)

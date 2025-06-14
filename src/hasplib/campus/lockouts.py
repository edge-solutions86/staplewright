"""Lockout calls, which are half the shop's telephone traffic.

Somebody is standing outside a door without a key. A technician goes, checks
they belong there, lets them in and writes it down. It sounds trivial and it
is not: the log is the only evidence the shop has when a department claims
its people never get locked out, and the after hours calls are the argument
for a card reader on that door.

Two rules do the work. Somebody who cannot be identified does not get let in,
which is why the log records how they were identified rather than whether.
And a door that generates lockouts every week is a door with the wrong
hardware on it, so the report that counts them by opening is the one that
gets taken to a budget meeting.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import datetime, time
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry

AFTER_HOURS_START = time(17, 0)
AFTER_HOURS_END = time(7, 0)


class Identification(StrEnum):
    """How the technician knew who they were letting in."""

    CAMPUS_CARD = "campus card"
    DEPARTMENT_STAFF = "vouched for by department"
    ROSTER = "on the room roster"
    NONE = "not identified"


class CallState(StrEnum):
    LOGGED = "logged"
    ATTENDED = "attended"
    OPENED = "opened"
    REFUSED = "refused"
    WITHDRAWN = "withdrawn"


@dataclass(frozen=True)
class Lockout:
    """One call, from the telephone ringing to the door opening."""

    number: str
    opening: str
    keyholder: str
    called_at: datetime
    state: CallState = CallState.LOGGED
    attended_at: datetime | None = None
    closed_at: datetime | None = None
    technician: str = ""
    identified_by: Identification = Identification.NONE
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("a lockout call needs a number")
        if self.attended_at is not None and self.attended_at < self.called_at:
            raise ConflictError("nobody attended before the call", number=self.number)

    @property
    def is_open(self) -> bool:
        return self.state in {CallState.LOGGED, CallState.ATTENDED}

    @property
    def after_hours(self) -> bool:
        struck = self.called_at.time()
        return struck >= AFTER_HOURS_START or struck < AFTER_HOURS_END

    def response_minutes(self) -> int | None:
        """How long the person stood there, or None if nobody has been."""
        if self.attended_at is None:
            return None
        return int((self.attended_at - self.called_at).total_seconds() // 60)

    def describe(self) -> str:
        when = self.called_at.isoformat(sep=" ", timespec="minutes")
        return f"{self.number} {self.opening} {self.keyholder} {when} {self.state.value}"


class LockoutLog:
    """Every call, open and closed."""

    def __init__(self) -> None:
        self._calls: Registry[Lockout] = Registry("lockout call", "number")

    def log(self, call: Lockout) -> Lockout:
        if call.state is not CallState.LOGGED:
            raise ConflictError("a call starts logged", number=call.number)
        return self._calls.add(call.number, call)

    def call(self, number: str) -> Lockout:
        return self._calls.get(number)

    def load(self, call: Lockout) -> Lockout:
        """Put a call back as it was stored, in whatever state it reached."""
        return self._calls.put(call.number, call)

    def attend(self, number: str, technician: str, when: datetime) -> Lockout:
        call = self._must_be(number, {CallState.LOGGED})
        if not technician.strip():
            raise InputError("somebody attends a call", number=number)
        if when < call.called_at:
            raise ConflictError("nobody attended before the call", number=number)
        updated = replace(call, state=CallState.ATTENDED, technician=technician, attended_at=when)
        return self._calls.put(number, updated)

    def open_door(self, number: str, how: Identification, when: datetime) -> Lockout:
        """Let them in, refusing to record an unidentified entry as opened."""
        call = self._must_be(number, {CallState.ATTENDED})
        if how is Identification.NONE:
            raise ConflictError("an unidentified person does not get let in", number=number)
        updated = replace(call, state=CallState.OPENED, identified_by=how, closed_at=when)
        return self._calls.put(number, updated)

    def refuse(self, number: str, when: datetime, note: str) -> Lockout:
        call = self._must_be(number, {CallState.ATTENDED})
        if not note.strip():
            raise InputError("a refusal says why", number=number)
        updated = replace(call, state=CallState.REFUSED, closed_at=when, note=note)
        return self._calls.put(number, updated)

    def withdraw(self, number: str, when: datetime, note: str = "") -> Lockout:
        """They found their key while the technician was on the way."""
        call = self._must_be(number, {CallState.LOGGED, CallState.ATTENDED})
        updated = replace(call, state=CallState.WITHDRAWN, closed_at=when, note=note)
        return self._calls.put(number, updated)

    def _must_be(self, number: str, states: set[CallState]) -> Lockout:
        call = self._calls.get(number)
        if call.state not in states:
            raise ConflictError(
                "the call is not in a state for that", number=number, state=str(call.state)
            )
        return call

    def open_calls(self) -> tuple[Lockout, ...]:
        return self._calls.where(lambda call: call.is_open)

    def for_opening(self, opening: str) -> tuple[Lockout, ...]:
        return self._calls.where(lambda call: call.opening == opening)

    def for_keyholder(self, keyholder: str) -> tuple[Lockout, ...]:
        return self._calls.where(lambda call: call.keyholder == keyholder)

    def after_hours(self) -> tuple[Lockout, ...]:
        return self._calls.where(lambda call: call.after_hours)

    def counts_by_opening(self) -> dict[str, int]:
        """Which doors generate the calls, worst first."""
        counts: dict[str, int] = {}
        for call in self._calls:
            counts[call.opening] = counts.get(call.opening, 0) + 1
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def repeat_callers(self, at_least: int = 3) -> tuple[str, ...]:
        counts: dict[str, int] = {}
        for call in self._calls:
            counts[call.keyholder] = counts.get(call.keyholder, 0) + 1
        return tuple(sorted(name for name, count in counts.items() if count >= at_least))

    def average_response(self) -> int | None:
        """Mean minutes to attend, across the calls anybody has attended."""
        minutes = [
            call.response_minutes() for call in self._calls if call.response_minutes() is not None
        ]
        if not minutes:
            return None
        return int(sum(m for m in minutes if m is not None) / len(minutes))

    def __contains__(self, number: object) -> bool:
        return number in self._calls

    def __iter__(self) -> Iterator[Lockout]:
        return iter(self._calls.values())

    def __len__(self) -> int:
        return len(self._calls)

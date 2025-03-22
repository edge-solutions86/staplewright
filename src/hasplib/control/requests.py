"""Key requests, and the signature that has to be on one before metal moves.

A request is a small state machine and the states are worth naming, because
the difference between a request that was denied and one that was withdrawn
matters to the department six months later. Raised, submitted, then approved
or denied, and a request that has been decided cannot be decided again.

Two rules do the real work. The signature has to reach: an authorizer signs
for one department and, usually, a list of buildings. And a master key needs
a signature that is allowed to approve masters, which most authorizers are
not. Everything else here is bookkeeping around those two.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.control.people import Directory
from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry


class RequestState(StrEnum):
    """Where a request has got to."""

    RAISED = "raised"
    SUBMITTED = "submitted"
    APPROVED = "approved"
    DENIED = "denied"
    WITHDRAWN = "withdrawn"
    FILLED = "filled"


OPEN_STATES = frozenset({RequestState.RAISED, RequestState.SUBMITTED})
DECIDED_STATES = frozenset({RequestState.APPROVED, RequestState.DENIED})


@dataclass(frozen=True)
class KeyRequest:
    """One person asking for one key."""

    number: str
    keyholder: str
    symbol: str
    building: str
    reason: str
    raised_on: date
    state: RequestState = RequestState.RAISED
    is_master: bool = False
    decided_on: date | None = None
    decided_by: str = ""
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("a request needs a number")
        if not self.reason.strip():
            raise InputError("a request needs a reason", number=self.number)

    @property
    def is_open(self) -> bool:
        return self.state in OPEN_STATES

    @property
    def is_decided(self) -> bool:
        return self.state in DECIDED_STATES

    def describe(self) -> str:
        return f"{self.number} {self.keyholder} wants {self.symbol} in {self.building}"


class RequestBook:
    """Every request, open and closed, in the order they were raised."""

    def __init__(self, directory: Directory) -> None:
        self.directory = directory
        self._requests: Registry[KeyRequest] = Registry("request", "number")

    def raise_request(self, request: KeyRequest) -> KeyRequest:
        if request.state is not RequestState.RAISED:
            raise ConflictError("a request starts raised", number=request.number)
        self.directory.person(request.keyholder)
        return self._requests.add(request.number, request)

    def request(self, number: str) -> KeyRequest:
        return self._requests.get(number)

    def load(self, request: KeyRequest) -> KeyRequest:
        """Put a request back as it was stored, in whatever state it reached.

        Used when the book comes off the database. Everything in it went
        through the front door once and there is no state machine left to
        walk, only a row to put back.
        """
        return self._requests.put(request.number, request)

    def submit(self, number: str) -> KeyRequest:
        """Send a request to whoever signs for that department."""
        request = self.request(number)
        if request.state is not RequestState.RAISED:
            raise ConflictError(
                "only a raised request can be submitted",
                number=number,
                state=str(request.state),
            )
        return self._store(replace(request, state=RequestState.SUBMITTED))

    def approve(self, number: str, signer: str, when: date, note: str = "") -> KeyRequest:
        """Sign a request off, refusing a signature that does not reach."""
        request = self._for_decision(number)
        person = self.directory.person(request.keyholder)
        if not person.may_hold_keys(when):
            raise ConflictError(
                "that keyholder may not hold keys",
                number=number,
                keyholder=request.keyholder,
                standing=str(person.standing),
            )
        if not self.directory.may_authorize(signer, person.department, request.building, when):
            raise ConflictError(
                "that signature does not reach this request",
                number=number,
                signer=signer,
                department=person.department,
                building=request.building,
            )
        if request.is_master and not self.directory.authorizer(signer).may_approve_masters:
            raise ConflictError(
                "that signature is not allowed to approve a master",
                number=number,
                signer=signer,
                symbol=request.symbol,
            )
        return self._store(
            replace(
                request,
                state=RequestState.APPROVED,
                decided_on=when,
                decided_by=signer,
                note=note,
            )
        )

    def deny(self, number: str, signer: str, when: date, note: str) -> KeyRequest:
        """Turn a request down. The note is required, not optional."""
        request = self._for_decision(number)
        if not note.strip():
            raise InputError("a denial says why", number=number)
        return self._store(
            replace(
                request,
                state=RequestState.DENIED,
                decided_on=when,
                decided_by=signer,
                note=note,
            )
        )

    def withdraw(self, number: str, when: date, note: str = "") -> KeyRequest:
        """The department changed its mind, which is not the same as a denial."""
        request = self.request(number)
        if not request.is_open:
            raise ConflictError(
                "only an open request can be withdrawn",
                number=number,
                state=str(request.state),
            )
        return self._store(
            replace(request, state=RequestState.WITHDRAWN, decided_on=when, note=note)
        )

    def mark_filled(self, number: str) -> KeyRequest:
        """The key went over the counter. Only an approved request can."""
        request = self.request(number)
        if request.state is not RequestState.APPROVED:
            raise ConflictError(
                "only an approved request gets filled",
                number=number,
                state=str(request.state),
            )
        return self._store(replace(request, state=RequestState.FILLED))

    def _for_decision(self, number: str) -> KeyRequest:
        request = self.request(number)
        if request.state is not RequestState.SUBMITTED:
            raise ConflictError(
                "only a submitted request can be decided",
                number=number,
                state=str(request.state),
            )
        return request

    def _store(self, request: KeyRequest) -> KeyRequest:
        return self._requests.put(request.number, request)

    def in_state(self, state: RequestState) -> tuple[KeyRequest, ...]:
        return self._requests.where(lambda request: request.state is state)

    def open_requests(self) -> tuple[KeyRequest, ...]:
        return self._requests.where(lambda request: request.is_open)

    def for_keyholder(self, identifier: str) -> tuple[KeyRequest, ...]:
        self.directory.person(identifier)
        return self._requests.where(lambda request: request.keyholder == identifier)

    def signed_by(self, signer: str) -> tuple[KeyRequest, ...]:
        return self._requests.where(lambda request: request.decided_by == signer)

    def waiting_on(self, department: str) -> tuple[KeyRequest, ...]:
        """Submitted requests a department still owes a decision on."""

        def theirs(request: KeyRequest) -> bool:
            if request.state is not RequestState.SUBMITTED:
                return False
            return self.directory.person(request.keyholder).department == department

        return self._requests.where(theirs)

    def __contains__(self, number: object) -> bool:
        return number in self._requests

    def __iter__(self) -> Iterator[KeyRequest]:
        return iter(self._requests.values())

    def __len__(self) -> int:
        return len(self._requests)

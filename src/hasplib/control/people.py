"""Keyholders, and the people allowed to say a keyholder should get a key.

Nobody holds a key. A person holds a key, and the difference matters the
morning that person stops working here. So the directory carries a status
and a separation date, and a keyholder who has separated cannot be issued
anything, which is the single most useful rule in this whole library.

Contractors are their own problem. They are legitimate keyholders with a
date the work ends, and after that date they are treated as separated whether
or not anybody remembered to close the record. The shop learned that one the
hard way in 2016 and the rule has been in the manual ever since.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry


class Affiliation(StrEnum):
    """What kind of person this is, which decides what they may hold."""

    FACULTY = "faculty"
    STAFF = "staff"
    STUDENT = "student"
    CONTRACTOR = "contractor"
    EMERITUS = "emeritus"


class Standing(StrEnum):
    """Where a keyholder sits with the shop today."""

    ACTIVE = "active"
    SEPARATED = "separated"
    SUSPENDED = "suspended"


TEMPORARY = frozenset({Affiliation.CONTRACTOR, Affiliation.STUDENT})
"""Affiliations that carry an end date and lapse on their own."""


@dataclass(frozen=True)
class Keyholder:
    """One person the shop will hand metal to."""

    identifier: str
    name: str
    affiliation: Affiliation
    department: str
    standing: Standing = Standing.ACTIVE
    ends_on: date | None = None
    separated_on: date | None = None

    def __post_init__(self) -> None:
        if not self.identifier.strip():
            raise InputError("a keyholder needs an identifier")
        if not self.name.strip():
            raise InputError("a keyholder needs a name", identifier=self.identifier)
        if self.affiliation in TEMPORARY and self.ends_on is None:
            raise ConflictError(
                "a temporary keyholder has a date the work ends",
                identifier=self.identifier,
                affiliation=str(self.affiliation),
            )
        if self.standing is Standing.SEPARATED and self.separated_on is None:
            raise ConflictError("a separated keyholder has a date", identifier=self.identifier)

    def lapsed_on(self, today: date) -> bool:
        """Whether a temporary record has run past its end date."""
        return self.ends_on is not None and today > self.ends_on

    def may_hold_keys(self, today: date) -> bool:
        if self.standing is not Standing.ACTIVE:
            return False
        return not self.lapsed_on(today)

    @property
    def label(self) -> str:
        return f"{self.name} ({self.identifier})"


@dataclass(frozen=True)
class Authorizer:
    """Somebody who may sign for keys, and how far their signature reaches."""

    identifier: str
    department: str
    buildings: tuple[str, ...] = ()
    may_approve_masters: bool = False

    def covers(self, department: str, building: str) -> bool:
        if department != self.department:
            return False
        return not self.buildings or building in self.buildings


class Directory:
    """Every keyholder and every authorizer, in one place."""

    def __init__(self) -> None:
        self._people: Registry[Keyholder] = Registry("keyholder", "identifier")
        self._authorizers: Registry[Authorizer] = Registry("authorizer", "identifier")

    def add(self, person: Keyholder) -> Keyholder:
        return self._people.add(person.identifier, person)

    def person(self, identifier: str) -> Keyholder:
        return self._people.get(identifier)

    def separate(self, identifier: str, when: date) -> Keyholder:
        """Mark somebody as gone. Their keys are somebody else's problem."""
        person = self.person(identifier)
        if person.standing is Standing.SEPARATED:
            raise ConflictError("that keyholder has already separated", identifier=identifier)
        updated = replace(person, standing=Standing.SEPARATED, separated_on=when)
        return self._people.put(identifier, updated)

    def suspend(self, identifier: str) -> Keyholder:
        person = self.person(identifier)
        if person.standing is Standing.SEPARATED:
            raise ConflictError("a separated keyholder cannot be suspended", identifier=identifier)
        updated = replace(person, standing=Standing.SUSPENDED)
        return self._people.put(identifier, updated)

    def reinstate(self, identifier: str) -> Keyholder:
        """Somebody came back, or the suspension was lifted."""
        person = self.person(identifier)
        updated = replace(person, standing=Standing.ACTIVE, separated_on=None)
        return self._people.put(identifier, updated)

    def extend(self, identifier: str, until: date) -> Keyholder:
        """Push a contract end date out. It cannot be pulled backward here."""
        person = self.person(identifier)
        if person.ends_on is None:
            raise ConflictError("that record has no end date to extend", identifier=identifier)
        if until <= person.ends_on:
            raise ConflictError(
                "an extension moves the date forward",
                identifier=identifier,
                ends_on=person.ends_on.isoformat(),
                asked=until.isoformat(),
            )
        updated = replace(person, ends_on=until)
        return self._people.put(identifier, updated)

    def add_authorizer(self, authorizer: Authorizer) -> Authorizer:
        self.person(authorizer.identifier)
        return self._authorizers.add(authorizer.identifier, authorizer)

    def authorizer(self, identifier: str) -> Authorizer:
        return self._authorizers.get(identifier)

    def may_authorize(self, identifier: str, department: str, building: str, today: date) -> bool:
        """Whether a signature counts today, for this department and building."""
        signer = self._authorizers.find(identifier)
        if signer is None or not self.person(identifier).may_hold_keys(today):
            return False
        return signer.covers(department, building)

    def in_department(self, department: str) -> tuple[Keyholder, ...]:
        found = self._people.where(lambda p: p.department == department)
        return tuple(sorted(found, key=lambda p: p.identifier))

    def active(self, today: date) -> tuple[Keyholder, ...]:
        found = self._people.where(lambda p: p.may_hold_keys(today))
        return tuple(sorted(found, key=lambda p: p.identifier))

    def lapsed(self, today: date) -> tuple[Keyholder, ...]:
        """Records that ran past their end date and nobody closed."""
        found = self._people.where(lambda p: p.standing is Standing.ACTIVE and p.lapsed_on(today))
        return tuple(sorted(found, key=lambda p: (p.ends_on or today, p.identifier)))

    def authorizers(self) -> tuple[Authorizer, ...]:
        return self._authorizers.by_key()

    def __contains__(self, identifier: object) -> bool:
        return identifier in self._people

    def __iter__(self) -> Iterator[Keyholder]:
        return iter(self._people.by_key())

    def __len__(self) -> int:
        return len(self._people)

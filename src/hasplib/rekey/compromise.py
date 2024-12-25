"""Working out how bad a missing key is before anybody argues about it.

The assessment is arithmetic and it is worth doing before the meeting. A key
is gone. It opens a list of doors. Some of those doors face outside, some of
them hold controlled substances or servers or the paint cage, and every one
of them is a cylinder somebody has to take apart if the decision is to rekey.

Severity is not a feeling. It comes off four numbers: how many openings, how
many buildings, whether any of them is an exterior door, and whether the key
sits above a change key in the hierarchy. A change key to one office is a
routine event. A building master taken out of a truck is not, and the shop
wants those two written up the same way so the difference is visible.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import IntEnum, StrEnum

from hasplib.campus.coverage import CoverageMap, keys_that_open
from hasplib.control.issue import KeyLedger
from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol


class Cause(StrEnum):
    """How the key left."""

    LOST = "lost"
    STOLEN = "stolen"
    UNRETURNED = "unreturned"
    DUPLICATED = "duplicated"
    UNACCOUNTED = "unaccounted"


class Severity(IntEnum):
    """How the shop grades an incident. Ordered on purpose."""

    ROUTINE = 1
    ELEVATED = 2
    SERIOUS = 3
    CRITICAL = 4

    @property
    def label(self) -> str:
        return self.name.lower()


@dataclass(frozen=True)
class Incident:
    """One missing key, written down the day it is reported."""

    number: str
    tag: str
    symbol: str
    cause: Cause
    reported_on: date
    keyholder: str = ""
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("an incident needs a number")
        if self.cause is Cause.STOLEN and not self.note.strip():
            raise InputError("a theft is written up", number=self.number)

    @property
    def key_symbol(self) -> KeySymbol:
        return KeySymbol.parse(self.symbol)


@dataclass(frozen=True)
class Assessment:
    """What one incident actually exposes."""

    incident: Incident
    openings: tuple[str, ...]
    buildings: tuple[str, ...]
    cylinders: tuple[str, ...]
    exterior_openings: tuple[str, ...]
    keys_to_recut: tuple[str, ...]
    holders_affected: tuple[str, ...]
    severity: Severity

    @property
    def opening_count(self) -> int:
        return len(self.openings)

    @property
    def touches_outside(self) -> bool:
        return bool(self.exterior_openings)

    @property
    def is_master(self) -> bool:
        return not self.incident.key_symbol.is_change_key

    def summary(self) -> str:
        return (
            f"{self.incident.number} {self.incident.symbol} {self.incident.cause.value}: "
            f"{self.opening_count} openings in {len(self.buildings)} buildings, "
            f"{self.severity.label}"
        )


def grade(
    openings: int,
    buildings: int,
    exterior: bool,
    is_master: bool,
    cause: Cause,
) -> Severity:
    """The four numbers, turned into a grade the shop can argue with.

    Written as a ladder rather than a score because a score invites people to
    tune the weights until they get the answer they wanted.
    """
    if is_master and buildings > 1:
        return Severity.CRITICAL
    if exterior and cause is Cause.STOLEN:
        return Severity.CRITICAL
    if is_master or openings > 20:
        return Severity.SERIOUS
    if exterior or openings > 5 or cause is Cause.STOLEN:
        return Severity.ELEVATED
    return Severity.ROUTINE


def assess(incident: Incident, coverage: CoverageMap, ledger: KeyLedger) -> Assessment:
    """Everything the incident touches, and how bad that makes it."""
    symbol = incident.key_symbol
    openings = coverage.openings_for(symbol)
    schedule = coverage.register.openings
    exterior = tuple(number for number in openings if schedule.opening(number).exterior)
    cylinders = tuple(c.serial for c in coverage.cylinders_for(symbol) if c.is_installed)
    still_out = keys_that_open(ledger, [symbol])
    to_recut = tuple(key.tag for key in still_out if key.tag != incident.tag)
    holders = tuple(sorted({key.keyholder for key in still_out if key.tag != incident.tag}))
    severity = grade(
        len(openings),
        len(coverage.buildings_for(symbol)),
        bool(exterior),
        not symbol.is_change_key,
        incident.cause,
    )
    return Assessment(
        incident=incident,
        openings=openings,
        buildings=coverage.buildings_for(symbol),
        cylinders=cylinders,
        exterior_openings=exterior,
        keys_to_recut=to_recut,
        holders_affected=holders,
        severity=severity,
    )


def worst_first(assessments: tuple[Assessment, ...]) -> tuple[Assessment, ...]:
    """Order a pile of incidents the way the morning meeting wants them."""
    return tuple(
        sorted(
            assessments,
            key=lambda item: (-item.severity, -item.opening_count, item.incident.number),
        )
    )

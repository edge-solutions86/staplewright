"""The numbers the shop takes to a budget meeting once a year.

None of these are hard. What makes them worth having in one place is that
they are the numbers somebody will ask for on a Tuesday afternoon with an
hour's notice, and working them out from scratch each time is how a figure
ends up different in two documents in the same folder.

Every one of them is a plain count over objects that are already in memory.
There is no clever aggregation and no cache: a campus is a few thousand keys
and a few hundred doors, and anything that needs more than that has bigger
problems than arithmetic.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.campus.coverage import CoverageMap
from hasplib.campus.lockouts import LockoutLog
from hasplib.campus.openings import OpeningSchedule
from hasplib.control.deposits import DepositBook, dollars
from hasplib.control.issue import KeyLedger
from hasplib.keys.symbol import KeySymbol


@dataclass(frozen=True)
class CampusNumbers:
    """One year of the shop, in the figures that get asked for."""

    buildings: int
    openings: int
    keyed_openings: int
    cylinders_installed: int
    cylinders_shelved: int
    keys_out: int
    keys_gone: int
    deposits_held_cents: int

    @property
    def uncovered(self) -> int:
        return self.keyed_openings - self.cylinders_installed

    def lines(self) -> tuple[str, ...]:
        return (
            f"{self.buildings} buildings, {self.openings} openings, "
            f"{self.keyed_openings} of them keyed",
            f"{self.cylinders_installed} cylinders in doors, {self.cylinders_shelved} on the shelf",
            f"{self.keys_out} keys out, {self.keys_gone} written off",
            f"deposits held {dollars(self.deposits_held_cents)}",
        )


def campus_numbers(
    openings: OpeningSchedule,
    coverage: CoverageMap,
    ledger: KeyLedger,
    book: DepositBook | None = None,
) -> CampusNumbers:
    """Count the campus once, so every report quotes the same figures."""
    register = coverage.register
    installed = [cylinder for cylinder in register if cylinder.is_installed]
    return CampusNumbers(
        buildings=len(openings.buildings()),
        openings=len(openings),
        keyed_openings=len(openings.keyed_openings()),
        cylinders_installed=len(installed),
        cylinders_shelved=len(register) - len(installed),
        keys_out=len(ledger.out()),
        keys_gone=len(ledger.gone()),
        deposits_held_cents=book.total_held() if book else ledger.deposits_held(),
    )


def keys_by_building(coverage: CoverageMap, ledger: KeyLedger) -> dict[str, int]:
    """How many keys out reach each building, biggest first.

    A key that reaches four buildings is counted in all four, because the
    question this answers is how exposed a building is rather than how many
    pieces of brass exist.
    """
    counts: dict[str, int] = {}
    for key in ledger.out():
        for building in coverage.buildings_for(KeySymbol.parse(key.symbol)):
            counts[building] = counts.get(building, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))


def keys_by_department(ledger: KeyLedger) -> dict[str, int]:
    counts: dict[str, int] = {}
    for key in ledger.out():
        department = ledger.directory.person(key.keyholder).department
        counts[department] = counts.get(department, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))


def incidents_by_year(reported: Sequence[date]) -> dict[int, int]:
    counts: dict[int, int] = {}
    for when in reported:
        counts[when.year] = counts.get(when.year, 0) + 1
    return dict(sorted(counts.items()))


def lockout_pressure(log: LockoutLog, openings: OpeningSchedule) -> tuple[tuple[str, int], ...]:
    """Doors generating calls, with the room number for the meeting."""
    counts = log.counts_by_opening()
    found: list[tuple[str, int]] = []
    for number, count in counts.items():
        try:
            label = openings.opening(number).label
        except Exception:
            label = number
        found.append((label, count))
    return tuple(found)


def exposure(
    coverage: CoverageMap, ledger: KeyLedger, limit: int = 5
) -> tuple[tuple[str, int], ...]:
    """The keys that are out and open the most doors, worst first."""
    found: list[tuple[str, int]] = []
    seen: set[str] = set()
    for key in ledger.out():
        if key.symbol in seen:
            continue
        seen.add(key.symbol)
        found.append((key.symbol, coverage.reach_of(KeySymbol.parse(key.symbol))))
    found.sort(key=lambda item: (-item[1], item[0]))
    return tuple(found[:limit])


def summary(numbers: CampusNumbers, title: str = "") -> tuple[str, ...]:
    lines = list(numbers.lines())
    if numbers.uncovered:
        lines.append(f"{numbers.uncovered} keyed openings with nothing in them")
    return (title, *lines) if title else tuple(lines)

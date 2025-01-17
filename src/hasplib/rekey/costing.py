"""What a rekey costs, in minutes and in cents, before anybody approves it.

The estimate is the argument. A department that has lost a master wants to
hear that it is one cylinder and a new key, and the only way to have that
conversation is to put the real number in front of them: ninety cylinders,
three hundred minutes of bench time, four hundred keys to cut, and a bill.

Interchangeable cores are the interesting line. A core is pinned at the bench
and swapped in seconds, so the labour at the door collapses and the labour in
the shop goes up. A mortise cylinder is the other way round: the shop takes
its time at the door with the room occupied and somebody watching.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.campus.coverage import CoverageMap
from hasplib.campus.hardware import CylinderFormat
from hasplib.errors import InputError
from hasplib.rekey.scope import ScopeDecision


@dataclass(frozen=True)
class RateCard:
    """What the shop charges and how long things take."""

    bench_minutes_per_cylinder: int = 12
    door_minutes_per_cylinder: int = 20
    core_door_minutes: int = 2
    key_cut_minutes: int = 3
    travel_minutes_per_building: int = 25
    labour_cents_per_hour: int = 5200
    blank_cents: int = 180
    pin_cents: int = 12
    pins_per_cylinder: int = 12

    def __post_init__(self) -> None:
        fields = [
            self.bench_minutes_per_cylinder,
            self.door_minutes_per_cylinder,
            self.core_door_minutes,
            self.key_cut_minutes,
            self.travel_minutes_per_building,
            self.labour_cents_per_hour,
            self.blank_cents,
            self.pin_cents,
            self.pins_per_cylinder,
        ]
        if any(value < 0 for value in fields):
            raise InputError("a rate card holds no negative numbers")
        if self.labour_cents_per_hour == 0:
            raise InputError("labour is not free on this campus")


DEFAULT_RATES = RateCard()


@dataclass(frozen=True)
class Estimate:
    """The number that goes on the bottom of the memo."""

    cylinders: int
    cores: int
    keys: int
    buildings: int
    bench_minutes: int
    door_minutes: int
    travel_minutes: int
    labour_cents: int
    parts_cents: int

    @property
    def total_minutes(self) -> int:
        return self.bench_minutes + self.door_minutes + self.travel_minutes

    @property
    def total_cents(self) -> int:
        return self.labour_cents + self.parts_cents

    @property
    def hours(self) -> float:
        return round(self.total_minutes / 60, 2)

    def summary(self) -> str:
        return (
            f"{self.cylinders} cylinders and {self.keys} keys, "
            f"{self.total_minutes} minutes, {self.total_cents} cents"
        )


def estimate(
    decision: ScopeDecision,
    coverage: CoverageMap,
    keys_to_cut: Sequence[str],
    rates: RateCard = DEFAULT_RATES,
) -> Estimate:
    """Cost one rekey decision, split by where the time is spent."""
    cylinders = [coverage.register.cylinder(serial) for serial in decision.cylinders]
    cores = [c for c in cylinders if c.format is CylinderFormat.SFIC]
    conventional = [c for c in cylinders if c.format is not CylinderFormat.SFIC]
    buildings = {
        coverage.register.openings.opening(c.installed_at).building
        for c in cylinders
        if c.installed_at
    }

    bench = rates.bench_minutes_per_cylinder * len(cylinders)
    bench += rates.key_cut_minutes * len(keys_to_cut)
    door = rates.door_minutes_per_cylinder * len(conventional)
    door += rates.core_door_minutes * len(cores)
    travel = rates.travel_minutes_per_building * len(buildings)

    minutes = bench + door + travel
    labour = round(minutes * rates.labour_cents_per_hour / 60)
    parts = rates.blank_cents * len(keys_to_cut)
    parts += rates.pin_cents * rates.pins_per_cylinder * len(cylinders)

    return Estimate(
        cylinders=len(cylinders),
        cores=len(cores),
        keys=len(keys_to_cut),
        buildings=len(buildings),
        bench_minutes=bench,
        door_minutes=door,
        travel_minutes=travel,
        labour_cents=labour,
        parts_cents=parts,
    )


def compare(first: Estimate, second: Estimate) -> int:
    """How many cents the second option saves against the first."""
    return first.total_cents - second.total_cents


def cheaper_of(options: Sequence[tuple[str, Estimate]]) -> str:
    """Name the cheapest option, breaking a tie on the fewest minutes."""
    if not options:
        raise InputError("nothing to compare")
    best = min(options, key=lambda item: (item[1].total_cents, item[1].total_minutes, item[0]))
    return best[0]

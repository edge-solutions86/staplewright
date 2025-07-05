"""Where along the blade a cut sits, and which end you measured from.

Most charts space the cuts from the shoulder: the flat step where the blade
meets the bow, which the machine jaw sits against. Some space them from the
tip instead, and a handful of families changed from one to the other between
catalogue editions.

That matters because the two only agree when the blank is exactly the length
the chart assumes. A blank a hundredth long, which happens, moves every tip
gauged cut a hundredth along the blade and leaves every shoulder gauged cut
where it was. So a shop working from the wrong end on a long blank cuts a key
that enters, turns nothing, and looks perfect against the chart.

The conversion is one subtraction. Getting the subtraction the wrong way
round is the mistake, and it is invisible until somebody stands at a door.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import BittingError, InputError
from hasplib.spec.chart import DepthAndSpaceChart


@dataclass(frozen=True)
class Blade:
    """One blank, measured, because they are not all the length they say."""

    part_number: str
    length_mils: int
    nominal_mils: int

    def __post_init__(self) -> None:
        if self.length_mils <= 0 or self.nominal_mils <= 0:
            raise InputError("a blade has a length", part=self.part_number)

    @property
    def error_mils(self) -> int:
        """How far off nominal this blank is. Positive means long."""
        return self.length_mils - self.nominal_mils

    @property
    def is_nominal(self) -> bool:
        return self.error_mils == 0


def shoulder_positions(chart: DepthAndSpaceChart) -> tuple[int, ...]:
    """Where the cuts sit measured from the shoulder, which is the chart."""
    return chart.spaces()


def tip_positions(chart: DepthAndSpaceChart, blade: Blade) -> tuple[int, ...]:
    """The same cuts measured back from the tip of this blank.

    Chamber one is nearest the shoulder, so from the tip it is the furthest
    away, and the list comes back in chamber order rather than reversed.
    """
    return tuple(blade.length_mils - space for space in shoulder_positions(chart))


def from_tip_to_shoulder(blade: Blade, positions: Sequence[int]) -> tuple[int, ...]:
    """Turn tip gauged positions into shoulder gauged ones for this blank."""
    return tuple(blade.length_mils - position for position in positions)


def positions_for(chart: DepthAndSpaceChart, blade: Blade) -> tuple[int, ...]:
    """Where the machine goes, given which end this family gauges from."""
    if not chart.gauged_from_tip:
        return shoulder_positions(chart)
    return from_tip_to_shoulder(blade, tip_positions(chart, blade))


def drift_on(chart: DepthAndSpaceChart, blade: Blade) -> int:
    """How far a tip gauged cut moves on a blank that is off nominal.

    Zero for a shoulder gauged family however wrong the blank is, which is
    the whole reason shops prefer shoulder gauging.
    """
    if not chart.gauged_from_tip:
        return 0
    return blade.error_mils


def within_tolerance(chart: DepthAndSpaceChart, blade: Blade, allowance_mils: int = 4) -> bool:
    """Whether this blank can be cut on this chart without moving the cuts."""
    if allowance_mils < 0:
        raise InputError("an allowance is not negative", allowance=allowance_mils)
    return abs(drift_on(chart, blade)) <= allowance_mils


def blade_long_enough(chart: DepthAndSpaceChart, blade: Blade, clearance_mils: int = 60) -> bool:
    """Whether the last chamber leaves metal between it and the tip."""
    last = shoulder_positions(chart)[-1]
    return blade.length_mils - last >= clearance_mils


def check_blade(
    chart: DepthAndSpaceChart, blade: Blade, allowance_mils: int = 4
) -> tuple[str, ...]:
    """Every reason this blank is wrong for this chart, in reading order."""
    found: list[str] = []
    if not blade_long_enough(chart, blade):
        found.append(
            f"{blade.part_number} is too short for {chart.family}, "
            f"the tip cut sits at {shoulder_positions(chart)[-1]}"
        )
    if not within_tolerance(chart, blade, allowance_mils):
        found.append(
            f"{blade.part_number} is {blade.error_mils} off nominal and "
            f"{chart.family} gauges from the tip"
        )
    return tuple(found)


def position_of(chart: DepthAndSpaceChart, chamber: int, blade: Blade) -> int:
    """Where one chamber sits on this blank, from whichever end applies."""
    if chamber < 1 or chamber > chart.chambers:
        raise BittingError(
            "no such chamber in this family", chamber=chamber, chambers=chart.chambers
        )
    return positions_for(chart, blade)[chamber - 1]

"""Building the column of brass one chamber needs.

Given the depths a chamber has to break at, the arithmetic is short. The
bottom pin fills from the plug up to the shallowest cut. Each further break
adds a wafer as thick as the distance between that cut and the one before it.
A driver goes on top and brings the column up to the height where the shell
takes over, and the spring sits above all of it.

Two things go wrong and both of them are physical. A wafer thinner than about
a thirty thousandth of an inch tips over in the chamber and jams the plug, so
one step splits are refused and systems get progressed two steps at a time. A
column that already reaches the shear line before the driver goes on has no
room for a driver at all, which happens when a chamber is asked to break at
too many depths at once.

A column is a column whether the cylinder has one shear line or two, so the
stacking arithmetic lives here once and the core code inherits it.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import PinningError
from hasplib.pinning.segments import DriverProfile, PinSegment, SegmentKind
from hasplib.spec.chart import DepthAndSpaceChart


@dataclass(frozen=True)
class PinningSpec:
    """How a chart's depths turn into lengths of brass.

    ``bottom_at_shallowest_mils`` is the bottom pin that sits under the
    shallowest cut on the chart. ``column_mils`` is the height from the floor
    of the plug chamber to the shear line, which every stack has to reach
    exactly.
    """

    chart: DepthAndSpaceChart
    bottom_at_shallowest_mils: int
    column_mils: int
    min_wafer_mils: int = 30
    min_driver_mils: int = 90

    def __post_init__(self) -> None:
        if self.bottom_at_shallowest_mils <= 0:
            raise PinningError("a bottom pin has a length")
        if self.column_mils <= self.bottom_at_shallowest_mils:
            raise PinningError(
                "the column is shorter than the shallowest bottom pin",
                column=self.column_mils,
                bottom=self.bottom_at_shallowest_mils,
            )
        if self.min_wafer_mils < 1:
            raise PinningError("a wafer has a thickness")

    def bottom_pin(self, depth: int) -> int:
        """The pin that sits against the key at one cut depth."""
        if not self.chart.allows_depth(depth):
            raise PinningError("that depth is not on the chart", depth=depth)
        steps = depth - self.chart.shallowest
        return self.bottom_at_shallowest_mils + self.chart.increment_mils * steps

    def wafer(self, shallower: int, deeper: int) -> int:
        """The wafer between two breaks in one chamber."""
        if deeper <= shallower:
            raise PinningError(
                "a wafer sits between two different depths",
                shallower=shallower,
                deeper=deeper,
            )
        return self.chart.increment_mils * (deeper - shallower)

    def wafers_between(self, depths: Sequence[int]) -> tuple[int, ...]:
        """Every wafer a run of breaks needs, shallow to deep.

        Refuses any wafer the shop will not stand up in a chamber, which is
        the check that stops a one step split reaching the bench.
        """
        wafers: list[int] = []
        for shallower, deeper in zip(depths, depths[1:], strict=False):
            thickness = self.wafer(shallower, deeper)
            if thickness < self.min_wafer_mils:
                raise PinningError(
                    "that wafer is too thin to stand up in the chamber",
                    thickness=thickness,
                    thinnest=self.min_wafer_mils,
                )
            wafers.append(thickness)
        return tuple(wafers)


@dataclass(frozen=True)
class PinStack:
    """One chamber, from the plug floor up.

    The wafers are in order from the bottom pin toward the driver, and the
    heights they break at are just the running total.
    """

    chamber: int
    bottom_mils: int
    wafers_mils: tuple[int, ...]
    driver_mils: int
    driver_profile: DriverProfile = DriverProfile.STANDARD

    @property
    def height_mils(self) -> int:
        return self.bottom_mils + sum(self.wafers_mils) + self.driver_mils

    @property
    def piece_count(self) -> int:
        return 2 + len(self.wafers_mils)

    @property
    def break_heights(self) -> tuple[int, ...]:
        """Where the plug turns, measured from the floor of the chamber."""
        heights: list[int] = [self.bottom_mils]
        for wafer in self.wafers_mils:
            heights.append(heights[-1] + wafer)
        return tuple(heights)

    def segments(self) -> tuple[PinSegment, ...]:
        """The parts to draw out of the kit for this chamber."""
        parts = [PinSegment(SegmentKind.BOTTOM, self.bottom_mils)]
        parts.extend(PinSegment(SegmentKind.WAFER, wafer) for wafer in self.wafers_mils)
        parts.append(PinSegment(SegmentKind.DRIVER, self.driver_mils, self.driver_profile))
        return tuple(parts)


def build_stack(
    spec: PinningSpec,
    chamber: int,
    depths: Sequence[int],
    driver_profile: DriverProfile = DriverProfile.STANDARD,
) -> PinStack:
    """The column for one chamber, given every depth it has to break at."""
    if chamber < 1 or chamber > spec.chart.chambers:
        raise PinningError(
            "no such chamber in this family",
            chamber=chamber,
            chambers=spec.chart.chambers,
        )
    ordered = sorted(set(depths))
    if not ordered:
        raise PinningError("a chamber breaks somewhere", chamber=chamber)
    bottom = spec.bottom_pin(ordered[0])
    try:
        wafers = spec.wafers_between(ordered)
    except PinningError as thin:
        raise PinningError(thin.message, chamber=chamber, **thin.facts) from None
    driver = spec.column_mils - bottom - sum(wafers)
    if driver < spec.min_driver_mils:
        raise PinningError(
            "the stack reaches the shear line with no room for a driver",
            chamber=chamber,
            driver=driver,
            shortest=spec.min_driver_mils,
        )
    return PinStack(chamber, bottom, wafers, driver, driver_profile)

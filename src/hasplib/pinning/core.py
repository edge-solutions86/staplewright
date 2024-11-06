"""Interchangeable cores, which have two shear lines instead of one.

A small format core is the thing the dorms run on. It drops into a housing
and is held there by a lug, and the lug is retracted by a second key that
lifts every stack past the operating shear line to a control shear line
sitting a little higher. Pull the core, drop in a differently pinned one, and
the room is rekeyed in about four seconds without a screwdriver.

The arithmetic that makes that work is the only awkward part of pinning. A
break aligns with a shear line when the stack under it plus the lift the key
gives equals the height of that line, and a shallower cut lifts more. So the
control break in a chamber sits exactly the control gap above where an
operating break at the same cut would sit, and the control cut is squeezed
from both sides. Too shallow and its break lands on top of the deepest
operating break with no room for a wafer between them. Too deep and the break
climbs so high there is nothing left of the chamber to put a driver in.

A core stack is an ordinary stack whose last wafer is the control wafer, so
the stacking arithmetic comes from the stack module and only the two shear
lines are new here.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import PinningError
from hasplib.keys.bitting import Bitting
from hasplib.pinning.segments import DriverProfile, PinSegment
from hasplib.pinning.stack import PinningSpec, PinStack


@dataclass(frozen=True)
class CoreSpec:
    """A pinning spec plus the second shear line."""

    pinning: PinningSpec
    control_gap_mils: int

    def __post_init__(self) -> None:
        if self.control_gap_mils <= 0:
            raise PinningError("the control line sits above the operating line")
        if self.control_gap_mils < self.pinning.min_wafer_mils:
            raise PinningError(
                "the control gap is thinner than the thinnest wafer",
                gap=self.control_gap_mils,
                thinnest=self.pinning.min_wafer_mils,
            )

    @property
    def chambers(self) -> int:
        return self.pinning.chart.chambers

    def operating_break(self, depth: int) -> int:
        """Where a break has to sit for an operating key cut this deep."""
        return self.pinning.bottom_pin(depth)

    def control_break(self, depth: int) -> int:
        """Where a break has to sit for the control key cut this deep."""
        return self.pinning.bottom_pin(depth) + self.control_gap_mils


@dataclass(frozen=True)
class CoreStack(PinStack):
    """One chamber of a core. The last wafer is the control wafer."""

    @property
    def control_wafer_mils(self) -> int:
        return self.wafers_mils[-1]

    @property
    def operating_breaks(self) -> tuple[int, ...]:
        return self.break_heights[:-1]

    @property
    def control_break(self) -> int:
        return self.break_heights[-1]

    def render(self) -> str:
        pieces = [str(self.bottom_mils)]
        pieces.extend(str(wafer) for wafer in self.wafers_mils[:-1])
        pieces.append(f"[{self.control_wafer_mils}]")
        pieces.append(str(self.driver_mils))
        return " ".join(pieces)


def build_core_stack(
    spec: CoreSpec,
    chamber: int,
    operating_depths: Sequence[int],
    control_depth: int,
    driver_profile: DriverProfile = DriverProfile.STANDARD,
) -> CoreStack:
    """One chamber of a core, given the keys and the control cut."""
    if chamber < 1 or chamber > spec.chambers:
        raise PinningError("no such chamber in this family", chamber=chamber)
    ordered = sorted(set(operating_depths))
    if not ordered:
        raise PinningError("a chamber breaks somewhere", chamber=chamber)
    pinning = spec.pinning
    bottom = pinning.bottom_pin(ordered[0])
    try:
        wafers = pinning.wafers_between(ordered)
    except PinningError as thin:
        raise PinningError(thin.message, chamber=chamber, **thin.facts) from None
    control = spec.control_break(control_depth)
    control_wafer = control - bottom - sum(wafers)
    if control_wafer < pinning.min_wafer_mils:
        raise PinningError(
            "the control cut is too shallow to clear the operating breaks",
            chamber=chamber,
            control_depth=control_depth,
            deepest_operating=ordered[-1],
            wafer=control_wafer,
        )
    driver = pinning.column_mils - control
    if driver < pinning.min_driver_mils:
        raise PinningError(
            "the control break leaves no room for a driver",
            chamber=chamber,
            driver=driver,
            shortest=pinning.min_driver_mils,
        )
    return CoreStack(chamber, bottom, (*wafers, control_wafer), driver, driver_profile)


@dataclass(frozen=True)
class CorePinning:
    """A whole core, with the control key it answers to."""

    stacks: tuple[CoreStack, ...]
    keys: tuple[Bitting, ...]
    control_key: Bitting

    @property
    def chambers(self) -> int:
        return len(self.stacks)

    @property
    def piece_count(self) -> int:
        return sum(stack.piece_count for stack in self.stacks)

    def stack(self, chamber: int) -> CoreStack:
        for stack in self.stacks:
            if stack.chamber == chamber:
                return stack
        raise PinningError("this core has no such chamber", chamber=chamber)

    def parts(self) -> tuple[PinSegment, ...]:
        return tuple(part for stack in self.stacks for part in stack.segments())

    def chart_lines(self) -> tuple[str, ...]:
        return tuple(f"{stack.chamber}  {stack.render()}" for stack in self.stacks)


def pin_core(
    spec: CoreSpec,
    keys: Sequence[Bitting],
    control_key: Bitting,
    driver_profile: DriverProfile = DriverProfile.STANDARD,
) -> CorePinning:
    """Pin a core for its operating keys and one control key."""
    if not keys:
        raise PinningError("a core is pinned for at least one key")
    for key in [*keys, control_key]:
        if key.chambers != spec.chambers:
            raise PinningError(
                "a key handed to this core is the wrong length",
                chambers=spec.chambers,
                got=key.chambers,
            )
    stacks = tuple(
        build_core_stack(
            spec,
            chamber,
            sorted({key.at(chamber) for key in keys}),
            control_key.at(chamber),
            driver_profile,
        )
        for chamber in range(1, spec.chambers + 1)
    )
    return CorePinning(stacks, tuple(keys), control_key)


def control_cut_range(spec: CoreSpec, operating_depths: Sequence[int]) -> tuple[int, ...]:
    """Every control cut that works in a chamber holding these operating cuts.

    This is the check that catches a control key borrowed off the wrong
    system. It is squeezed from below by the wafer that has to fit above the
    deepest operating break, and from above by the driver that still has to
    reach the top of the chamber.
    """
    ordered = sorted(set(operating_depths))
    if not ordered:
        raise PinningError("a chamber breaks somewhere")
    pinning = spec.pinning
    top_operating = pinning.bottom_pin(ordered[0]) + sum(pinning.wafers_between(ordered))
    workable: list[int] = []
    for depth in pinning.chart.depths:
        control = spec.control_break(depth)
        if control - top_operating < pinning.min_wafer_mils:
            continue
        if pinning.column_mils - control < pinning.min_driver_mils:
            continue
        workable.append(depth)
    return tuple(workable)

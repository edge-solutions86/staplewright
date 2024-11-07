"""A whole cylinder, which is the chambers plus the reason they were built.

The bench works off a pinning chart: one row a chamber, bottom pin first,
then wafers, then the driver. This builds that chart from the keys the
cylinder is meant to take, and it keeps hold of the keys, because six months
later the only interesting question about a cylinder is what is supposed to
open it.

Security drivers are decided per cylinder rather than per chamber. The shop
either spools a cylinder or it does not, and mixing profiles inside one
cylinder is the kind of thing that reads as clever and comes back as a
service call.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import PinningError
from hasplib.keys.bitting import Bitting
from hasplib.pinning.segments import DriverProfile, PinKit, PinSegment
from hasplib.pinning.stack import PinningSpec, PinStack, build_stack
from hasplib.progression.incidental import incidental_count, operating_count


@dataclass(frozen=True)
class CylinderPinning:
    """The chart for one cylinder and the keys it was built for."""

    stacks: tuple[PinStack, ...]
    keys: tuple[Bitting, ...]
    driver_profile: DriverProfile = DriverProfile.STANDARD

    @property
    def chambers(self) -> int:
        return len(self.stacks)

    def stack(self, chamber: int) -> PinStack:
        for stack in self.stacks:
            if stack.chamber == chamber:
                return stack
        raise PinningError("this cylinder has no such chamber", chamber=chamber)

    @property
    def wafer_count(self) -> int:
        """Every master wafer in the cylinder, which is what it cost."""
        return sum(len(stack.wafers_mils) for stack in self.stacks)

    @property
    def piece_count(self) -> int:
        return sum(stack.piece_count for stack in self.stacks)

    @property
    def mastered_chambers(self) -> tuple[int, ...]:
        return tuple(stack.chamber for stack in self.stacks if stack.wafers_mils)

    def parts(self) -> tuple[PinSegment, ...]:
        return tuple(part for stack in self.stacks for part in stack.segments())

    def operates(self, bitting: Bitting) -> bool:
        """Whether a key turns this plug, drawn or not."""
        if bitting.chambers != self.chambers:
            return False
        for stack in self.stacks:
            depths = self.break_depths(stack.chamber)
            if bitting.at(stack.chamber) not in depths:
                return False
        return True

    def break_depths(self, chamber: int) -> tuple[int, ...]:
        """The cut depths one chamber accepts, read back off the keys."""
        self.stack(chamber)
        return tuple(sorted({key.at(chamber) for key in self.keys}))

    @property
    def operating_key_count(self) -> int:
        return operating_count(self.keys)

    @property
    def incidental_key_count(self) -> int:
        return incidental_count(self.keys)

    def can_be_built_from(self, kit: PinKit) -> bool:
        return not kit.shortfall(self.parts())


def pin_cylinder(
    spec: PinningSpec,
    keys: Sequence[Bitting],
    driver_profile: DriverProfile = DriverProfile.STANDARD,
) -> CylinderPinning:
    """Work out every chamber for a cylinder that has to take these keys."""
    if not keys:
        raise PinningError("a cylinder is pinned for at least one key")
    chambers = spec.chart.chambers
    for key in keys:
        if key.chambers != chambers:
            raise PinningError(
                "a key handed to this cylinder is the wrong length",
                chambers=chambers,
                got=key.chambers,
            )
    stacks = tuple(
        build_stack(
            spec,
            chamber,
            sorted({key.at(chamber) for key in keys}),
            driver_profile,
        )
        for chamber in range(1, chambers + 1)
    )
    return CylinderPinning(stacks, tuple(keys), driver_profile)


def build_from_kit(
    spec: PinningSpec,
    kit: PinKit,
    keys: Sequence[Bitting],
    driver_profile: DriverProfile = DriverProfile.STANDARD,
) -> CylinderPinning:
    """Pin a cylinder and take the brass out of the cabinet to do it."""
    pinning = pin_cylinder(spec, keys, driver_profile)
    kit.draw(pinning.parts())
    return pinning


def strip_to_kit(kit: PinKit, pinning: CylinderPinning) -> None:
    """A cylinder coming apart puts its parts back."""
    kit.put_back(pinning.parts())

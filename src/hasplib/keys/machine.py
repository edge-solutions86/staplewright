"""Cutting a key by code, and proving afterwards that it came out right.

Two machines in the shop and they are not interchangeable. The duplicator
traces an original, which is fast and inherits every bit of wear the original
has. The code machine cuts to numbers off a card, which is slower and is the
only thing that produces a key identical to what the schedule says.

Everything the code machine needs comes off the chart. The carriage goes to
the space for a chamber, the cutter comes down to the dimension for a depth,
and the operator does that once per cut. The two things that go wrong are a
carriage out of calibration, which walks every cut along the blade by the
same amount, and a cutter worn small, which makes every cut shallow. Both are
constant offsets, which is why measuring one key catches them.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import BittingError, InputError
from hasplib.keys.bitting import Bitting
from hasplib.spec.chart import DepthAndSpaceChart


@dataclass(frozen=True)
class Calibration:
    """What this machine is doing wrong today, in thousandths."""

    space_offset_mils: int = 0
    depth_offset_mils: int = 0

    @property
    def is_true(self) -> bool:
        return self.space_offset_mils == 0 and self.depth_offset_mils == 0


TRUE = Calibration()


@dataclass(frozen=True)
class CutInstruction:
    """One line the operator works through, in order from the shoulder."""

    chamber: int
    depth: int
    space_mils: int
    depth_mils: int

    def render(self) -> str:
        return f"{self.chamber}  depth {self.depth}  at {self.space_mils}  to {self.depth_mils}"


@dataclass(frozen=True)
class CutCard:
    """The whole setup for one key."""

    family: str
    bitting: Bitting
    instructions: tuple[CutInstruction, ...]
    calibration: Calibration = TRUE

    @property
    def cuts(self) -> int:
        return len(self.instructions)

    def lines(self) -> tuple[str, ...]:
        header = f"{self.family}  {self.bitting.render()}"
        return (header, *(item.render() for item in self.instructions))


def cut_card(
    chart: DepthAndSpaceChart, bitting: Bitting, calibration: Calibration = TRUE
) -> CutCard:
    """Everything the code machine needs to cut one key."""
    chart.validate(bitting)
    instructions = tuple(
        CutInstruction(
            chamber=chamber,
            depth=bitting.at(chamber),
            space_mils=chart.space_for(chamber) + calibration.space_offset_mils,
            depth_mils=chart.dimension_for(bitting.at(chamber)) + calibration.depth_offset_mils,
        )
        for chamber in range(1, chart.chambers + 1)
    )
    return CutCard(chart.family, bitting, instructions, calibration)


def measure(chart: DepthAndSpaceChart, dimensions: Sequence[int]) -> Bitting:
    """Read a cut key back into a bitting, refusing anything off the chart."""
    if len(dimensions) != chart.chambers:
        raise InputError(
            "that is not a key from this family",
            chambers=chart.chambers,
            got=len(dimensions),
        )
    return Bitting(chart.depth_for_dimension(mils) for mils in dimensions)


def verify(
    chart: DepthAndSpaceChart, wanted: Bitting, dimensions: Sequence[int]
) -> tuple[int, ...]:
    """Which chambers came out wrong, counted from one.

    Anything not near a published depth raises, because that is a broken key
    rather than a wrong one and the operator needs to hear it differently.
    """
    cut = measure(chart, dimensions)
    return wanted.differs_from(cut)


def drift(chart: DepthAndSpaceChart, wanted: Bitting, dimensions: Sequence[int]) -> int | None:
    """The constant depth error across a key, if there is one.

    A machine out of calibration is wrong by the same amount everywhere. One
    chamber wrong and the rest right is an operator error, not a machine, and
    this returns nothing rather than pretending otherwise.
    """
    if len(dimensions) != chart.chambers:
        raise InputError("that is not a key from this family", chambers=chart.chambers)
    errors = {
        mils - chart.dimension_for(wanted.at(chamber))
        for chamber, mils in enumerate(dimensions, start=1)
    }
    if len(errors) != 1:
        return None
    only = errors.pop()
    return only or None


def duplicate_wear(original: Sequence[int], copy: Sequence[int]) -> int:
    """How much shallower a traced copy came out than what it was traced from.

    A duplicator follows the original, so a worn original gives a shallow
    copy, and the copy of that copy is shallower again. Three generations in
    and the key will not turn a tight cylinder.
    """
    if len(original) != len(copy):
        raise InputError("those are not the same key", original=len(original), copy=len(copy))
    if not original:
        raise InputError("nothing to compare")
    return max(mine - theirs for mine, theirs in zip(copy, original, strict=True))


def within_tolerance(chart: DepthAndSpaceChart, dimensions: Sequence[int]) -> bool:
    """Whether every cut on a key is near enough to a published depth."""
    try:
        measure(chart, dimensions)
    except (BittingError, InputError):
        return False
    return True

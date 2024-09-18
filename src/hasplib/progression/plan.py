"""The progression plan, which decides how big a system can ever get.

Pick a master. Pick the chambers that are allowed to move. In each of those,
the depths a key under the master may use are the chart depths that sit a
whole number of steps away from the master cut. Everything else in the key
stays where the master put it, and those chambers are the constants.

Multiply the counts together and that is the capacity of the system, before
macs throws some of them away. The number is usually smaller than people
expect, which is the entire reason this file exists. A six pin system on a
ten depth chart progressed two steps in four chambers holds a couple of
hundred keys, not thousands, and a department that wants six hundred offices
on one master is going to hear the word no.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from math import prod

from hasplib.errors import ProgressionError
from hasplib.keys.bitting import Bitting
from hasplib.spec.chart import DepthAndSpaceChart


def available_depths(chart: DepthAndSpaceChart, master_depth: int, step: int) -> tuple[int, ...]:
    """Depths a key under this master may use in one chamber.

    The master cut itself is not in the list. A chamber cut at the master
    depth is a maintained chamber and needs no wafer, and letting it turn up
    here would quietly produce a key identical to the master.
    """
    if step < 1:
        raise ProgressionError("a progression step is at least one", step=step)
    if not chart.allows_depth(master_depth):
        raise ProgressionError(
            "the master is cut to a depth this chart does not publish",
            depth=master_depth,
            family=chart.family,
        )
    return tuple(
        depth
        for depth in chart.depths
        if depth != master_depth and abs(depth - master_depth) % step == 0
    )


@dataclass(frozen=True)
class ProgressionPlan:
    """One master and the room underneath it.

    ``progressed`` names the chambers that move, counted from one. Anything
    not named is a constant and stays at the master cut.
    """

    chart: DepthAndSpaceChart
    master: Bitting
    progressed: tuple[int, ...]
    step: int = 2

    def __post_init__(self) -> None:
        if self.master.chambers != self.chart.chambers:
            raise ProgressionError(
                "the master is not the length of the family",
                chambers=self.chart.chambers,
                got=self.master.chambers,
            )
        if not self.progressed:
            raise ProgressionError("a plan with no moving chambers holds one key")
        if len(set(self.progressed)) != len(self.progressed):
            raise ProgressionError("a chamber is named twice", progressed=self.progressed)
        for chamber in self.progressed:
            if chamber < 1 or chamber > self.chart.chambers:
                raise ProgressionError(
                    "no such chamber in this family",
                    chamber=chamber,
                    chambers=self.chart.chambers,
                )
        for chamber in self.progressed:
            if not self.depths_in(chamber):
                raise ProgressionError(
                    "that chamber has nowhere to go at this step",
                    chamber=chamber,
                    master=self.master.at(chamber),
                    step=self.step,
                )

    @property
    def constants(self) -> tuple[int, ...]:
        moving = set(self.progressed)
        return tuple(c for c in range(1, self.chart.chambers + 1) if c not in moving)

    def depths_in(self, chamber: int) -> tuple[int, ...]:
        """Every depth a key under the master may carry in one chamber."""
        if chamber in self.progressed:
            return available_depths(self.chart, self.master.at(chamber), self.step)
        return (self.master.at(chamber),)

    @property
    def capacity(self) -> int:
        """How many distinct keys the plan holds, before macs is applied."""
        return prod(len(self.depths_in(chamber)) for chamber in self.progressed)

    def order(self) -> tuple[int, ...]:
        """Which chamber turns over fastest. The rightmost one does."""
        return tuple(sorted(self.progressed, reverse=True))

    def combinations(self) -> Iterator[Bitting]:
        """Every key the plan holds, in the order the array prints them.

        The leftmost progressed chamber changes slowest, which is what makes
        a printed array readable, and it means blocks of consecutive keys
        share their early cuts.
        """
        moving = tuple(sorted(self.progressed))
        cuts = list(self.master.depths)

        def walk(index: int) -> Iterator[Bitting]:
            if index == len(moving):
                yield Bitting(cuts)
                return
            chamber = moving[index]
            for depth in self.depths_in(chamber):
                cuts[chamber - 1] = depth
                yield from walk(index + 1)
            cuts[chamber - 1] = self.master.at(chamber)

        return walk(0)

    def holds(self, bitting: Bitting) -> bool:
        """Whether a key could have come out of this plan."""
        if bitting.chambers != self.chart.chambers:
            return False
        for chamber in range(1, self.chart.chambers + 1):
            if bitting.at(chamber) not in self.depths_in(chamber):
                return False
        return True

    def describe(self) -> str:
        moving = ",".join(str(c) for c in sorted(self.progressed))
        return (
            f"master {self.master.render()}, chambers {moving} progressed "
            f"{self.step} steps, holds {self.capacity}"
        )


def plan_from_pattern(
    chart: DepthAndSpaceChart, master: Bitting, pattern: Sequence[int | None], step: int = 2
) -> ProgressionPlan:
    """Build a plan from a schedule pattern, where an x is a moving chamber."""
    if len(pattern) != chart.chambers:
        raise ProgressionError(
            "the pattern is not the length of the family",
            chambers=chart.chambers,
            got=len(pattern),
        )
    progressed = tuple(chamber for chamber, depth in enumerate(pattern, start=1) if depth is None)
    return ProgressionPlan(chart, master, progressed, step)

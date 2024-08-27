"""The depth and space chart, which is the only thing that makes a cut real.

A chart says three things. Which depth numbers exist at all, how far down the
blade each one cuts, and how far along the blade each chamber sits. Everything
downstream is arithmetic on those three.

Dimensions are held in mils, meaning thousandths of an inch, as whole numbers.
The shop measures in thousandths and a float here would eventually print a
depth as 0.30500000000000004 on a chart somebody has to read at a bench.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from hasplib.errors import BittingError, SpecificationError
from hasplib.keys.bitting import Bitting


@dataclass(frozen=True)
class DepthAndSpaceChart:
    """One manufacturer's cutting chart for one family of cylinders.

    ``depths`` is the set of depth numbers the chart publishes, in order. It
    is not always a plain range: two of the charts the shop uses skip a depth
    that the factory withdrew after a run of broken keys.

    ``root_mils`` is the blade dimension at the shallowest published depth,
    and each further depth step removes ``increment_mils`` more metal. A key
    is measured from the shoulder unless ``gauged_from_tip`` says otherwise.
    """

    manufacturer: str
    family: str
    chambers: int
    depths: tuple[int, ...]
    root_mils: int
    increment_mils: int
    first_space_mils: int
    space_increment_mils: int
    macs: int
    gauged_from_tip: bool = False
    depth_tolerance_mils: int = 2
    notes: str = field(default="", compare=False)

    def __post_init__(self) -> None:
        if self.chambers < 1:
            raise SpecificationError("a chart needs at least one chamber", family=self.family)
        if not self.depths:
            raise SpecificationError("a chart with no depths cuts nothing", family=self.family)
        if list(self.depths) != sorted(set(self.depths)):
            raise SpecificationError(
                "chart depths are listed once each and in order",
                family=self.family,
                depths=self.depths,
            )
        if self.increment_mils <= 0:
            raise SpecificationError("depths get deeper, not shallower", family=self.family)
        if self.space_increment_mils <= 0:
            raise SpecificationError("chambers are spaced apart", family=self.family)
        if self.macs < 1:
            raise SpecificationError("a macs of zero forbids every key", family=self.family)
        if self.root_mils - self.increment_mils * max(self.depths) <= 0:
            raise SpecificationError(
                "the deepest depth on this chart cuts through the blade",
                family=self.family,
                root_mils=self.root_mils,
                deepest=max(self.depths),
            )

    @property
    def shallowest(self) -> int:
        return self.depths[0]

    @property
    def deepest(self) -> int:
        return self.depths[-1]

    @property
    def depth_count(self) -> int:
        return len(self.depths)

    def allows_depth(self, depth: int) -> bool:
        return depth in self.depths

    def dimension_for(self, depth: int) -> int:
        """How far down the blade a cut of this depth reaches, in mils."""
        if depth not in self.depths:
            raise BittingError(
                "the chart does not publish that depth",
                depth=depth,
                family=self.family,
                depths=self.depths,
            )
        return self.root_mils - self.increment_mils * (depth - self.shallowest)

    def depth_for_dimension(self, mils: int) -> int:
        """Read a measured key back into a depth number.

        Anything further than the chart tolerance from a published depth is a
        worn key or a bad cut, and the shop wants to hear about it rather than
        get a plausible answer.
        """
        best: int | None = None
        best_gap = self.depth_tolerance_mils + 1
        for depth in self.depths:
            gap = abs(self.dimension_for(depth) - mils)
            if gap < best_gap:
                best, best_gap = depth, gap
        if best is None:
            raise BittingError(
                "that measurement is not near any depth on the chart",
                mils=mils,
                family=self.family,
                tolerance=self.depth_tolerance_mils,
            )
        return best

    def space_for(self, chamber: int) -> int:
        """How far along the blade a chamber sits, in mils from the gauge."""
        if chamber < 1 or chamber > self.chambers:
            raise BittingError(
                "no such chamber in this family",
                chamber=chamber,
                chambers=self.chambers,
                family=self.family,
            )
        return self.first_space_mils + self.space_increment_mils * (chamber - 1)

    def spaces(self) -> tuple[int, ...]:
        return tuple(self.space_for(chamber) for chamber in range(1, self.chambers + 1))

    def validate(self, bitting: Bitting) -> None:
        """Refuse a key the chart cannot cut. Says nothing about masters."""
        if bitting.chambers != self.chambers:
            raise BittingError(
                "the key is not as long as the family is",
                family=self.family,
                chambers=self.chambers,
                got=bitting.chambers,
            )
        for chamber, depth in enumerate(bitting.depths, start=1):
            if depth not in self.depths:
                raise BittingError(
                    "the chart does not publish that depth",
                    chamber=chamber,
                    depth=depth,
                    family=self.family,
                )

    def is_cuttable(self, bitting: Bitting) -> bool:
        try:
            self.validate(bitting)
        except BittingError:
            return False
        return True

    def describe(self) -> str:
        return (
            f"{self.manufacturer} {self.family}, {self.chambers} chambers, "
            f"depths {self.shallowest} to {self.deepest}, macs {self.macs}"
        )

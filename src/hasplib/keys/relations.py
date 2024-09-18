"""What two keys in one system are to each other, chamber by chamber.

Everything about mastering falls out of one comparison. Where a master and a
key under it are cut the same, the cylinder needs one pin. Where they differ,
the cylinder needs a split, which is a master wafer sitting between the bottom
pin and the top pin so the plug turns at two different heights.

So the count of chambers where two keys differ is the count of extra wafers
the cylinder carries, and that number is the cost of the whole design. A
master that differs from its change keys in every chamber is expensive, loose
and full of keys nobody meant to cut. One that differs in a single chamber is
cheap and gives the system almost nowhere to grow.

The step between the two cuts matters as much as the count. A one step split
leaves a wafer under twenty thousandths of an inch, which the shop will not
put in a cylinder that gets used, so systems are progressed two steps at a
time and the wafer comes out at twice the increment.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting


@dataclass(frozen=True)
class ChamberSplit:
    """One chamber where two keys disagree, and by how much."""

    chamber: int
    above: int
    below: int

    @property
    def step(self) -> int:
        return abs(self.above - self.below)

    @property
    def below_is_deeper(self) -> bool:
        return self.below > self.above


@dataclass(frozen=True)
class MasteringReport:
    """How one key sits under another."""

    splits: tuple[ChamberSplit, ...]
    chambers: int

    @property
    def split_count(self) -> int:
        return len(self.splits)

    @property
    def maintained_chambers(self) -> tuple[int, ...]:
        """Chambers cut the same on both keys, which need no wafer."""
        split_at = {split.chamber for split in self.splits}
        return tuple(c for c in range(1, self.chambers + 1) if c not in split_at)

    @property
    def split_chambers(self) -> tuple[int, ...]:
        return tuple(split.chamber for split in self.splits)

    def step_in(self, chamber: int) -> int:
        for split in self.splits:
            if split.chamber == chamber:
                return split.step
        return 0

    @property
    def is_identical(self) -> bool:
        return not self.splits


def compare(above: Bitting, below: Bitting) -> MasteringReport:
    """Line two keys up and report every chamber they disagree in."""
    if above.chambers != below.chambers:
        raise InputError(
            "two keys of different lengths are not in one system",
            above=above.chambers,
            below=below.chambers,
        )
    splits = tuple(
        ChamberSplit(chamber, above.at(chamber), below.at(chamber))
        for chamber in range(1, above.chambers + 1)
        if above.at(chamber) != below.at(chamber)
    )
    return MasteringReport(splits, above.chambers)


def wafer_count(above: Bitting, below: Bitting) -> int:
    """How many extra wafers a cylinder needs to take both keys."""
    return compare(above, below).split_count


def wafers_for(master: Bitting, changes: Sequence[Bitting]) -> dict[int, tuple[int, ...]]:
    """Every depth each chamber has to break at, master included.

    A cylinder that takes a master and four change keys breaks in a chamber
    at every depth any of those keys cuts there. The answer is keyed by
    chamber and each list is sorted shallow to deep, which is the order the
    stack gets built in.
    """
    depths: dict[int, set[int]] = {
        chamber: {master.at(chamber)} for chamber in range(1, master.chambers + 1)
    }
    for change in changes:
        if change.chambers != master.chambers:
            raise InputError(
                "a change key is not the same length as its master",
                master=master.chambers,
                change=change.chambers,
            )
        for chamber in range(1, master.chambers + 1):
            depths[chamber].add(change.at(chamber))
    return {chamber: tuple(sorted(found)) for chamber, found in depths.items()}


def steps_are_multiples_of(above: Bitting, below: Bitting, step: int) -> bool:
    """Whether every split between the two keys moves in whole steps."""
    if step < 1:
        raise InputError("a progression step is at least one", step=step)
    return all(split.step % step == 0 for split in compare(above, below).splits)


def shallowest_split(above: Bitting, below: Bitting) -> int | None:
    """The tightest split between the two keys, which is the thin wafer."""
    splits = compare(above, below).splits
    if not splits:
        return None
    return min(split.step for split in splits)


def keys_are_distinct(keys: Sequence[Bitting]) -> bool:
    return len({key.depths for key in keys}) == len(keys)


def duplicate_bittings(keys: Sequence[Bitting]) -> tuple[Bitting, ...]:
    """Any bitting that turns up more than once, in the order first seen."""
    seen: dict[tuple[int, ...], int] = {}
    for key in keys:
        seen[key.depths] = seen.get(key.depths, 0) + 1
    return tuple(Bitting(depths) for depths, count in seen.items() if count > 1)

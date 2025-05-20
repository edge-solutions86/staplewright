"""The two ways a shop hands out blocks of an array, and what each costs.

The block method is the obvious one. Cut the array into runs and give one run
to each master. It is easy to read, easy to extend, and it wastes whatever is
left at the end of a run when a department turns out to need six keys rather
than ten.

The rotating constant method holds one chamber still per master instead. Every
key under master AA carries the same cut in the constant chamber, which means
a key can be read back to its master by looking at one position, and it means
the masters do not compete for the same block of the array. It costs a chamber:
that one cannot be progressed for change keys any more, so the system gets
narrower as it gets easier to read.

Neither is right. The block method suits a campus that grows by buildings and
the rotating constant suits one that grows by departments, and the shop has
used both in the same decade.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import CapacityError, ProgressionError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.array import KeyBittingArray


@dataclass(frozen=True)
class Allocation:
    """One master and the change keys it was given."""

    symbol: KeySymbol
    keys: tuple[Bitting, ...]
    constant_chamber: int | None = None
    constant_depth: int | None = None

    @property
    def size(self) -> int:
        return len(self.keys)

    def describe(self) -> str:
        if self.constant_chamber is None:
            return f"{self.symbol.render()}: {self.size} keys"
        return (
            f"{self.symbol.render()}: {self.size} keys, "
            f"chamber {self.constant_chamber} held at {self.constant_depth}"
        )


def by_blocks(
    array: KeyBittingArray, symbols: Sequence[KeySymbol], size: int
) -> tuple[Allocation, ...]:
    """Cut the array into runs and hand one to each master, in order."""
    blocks = array.blocks(size)
    if len(symbols) > len(blocks):
        raise CapacityError(
            "not enough blocks for the masters that want one",
            wanted=len(symbols),
            blocks=len(blocks),
        )
    if len(set(symbols)) != len(symbols):
        raise ProgressionError("a symbol was handed a block twice")
    return tuple(
        Allocation(symbol, block)
        for symbol, block in zip(symbols, blocks[: len(symbols)], strict=True)
    )


def constant_depths(array: KeyBittingArray, chamber: int) -> tuple[int, ...]:
    """Every depth the array actually uses in one chamber, shallow first."""
    plan = array.plan
    if chamber < 1 or chamber > plan.chart.chambers:
        raise ProgressionError("no such chamber in this family", chamber=chamber)
    return tuple(sorted({key.at(chamber) for key in array}))


def by_rotating_constant(
    array: KeyBittingArray, symbols: Sequence[KeySymbol], chamber: int
) -> tuple[Allocation, ...]:
    """Hold one chamber still per master, one depth each.

    A master gets every key in the array that carries its depth in the
    constant chamber, so the allocations partition the array exactly and a
    key can be read back to its master from one cut.
    """
    if len(set(symbols)) != len(symbols):
        raise ProgressionError("a symbol was handed a depth twice")
    depths = constant_depths(array, chamber)
    if len(symbols) > len(depths):
        raise CapacityError(
            "the constant chamber does not hold that many masters",
            wanted=len(symbols),
            depths=len(depths),
            chamber=chamber,
        )
    found: list[Allocation] = []
    for symbol, depth in zip(symbols, depths, strict=False):
        keys = tuple(key for key in array if key.at(chamber) == depth)
        found.append(Allocation(symbol, keys, chamber, depth))
    return tuple(found)


def master_of(allocations: Sequence[Allocation], key: Bitting) -> KeySymbol | None:
    """Which master a key belongs to, read off the allocations."""
    for allocation in allocations:
        if key in allocation.keys:
            return allocation.symbol
    return None


def unallocated(array: KeyBittingArray, allocations: Sequence[Allocation]) -> tuple[Bitting, ...]:
    """Keys in the array nobody was given, which is the waste."""
    handed = {key.depths for allocation in allocations for key in allocation.keys}
    return tuple(key for key in array if key.depths not in handed)


def waste(array: KeyBittingArray, allocations: Sequence[Allocation]) -> int:
    return len(unallocated(array, allocations))


def is_partition(array: KeyBittingArray, allocations: Sequence[Allocation]) -> bool:
    """Whether the allocations cover the array exactly once each."""
    handed = [key.depths for allocation in allocations for key in allocation.keys]
    return len(handed) == len(set(handed)) == array.usable_count

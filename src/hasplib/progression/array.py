"""The key bitting array, which is the plan with the impossible keys taken out.

A plan says how many keys exist. The array says which ones the shop would
actually cut, because macs and policy delete a slice of every plan and the
slice is never small. Six pins progressed two steps in three chambers looks
like sixty four keys on paper and comes back as fifty something once the
milling limit has had its say.

The array also carries the block structure. Change keys are handed out in
runs rather than one at a time, so that a department that grows gets the next
key in its own block instead of one borrowed from the far side of the system.
Blocks come off the array in printed order, which means the keys in one block
share their early cuts and differ only near the tip.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence

from hasplib.errors import CapacityError, ProgressionError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.plan import ProgressionPlan
from hasplib.spec.rules import DEFAULT_RULES, CuttingRules, check_cuts


class KeyBittingArray:
    """Every key one plan holds, sorted into what is cuttable and what is not.

    Both lists are worked out once and kept, because a six pin plan with four
    moving chambers is a few hundred keys and the reports ask for them over
    and over.
    """

    def __init__(self, plan: ProgressionPlan, rules: CuttingRules = DEFAULT_RULES) -> None:
        self.plan = plan
        self.rules = rules
        usable: list[Bitting] = []
        rejected: list[tuple[Bitting, tuple[str, ...]]] = []
        for candidate in plan.combinations():
            violations = check_cuts(plan.chart, candidate, rules)
            if violations:
                rejected.append((candidate, tuple(v.rule for v in violations)))
            else:
                usable.append(candidate)
        self._usable = tuple(usable)
        self._rejected = tuple(rejected)

    @property
    def capacity(self) -> int:
        """What the plan promised before any rule was applied."""
        return self.plan.capacity

    @property
    def usable(self) -> tuple[Bitting, ...]:
        return self._usable

    @property
    def rejected(self) -> tuple[tuple[Bitting, tuple[str, ...]], ...]:
        return self._rejected

    @property
    def usable_count(self) -> int:
        return len(self._usable)

    @property
    def loss(self) -> int:
        return len(self._rejected)

    def rejection_reasons(self) -> dict[str, int]:
        """How many keys each rule cost, worst first when printed."""
        counts: dict[str, int] = {}
        for _, reasons in self._rejected:
            for reason in set(reasons):
                counts[reason] = counts.get(reason, 0) + 1
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def blocks(self, size: int) -> tuple[tuple[Bitting, ...], ...]:
        """Cut the usable keys into runs of the given size.

        The last block is short if the array does not divide evenly. It is
        still a block, because throwing away nine keys to keep the arithmetic
        tidy is not something the shop will agree to.
        """
        if size < 1:
            raise ProgressionError("a block holds at least one key", size=size)
        return tuple(
            self._usable[start : start + size] for start in range(0, len(self._usable), size)
        )

    def block_count(self, size: int) -> int:
        return len(self.blocks(size))

    def block_holding(self, bitting: Bitting, size: int) -> int | None:
        """Which block a key sits in, counted from one, or None if it is out."""
        for index, block in enumerate(self.blocks(size), start=1):
            if bitting in block:
                return index
        return None

    def take(self, count: int, skip: int = 0) -> tuple[Bitting, ...]:
        """The next run of keys off the array, refusing to overdraw."""
        if count < 0 or skip < 0:
            raise ProgressionError("cannot take a negative number of keys", count=count, skip=skip)
        available = self._usable[skip:]
        if len(available) < count:
            raise CapacityError(
                "the array does not hold that many more keys",
                wanted=count,
                left=len(available),
                capacity=self.capacity,
            )
        return available[:count]

    def holds(self, bitting: Bitting) -> bool:
        return bitting in self._usable

    def __iter__(self) -> Iterator[Bitting]:
        return iter(self._usable)

    def __len__(self) -> int:
        return len(self._usable)

    def describe(self) -> str:
        return (
            f"{self.usable_count} of {self.capacity} cuttable, {self.loss} thrown out by the rules"
        )


def assign_blocks(
    array: KeyBittingArray, symbols: Sequence[KeySymbol], size: int
) -> dict[KeySymbol, tuple[Bitting, ...]]:
    """Hand one block to each symbol, in the order the symbols were given.

    Refuses rather than wrapping around. A system that cannot seat every
    department it was designed for is a design problem, not something to fix
    by quietly giving two of them the same keys.
    """
    blocks = array.blocks(size)
    if len(symbols) > len(blocks):
        raise CapacityError(
            "not enough blocks for the masters that want one",
            wanted=len(symbols),
            blocks=len(blocks),
            size=size,
        )
    if len(set(symbols)) != len(symbols):
        raise ProgressionError("a symbol was handed a block twice")
    return dict(zip(symbols, blocks[: len(symbols)], strict=True))

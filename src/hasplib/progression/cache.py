"""Keeping arrays that have already been built, because they get rebuilt a lot.

An array is a few hundred keys and every one of them goes through the whole
rule set. That is cheap once. It stops being cheap when the widening report
builds six of them to compare, the capacity page builds another, and a web
request does all of that again forty seconds later because somebody refreshed.

So a plan and a policy identify an array, and an array that has been built is
kept. The cache is bounded and dumb: oldest out first, no timing, no
weighting, and a counter of hits and misses so the shop can tell whether it
is earning its keep. Anything cleverer would need evidence this one does not
have yet.
"""

from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass

from hasplib.errors import InputError
from hasplib.progression.array import KeyBittingArray
from hasplib.progression.plan import ProgressionPlan
from hasplib.spec.rules import DEFAULT_RULES, CuttingRules

Key = tuple[str, str, tuple[int, ...], int, int, int, int]


def cache_key(plan: ProgressionPlan, rules: CuttingRules) -> Key:
    """Everything that changes the answer, and nothing that does not.

    The chart is identified by manufacturer and family rather than by the
    whole object, because two charts with the same name are the same chart
    and one of them being a different instance is not interesting.
    """
    return (
        f"{plan.chart.manufacturer}/{plan.chart.family}",
        plan.master.render(),
        tuple(sorted(plan.progressed)),
        plan.step,
        rules.max_run,
        rules.min_distinct,
        -1 if rules.shallow_tip_limit is None else rules.shallow_tip_limit,
    )


@dataclass
class CacheStats:
    """How the cache is doing, for the health page."""

    hits: int = 0
    misses: int = 0
    evictions: int = 0

    @property
    def lookups(self) -> int:
        return self.hits + self.misses

    @property
    def hit_rate(self) -> float:
        if not self.lookups:
            return 0.0
        return round(self.hits / self.lookups, 3)

    def describe(self) -> str:
        return (
            f"{self.hits} hits, {self.misses} misses, {self.evictions} evicted, "
            f"rate {self.hit_rate}"
        )


class ArrayCache:
    """Built arrays, oldest out first when it is full."""

    def __init__(self, size: int = 32) -> None:
        if size < 1:
            raise InputError("a cache of nothing is not a cache", size=size)
        self.size = size
        self._held: OrderedDict[Key, KeyBittingArray] = OrderedDict()
        self.stats = CacheStats()

    def get(self, plan: ProgressionPlan, rules: CuttingRules = DEFAULT_RULES) -> KeyBittingArray:
        """The array for this plan, built if nobody has built it yet."""
        key = cache_key(plan, rules)
        held = self._held.get(key)
        if held is not None:
            self._held.move_to_end(key)
            self.stats.hits += 1
            return held
        self.stats.misses += 1
        built = KeyBittingArray(plan, rules)
        self._held[key] = built
        self._evict()
        return built

    def holds(self, plan: ProgressionPlan, rules: CuttingRules = DEFAULT_RULES) -> bool:
        return cache_key(plan, rules) in self._held

    def _evict(self) -> None:
        while len(self._held) > self.size:
            self._held.popitem(last=False)
            self.stats.evictions += 1

    def clear(self) -> None:
        """Empty it. A chart changing is the only reason to, and it is rare."""
        self._held.clear()

    def keys(self) -> tuple[Key, ...]:
        return tuple(self._held)

    def __len__(self) -> int:
        return len(self._held)


_shared = ArrayCache()


def shared_cache() -> ArrayCache:
    """One cache for the process, which is what the reports use."""
    return _shared


def array_for(plan: ProgressionPlan, rules: CuttingRules = DEFAULT_RULES) -> KeyBittingArray:
    return _shared.get(plan, rules)

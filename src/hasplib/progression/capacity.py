"""How much room a system has left, and what widening it would cost.

Every keying system runs out. The question is whether it runs out in four
years or in forty, and the shop only finds out by asking, because nobody
notices an array filling up until the afternoon there is no key left for a
new office.

The forecast is deliberately crude. A department that took eleven keys last
year will take about eleven again, and any model more sophisticated than that
is pretending to know something. What the arithmetic is for is the difference
between four years and forty, not the difference between four and five.

Widening is where the interesting trade sits. Progressing another chamber
multiplies the capacity and costs a wafer in every cylinder. Dropping to a
one step progression doubles it and produces wafers too thin to stand up.
Loosening macs is not a decision the shop gets to make at all, because it is
the manufacturer's number. So the options come back with what each one costs,
and somebody who knows the buildings picks one.
"""

from __future__ import annotations

from dataclasses import dataclass, replace

from hasplib.errors import InputError
from hasplib.progression.array import KeyBittingArray
from hasplib.progression.plan import ProgressionPlan
from hasplib.spec.rules import CuttingRules


@dataclass(frozen=True)
class Headroom:
    """What is left in an array, and how long that lasts."""

    capacity: int
    cuttable: int
    used: int
    reserve_wanted: int

    @property
    def free(self) -> int:
        return max(self.cuttable - self.used, 0)

    @property
    def reserve_left(self) -> int:
        return self.free - self.reserve_wanted

    @property
    def under_reserve(self) -> bool:
        return self.reserve_left < 0

    @property
    def used_fraction(self) -> float:
        if not self.cuttable:
            return 1.0
        return round(self.used / self.cuttable, 3)

    def describe(self) -> str:
        return (
            f"{self.used} of {self.cuttable} cut, {self.free} free, reserve {self.reserve_wanted}"
        )


def headroom(array: KeyBittingArray, used: int, reserve_fraction: float = 0.2) -> Headroom:
    """What is left, against the reserve the shop keeps for surprises."""
    if used < 0:
        raise InputError("a count of cut keys is not negative", used=used)
    if not 0 <= reserve_fraction < 1:
        raise InputError("a reserve is a fraction under one", reserve=reserve_fraction)
    wanted = int(array.usable_count * reserve_fraction)
    return Headroom(array.capacity, array.usable_count, used, wanted)


def years_left(room: Headroom, keys_per_year: int) -> int | None:
    """How long the free keys last at the rate they are being cut.

    None when nothing is being cut, because a system nobody is growing does
    not run out and saying otherwise invites a rebuild nobody needs.
    """
    if keys_per_year < 0:
        raise InputError("a growth rate is not negative", rate=keys_per_year)
    if keys_per_year == 0:
        return None
    return max((room.free - room.reserve_wanted) // keys_per_year, 0)


def forecast(used: int, keys_per_year: int, years: int) -> int:
    """How many keys will be cut by then, at this year's rate."""
    if years < 0:
        raise InputError("a forecast runs forward", years=years)
    return used + keys_per_year * years


@dataclass(frozen=True)
class WideningOption:
    """One way to make a system bigger, and what it costs."""

    name: str
    capacity: int
    wafers_added: int
    thinnest_split: int
    note: str = ""

    def gain_over(self, current: int) -> int:
        return self.capacity - current


def widening_options(
    plan: ProgressionPlan, rules: CuttingRules | None = None
) -> tuple[WideningOption, ...]:
    """Every way to widen this plan, with what each one costs.

    A chamber that is already progressed is not offered again, and an option
    that produces no more keys than the plan already holds is left out: it is
    a cost with nothing on the other side of it.
    """
    policy = rules or CuttingRules()
    current = KeyBittingArray(plan, policy).usable_count
    step_mils = plan.chart.increment_mils
    found: list[WideningOption] = []

    for chamber in plan.constants:
        try:
            wider = replace(plan, progressed=tuple(sorted((*plan.progressed, chamber))))
        except Exception:
            continue
        count = KeyBittingArray(wider, policy).usable_count
        if count <= current:
            continue
        found.append(
            WideningOption(
                name=f"progress chamber {chamber}",
                capacity=count,
                wafers_added=1,
                thinnest_split=plan.step * step_mils,
                note="one more wafer in every cylinder",
            )
        )

    if plan.step > 1:
        try:
            tighter = replace(plan, step=plan.step - 1)
            count = KeyBittingArray(tighter, policy).usable_count
        except Exception:
            count = current
        if count > current:
            found.append(
                WideningOption(
                    name=f"progress {plan.step - 1} steps instead of {plan.step}",
                    capacity=count,
                    wafers_added=0,
                    thinnest_split=(plan.step - 1) * step_mils,
                    note="thinner wafers, which the bench will argue about",
                )
            )

    found.sort(key=lambda option: (-option.capacity, option.name))
    return tuple(found)


def best_option(
    options: tuple[WideningOption, ...], thinnest_allowed: int
) -> WideningOption | None:
    """The widest option whose wafers the shop will actually build."""
    workable = [item for item in options if item.thinnest_split >= thinnest_allowed]
    if not workable:
        return None
    return max(workable, key=lambda item: (item.capacity, -item.wafers_added))

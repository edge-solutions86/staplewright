"""Incidental keys, which are the ones the shop never meant to make.

A cylinder does not know what a key symbol is. It knows that in chamber one
it breaks at depth 3 and at depth 7, and in chamber two at 4 and 6, and so
on. Any key carrying one of the break depths in every chamber turns the plug,
whether or not anybody drew it on a schedule.

So a cylinder pinned for a master and three change keys accepts the whole
cartesian product of its break depths, and the difference between that
product and the keys on the schedule is the set of incidental keys. They are
real. They open the door. Nobody has one, which is the only thing that makes
them tolerable, and a system that generates hundreds of them is a system
where somebody eventually cuts one by accident and cannot explain why it
works.

Cross keying is the usual cause. Put two unrelated change keys on one
cylinder and the incidental count goes up hard, which is why the shop asks
what a request is for before it says yes.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from itertools import product

from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting
from hasplib.keys.relations import wafers_for
from hasplib.spec.chart import DepthAndSpaceChart
from hasplib.spec.rules import DEFAULT_RULES, CuttingRules, check_cuts


def break_depths(keys: Sequence[Bitting]) -> dict[int, tuple[int, ...]]:
    """Every depth each chamber has to break at to take all of these keys."""
    if not keys:
        raise InputError("a cylinder with no keys breaks nowhere")
    first, rest = keys[0], keys[1:]
    return wafers_for(first, rest)


def operating_keys(keys: Sequence[Bitting]) -> Iterator[Bitting]:
    """Every key that turns a cylinder pinned to take the ones given.

    Produced in chamber order with the last chamber turning over fastest,
    which puts the intended keys near each other in the listing.
    """
    breaks = break_depths(keys)
    chambers = sorted(breaks)
    for combination in product(*(breaks[chamber] for chamber in chambers)):
        yield Bitting(combination)


def operating_count(keys: Sequence[Bitting]) -> int:
    """How many keys turn the plug, without building any of them."""
    breaks = break_depths(keys)
    total = 1
    for depths in breaks.values():
        total *= len(depths)
    return total


def incidental_keys(keys: Sequence[Bitting]) -> tuple[Bitting, ...]:
    """Keys the cylinder accepts that nobody put on the schedule."""
    intended = {key.depths for key in keys}
    return tuple(key for key in operating_keys(keys) if key.depths not in intended)


def incidental_count(keys: Sequence[Bitting]) -> int:
    return operating_count(keys) - len({key.depths for key in keys})


def cuttable_incidentals(
    chart: DepthAndSpaceChart,
    keys: Sequence[Bitting],
    rules: CuttingRules = DEFAULT_RULES,
) -> tuple[Bitting, ...]:
    """The incidental keys somebody could actually walk out with.

    An incidental key that breaks macs still opens the door, but no machine
    in the shop will cut it and no factory blank arrives that way. The ones
    that pass the rules are the ones worth losing sleep over.
    """
    return tuple(key for key in incidental_keys(keys) if not check_cuts(chart, key, rules))


def is_clean(keys: Sequence[Bitting]) -> bool:
    """Whether a cylinder takes its keys and nothing else."""
    return incidental_count(keys) == 0


def worst_chamber(keys: Sequence[Bitting]) -> int:
    """The chamber carrying the most breaks, which is where to look first."""
    breaks = break_depths(keys)
    return max(breaks, key=lambda chamber: (len(breaks[chamber]), -chamber))


def cross_keying_cost(intended: Sequence[Bitting], extra: Bitting) -> int:
    """How many new incidental keys adding one more key to a cylinder makes."""
    before = incidental_count(intended)
    after = incidental_count([*intended, extra])
    return after - before

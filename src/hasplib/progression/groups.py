"""Keying groups, which are the sets of keys one cylinder has to take.

A door is not keyed to a symbol. It is keyed to a list of them: the change
key for the room, the master for the building, the grand master, and now and
then a key from the far side of the system because a lab is shared. That list
is a keying group, and it is the unit the shop actually pins to.

Two things about a group are worth computing before anything is cut. How many
wafers it costs, which is the bench time and the failure rate. And how many
keys it gives away, which is the security cost, and which goes up fast the
moment a group holds two symbols that are not related to each other.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.schedule import KeyingSchedule
from hasplib.keys.symbol import KeySymbol, common_ancestor, sort_symbols
from hasplib.progression.incidental import incidental_count, operating_count


@dataclass(frozen=True)
class KeyingGroup:
    """The symbols one cylinder answers to, and what to call it."""

    name: str
    symbols: tuple[KeySymbol, ...]

    def __post_init__(self) -> None:
        if not self.symbols:
            raise ConflictError("a keying group holds at least one key", group=self.name)
        if len(set(self.symbols)) != len(self.symbols):
            raise ConflictError("a symbol is in the group twice", group=self.name)

    @property
    def size(self) -> int:
        return len(self.symbols)

    def ordered(self) -> tuple[KeySymbol, ...]:
        return tuple(sort_symbols(list(self.symbols)))

    def holds(self, symbol: KeySymbol) -> bool:
        return symbol in self.symbols

    def top_symbol(self) -> KeySymbol:
        """The lowest key that sits above everything in the group."""
        walk = self.symbols[0]
        for symbol in self.symbols[1:]:
            walk = common_ancestor(walk, symbol)
        return walk

    @property
    def is_cross_keyed(self) -> bool:
        """Whether the group holds keys the hierarchy does not relate.

        A group is not cross keyed when every symbol in it sits on one line
        of descent, master above change key above nothing. The moment two
        symbols sit on different branches the cylinder is doing something the
        schedule alone does not explain.
        """
        line = sort_symbols(list(self.symbols))
        for shallower, deeper in zip(line, line[1:], strict=False):
            if not deeper.descends_from(shallower):
                return True
        return False


def bittings_for(schedule: KeyingSchedule, group: KeyingGroup) -> tuple[Bitting, ...]:
    """The cuts behind a group, refusing any symbol nobody has cut yet."""
    found: list[Bitting] = []
    for symbol in group.ordered():
        entry = schedule.entry(symbol)
        if entry.bitting is None:
            raise NotFoundError(
                "that key has not been cut yet",
                symbol=symbol.render(),
                group=group.name,
            )
        found.append(entry.bitting)
    return tuple(found)


def sections_for(schedule: KeyingSchedule, group: KeyingGroup) -> tuple[str, ...]:
    """Every keyway section the group's keys are cut on."""
    return tuple(sorted({schedule.entry(symbol).section for symbol in group.symbols}))


def cylinder_section(schedule: KeyingSchedule, group: KeyingGroup) -> str:
    """The section a cylinder has to be keyed to for the whole group to enter.

    Every key in the group must enter the cylinder, so the cylinder gets the
    section that all of them reach, which is the lowest one in the family.
    """
    family = schedule.family
    sections = sections_for(schedule, group)
    for candidate in sections:
        if all(family.enters(section, candidate) for section in sections):
            return candidate
    raise ConflictError(
        "no one section takes every key in this group",
        group=group.name,
        sections=sections,
    )


@dataclass(frozen=True)
class GroupCost:
    """What one keying group costs to build and what it gives away."""

    group: str
    keys: int
    wafers: int
    operating: int
    incidental: int

    @property
    def is_clean(self) -> bool:
        return self.incidental == 0


def cost_of(schedule: KeyingSchedule, group: KeyingGroup) -> GroupCost:
    """Wafer count and key leak for one group, without pinning anything."""
    bittings = bittings_for(schedule, group)
    chambers = schedule.chambers
    wafers = 0
    for chamber in range(1, chambers + 1):
        wafers += len({bitting.at(chamber) for bitting in bittings}) - 1
    return GroupCost(
        group=group.name,
        keys=len(bittings),
        wafers=wafers,
        operating=operating_count(bittings),
        incidental=incidental_count(bittings),
    )


def worst_groups(
    schedule: KeyingSchedule, groups: Sequence[KeyingGroup], limit: int = 5
) -> tuple[GroupCost, ...]:
    """The groups giving away the most keys, worst first."""
    costs = [cost_of(schedule, group) for group in groups]
    costs.sort(key=lambda cost: (-cost.incidental, -cost.wafers, cost.group))
    return tuple(costs[:limit])

"""Measuring the shape of a master key system, which is not the same as its size.

A system with four hundred change keys under one grand master is not a large
system, it is a broken one, and the number that says so is not the count. It
is the fan out: how many keys hang directly off each master, and how uneven
that is across the tree.

Three numbers matter and the rest is decoration.

Fan out, because a master with sixty children is a master somebody will use
as a shortcut instead of finding the right key. Depth, because every level
between the top key and the door is a wafer in every chamber and a wafer is
where a system starts picking itself. And reach, which is how many doors one
key turns, because that is the number that goes on an incident report.

Nothing here refuses anything. These are measurements, and the shop decides
what to do about them, because a plant room with one master over forty doors
is fine and a residence hall with the same shape is not.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from statistics import median

from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol

WIDE_FAN_OUT = 25
DEEP_TREE = 4


@dataclass(frozen=True)
class Level:
    """One rank of the tree, and how much is sitting on it."""

    depth: int
    keys: int
    masters: int

    @property
    def change_keys(self) -> int:
        return self.keys - self.masters

    def describe(self) -> str:
        return f"level {self.depth}: {self.keys} keys, {self.masters} of them masters"


@dataclass(frozen=True)
class Shape:
    """What a system looks like, measured rather than judged."""

    symbols: int
    depth: int
    levels: tuple[Level, ...]
    fan_out: dict[str, int]

    @property
    def widest(self) -> tuple[str, int]:
        """The master with the most hanging off it, which is the one to watch."""
        if not self.fan_out:
            return ("", 0)
        return max(self.fan_out.items(), key=lambda pair: (pair[1], pair[0]))

    @property
    def average_fan_out(self) -> float:
        if not self.fan_out:
            return 0.0
        return sum(self.fan_out.values()) / len(self.fan_out)

    @property
    def median_fan_out(self) -> float:
        if not self.fan_out:
            return 0.0
        return float(median(sorted(self.fan_out.values())))

    @property
    def is_lopsided(self) -> bool:
        """One branch carrying most of the system, which is how they grow.

        A tree is lopsided when its widest master carries more than three
        times the middle one. That is not a rule from anywhere, it is the
        point at which the shop stops being able to reason about the system
        by looking at a chart of it.
        """
        if len(self.fan_out) < 3:
            return False
        middle = self.median_fan_out
        return middle > 0 and self.widest[1] > middle * 3

    def describe(self) -> str:
        return f"{self.symbols} symbols, {self.depth} deep, widest master {self.widest[1]}"


def _parent_of(symbol: KeySymbol) -> str | None:
    found = symbol.parent()
    return None if found is None else found.render()


def shape_of(symbols: Sequence[KeySymbol]) -> Shape:
    """Measure a set of symbols, which is all a schedule really is."""
    if not symbols:
        raise InputError("there is nothing to measure")
    by_level: dict[int, list[KeySymbol]] = {}
    fan_out: dict[str, int] = {}
    for symbol in symbols:
        by_level.setdefault(symbol.level, []).append(symbol)
        parent = _parent_of(symbol)
        if parent is not None:
            fan_out[parent] = fan_out.get(parent, 0) + 1
    levels = tuple(
        Level(
            depth,
            len(found),
            sum(1 for symbol in found if not symbol.is_change_key),
        )
        for depth, found in sorted(by_level.items())
    )
    return Shape(len(symbols), max(by_level), levels, fan_out)


def wide_masters(found: Shape, over: int = WIDE_FAN_OUT) -> tuple[tuple[str, int], ...]:
    """Masters carrying more than the shop is comfortable with, widest first."""
    if over < 1:
        raise InputError("a fan out limit is at least one", over=over)
    wide = [(name, count) for name, count in found.fan_out.items() if count > over]
    wide.sort(key=lambda pair: (-pair[1], pair[0]))
    return tuple(wide)


def only_children(found: Shape) -> tuple[str, ...]:
    """Masters with exactly one key under them, which are a level for nothing.

    A master that opens one change key is a second key cut to the same doors
    with an extra wafer in every chamber to make it possible. It is always a
    plan that was going to grow and did not.
    """
    return tuple(sorted(name for name, count in found.fan_out.items() if count == 1))


def barren_masters(symbols: Sequence[KeySymbol]) -> tuple[str, ...]:
    """Masters with nothing under them at all, in symbol order."""
    found = shape_of(symbols)
    masters = {
        symbol.render() for symbol in symbols if not symbol.is_change_key and not symbol.is_top
    }
    return tuple(sorted(masters - set(found.fan_out)))


def orphans(symbols: Sequence[KeySymbol]) -> tuple[str, ...]:
    """Keys whose master is not on the schedule, which is a gap in the tree."""
    present = {symbol.render() for symbol in symbols}
    missing = {
        parent
        for parent in (_parent_of(symbol) for symbol in symbols)
        if parent is not None and parent not in present
    }
    return tuple(sorted(missing))


def level_table(found: Shape) -> tuple[str, ...]:
    """The tree rank by rank, which is the picture people want."""
    lines = [f"{found.symbols} symbols across {found.depth + 1} levels"]
    lines.extend(f"  {level.describe()}" for level in found.levels)
    return tuple(lines)


def shape_report(symbols: Sequence[KeySymbol], over: int = WIDE_FAN_OUT) -> tuple[str, ...]:
    """Everything worth saying about the shape of a system, on one page."""
    found = shape_of(symbols)
    lines = [found.describe(), ""]
    lines.extend(level_table(found))
    lines.append("")
    wide = wide_masters(found, over)
    if wide:
        lines.append(f"{len(wide)} masters carry more than {over} keys")
        lines.extend(f"  {name:<10}{count}" for name, count in wide)
    else:
        lines.append(f"no master carries more than {over} keys")
    thin = only_children(found)
    if thin:
        lines.append(f"{len(thin)} masters have one key under them")
        lines.extend(f"  {name}" for name in thin)
    if found.depth > DEEP_TREE:
        lines.append(f"the tree is {found.depth} deep, which is a wafer in every chamber")
    if found.is_lopsided:
        lines.append(f"one branch carries the system: {found.widest[0]} has {found.widest[1]}")
    missing = orphans(symbols)
    if missing:
        lines.append(f"{len(missing)} masters are referred to and not on the schedule")
        lines.extend(f"  {name}" for name in missing)
    return tuple(lines)


def compare(first: Shape, second: Shape) -> tuple[str, ...]:
    """Two systems side by side, for the argument about which to extend."""
    return (
        f"symbols   {first.symbols:>6}{second.symbols:>8}",
        f"depth     {first.depth:>6}{second.depth:>8}",
        f"widest    {first.widest[1]:>6}{second.widest[1]:>8}",
        f"average   {first.average_fan_out:>6.1f}{second.average_fan_out:>8.1f}",
    )

"""What one key opens, which is the question every incident starts with.

Three records have to line up to answer it. A cylinder sits in an opening. A
cylinder belongs to a keying group. A keying group lists the symbols that
operate it. Follow those and a symbol turns into a list of doors.

The reverse direction is the one that gets asked at eight in the morning. A
tag number comes off a key somebody lost, the ledger says what it was cut to,
and the shop needs every opening that key turns before it can decide how much
of the campus to rekey.

Nothing here is derived from the hierarchy. A grand master opens a door
because the door's group says so, not because the symbol sits high up, and
the difference matters: the whole point of an incident review is finding the
cylinder where the two disagree.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping

from hasplib.campus.hardware import Cylinder, HardwareRegister, Position
from hasplib.control.issue import IssuedKey, KeyLedger
from hasplib.errors import NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.groups import KeyingGroup


class CoverageMap:
    """Cylinders, their groups, and the doors those groups sit in."""

    def __init__(
        self,
        register: HardwareRegister,
        groups: Mapping[str, KeyingGroup],
    ) -> None:
        self.register = register
        self.groups = dict(groups)

    def group(self, name: str) -> KeyingGroup:
        try:
            return self.groups[name]
        except KeyError:
            raise NotFoundError("no such keying group", group=name) from None

    def group_of(self, cylinder: Cylinder) -> KeyingGroup | None:
        if not cylinder.group:
            return None
        return self.groups.get(cylinder.group)

    def symbols_opening(self, opening_number: str) -> tuple[KeySymbol, ...]:
        """Every symbol that turns anything on one opening."""
        found: set[KeySymbol] = set()
        for cylinder in self.register.on_opening(opening_number):
            group = self.group_of(cylinder)
            if group is not None:
                found.update(group.symbols)
        return tuple(sorted(found, key=lambda symbol: (symbol.level, symbol.render())))

    def openings_for(self, symbol: KeySymbol) -> tuple[str, ...]:
        """Every opening a key cut to this symbol turns."""
        found: set[str] = set()
        for cylinder in self.register:
            if not cylinder.is_installed:
                continue
            group = self.group_of(cylinder)
            if group is not None and group.holds(symbol):
                found.add(cylinder.installed_at or "")
        return tuple(sorted(found))

    def openings_for_any(self, symbols: Iterable[KeySymbol]) -> tuple[str, ...]:
        found: set[str] = set()
        for symbol in symbols:
            found.update(self.openings_for(symbol))
        return tuple(sorted(found))

    def buildings_for(self, symbol: KeySymbol) -> tuple[str, ...]:
        openings = self.openings_for(symbol)
        found = {self.register.openings.opening(number).building for number in openings}
        return tuple(sorted(found))

    def cylinders_for(self, symbol: KeySymbol) -> tuple[Cylinder, ...]:
        """The cylinders that would have to come apart to lock a symbol out."""
        found = []
        for cylinder in self.register:
            group = self.group_of(cylinder)
            if group is not None and group.holds(symbol):
                found.append(cylinder)
        found.sort(key=lambda cylinder: cylinder.serial)
        return tuple(found)

    def unmapped_cylinders(self) -> tuple[Cylinder, ...]:
        """Installed cylinders whose group is missing or unknown."""
        found = [
            cylinder
            for cylinder in self.register
            if cylinder.is_installed and self.group_of(cylinder) is None
        ]
        found.sort(key=lambda cylinder: cylinder.serial)
        return tuple(found)

    def uncovered_positions(self) -> tuple[tuple[str, Position], ...]:
        """Keyed openings with nothing in the lockset, which open to nobody."""
        found = [
            (opening.number, Position.LOCKSET) for opening in self.register.uncovered_openings()
        ]
        return tuple(found)

    def reach_of(self, symbol: KeySymbol) -> int:
        return len(self.openings_for(symbol))

    def widest_symbols(self, limit: int = 5) -> tuple[tuple[KeySymbol, int], ...]:
        """The keys that open the most doors, which are the ones to watch."""
        counts: dict[KeySymbol, int] = {}
        for cylinder in self.register:
            if not cylinder.is_installed:
                continue
            group = self.group_of(cylinder)
            if group is None:
                continue
            for symbol in group.symbols:
                counts[symbol] = counts.get(symbol, 0) + 1
        ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0].render()))
        return tuple(ranked[:limit])


def openings_a_tag_turns(coverage: CoverageMap, ledger: KeyLedger, tag: str) -> tuple[str, ...]:
    """What one stamped key opens, read through the ledger."""
    key = ledger.key(tag)
    return coverage.openings_for(KeySymbol.parse(key.symbol))


def keys_that_open(ledger: KeyLedger, symbols: Iterable[KeySymbol]) -> tuple[IssuedKey, ...]:
    """Every key out that is cut to one of these symbols."""
    wanted = {symbol.render() for symbol in symbols}
    found = [key for key in ledger.out() if key.symbol in wanted]
    found.sort(key=lambda key: key.tag)
    return tuple(found)


def people_who_can_open(
    coverage: CoverageMap, ledger: KeyLedger, opening_number: str
) -> tuple[str, ...]:
    """Everybody currently holding a key that turns one door."""
    symbols = coverage.symbols_opening(opening_number)
    holders = {key.keyholder for key in keys_that_open(ledger, symbols)}
    return tuple(sorted(holders))

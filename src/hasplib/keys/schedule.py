"""The keying schedule, which is the document the whole shop argues about.

A schedule is every symbol in a system, what each one is cut to, and which
keyway section each one is cut on. It is a tree by construction because the
symbols are a tree, so the schedule only has to refuse the ways a person can
break that tree by hand: hanging a key under a master nobody created, cutting
two different keys the same, or cutting a key on a section it will not enter.

Bittings are optional on purpose. A schedule gets drawn before it gets cut,
and half the value of the document is being able to say which symbols are
still open.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace

from hasplib.errors import ConflictError, HierarchyError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol, sort_symbols
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.rules import CutViolation, RuleSet


@dataclass(frozen=True)
class ScheduleEntry:
    """One line of the schedule."""

    symbol: KeySymbol
    section: str
    bitting: Bitting | None = None
    note: str = ""

    @property
    def is_cut(self) -> bool:
        return self.bitting is not None

    def render(self) -> str:
        cut = self.bitting.render() if self.bitting else "open"
        return f"{self.symbol.render():<8}{self.section:<5}{cut}"


class KeyingSchedule:
    """Every symbol in one system, with what it is cut to.

    The top key is created with the schedule because a system without one is
    not a system. Everything else is added under something already present.
    """

    def __init__(
        self,
        name: str,
        ruleset: RuleSet,
        family: KeywayFamily,
        top_section: str,
    ) -> None:
        family.section(top_section)
        self.name = name
        self.ruleset = ruleset
        self.family = family
        self._entries: dict[str, ScheduleEntry] = {}
        top = KeySymbol.parse("GGM")
        self._entries[top.render()] = ScheduleEntry(top, top_section)

    @property
    def top(self) -> KeySymbol:
        return KeySymbol.parse("GGM")

    @property
    def chambers(self) -> int:
        return self.ruleset.chart.chambers

    def add(self, symbol: KeySymbol, section: str | None = None, note: str = "") -> ScheduleEntry:
        """Put a symbol in the schedule under a parent that already exists."""
        key = symbol.render()
        if key in self._entries:
            raise ConflictError("that symbol is already in the schedule", symbol=key)
        parent = symbol.parent()
        if parent is None:
            raise ConflictError("the schedule already has a top key", symbol=key)
        parent_entry = self._entries.get(parent.render())
        if parent_entry is None:
            raise HierarchyError("nothing to hang that under", symbol=key, wanted=parent.render())
        chosen = section or parent_entry.section
        self.family.section(chosen)
        if not self.family.enters(parent_entry.section, chosen):
            raise HierarchyError(
                "the key above would not enter that section",
                symbol=key,
                section=chosen,
                above=parent_entry.section,
            )
        entry = ScheduleEntry(symbol, chosen, None, note)
        self._entries[key] = entry
        return entry

    def entry(self, symbol: KeySymbol) -> ScheduleEntry:
        try:
            return self._entries[symbol.render()]
        except KeyError:
            raise NotFoundError(
                "no such symbol in this schedule", symbol=symbol.render(), system=self.name
            ) from None

    def assign(self, symbol: KeySymbol, bitting: Bitting) -> ScheduleEntry:
        """Cut a symbol, refusing anything the ruleset will not cut."""
        entry = self.entry(symbol)
        violations = self.ruleset.check(bitting)
        if violations:
            raise ConflictError(
                "that bitting is not one the shop cuts",
                symbol=symbol.render(),
                reasons=tuple(v.rule for v in violations),
            )
        holder = self.symbol_cut_to(bitting)
        if holder is not None and holder != symbol:
            raise ConflictError(
                "another key in this system is already cut that way",
                symbol=symbol.render(),
                held_by=holder.render(),
                bitting=bitting.render(),
            )
        updated = replace(entry, bitting=bitting)
        self._entries[symbol.render()] = updated
        return updated

    def clear(self, symbol: KeySymbol) -> ScheduleEntry:
        """Take the cuts off a symbol and leave it in the schedule."""
        entry = self.entry(symbol)
        updated = replace(entry, bitting=None)
        self._entries[symbol.render()] = updated
        return updated

    def bitting_of(self, symbol: KeySymbol) -> Bitting | None:
        return self.entry(symbol).bitting

    def symbol_cut_to(self, bitting: Bitting) -> KeySymbol | None:
        for entry in self._entries.values():
            if entry.bitting == bitting:
                return entry.symbol
        return None

    def children(self, symbol: KeySymbol) -> tuple[KeySymbol, ...]:
        self.entry(symbol)
        found = [
            entry.symbol for entry in self._entries.values() if entry.symbol.parent() == symbol
        ]
        return tuple(sort_symbols(found))

    def descendants(self, symbol: KeySymbol) -> tuple[KeySymbol, ...]:
        self.entry(symbol)
        found = [
            entry.symbol
            for entry in self._entries.values()
            if entry.symbol != symbol and entry.symbol.descends_from(symbol)
        ]
        return tuple(sort_symbols(found))

    def symbols(self) -> tuple[KeySymbol, ...]:
        return tuple(sort_symbols([entry.symbol for entry in self._entries.values()]))

    def entries(self) -> tuple[ScheduleEntry, ...]:
        return tuple(self.entry(symbol) for symbol in self.symbols())

    def change_keys(self) -> tuple[KeySymbol, ...]:
        return tuple(symbol for symbol in self.symbols() if symbol.is_change_key)

    def masters(self) -> tuple[KeySymbol, ...]:
        return tuple(symbol for symbol in self.symbols() if not symbol.is_change_key)

    def open_symbols(self) -> tuple[KeySymbol, ...]:
        return tuple(entry.symbol for entry in self.entries() if not entry.is_cut)

    def cut_symbols(self) -> tuple[KeySymbol, ...]:
        return tuple(entry.symbol for entry in self.entries() if entry.is_cut)

    def problems(self) -> tuple[CutViolation, ...]:
        """Anything cut into the schedule that the ruleset now refuses.

        Policy changes after a system is drawn, and the shop wants to know
        which of the keys already in the ground would not be cut today.
        """
        found: list[CutViolation] = []
        for entry in self.entries():
            if entry.bitting is None:
                continue
            for violation in self.ruleset.check(entry.bitting):
                found.append(
                    CutViolation(
                        rule=violation.rule,
                        message=f"{entry.symbol.render()}: {violation.message}",
                        chamber=violation.chamber,
                        facts=violation.facts,
                    )
                )
        return tuple(found)

    def __contains__(self, symbol: object) -> bool:
        if not isinstance(symbol, KeySymbol):
            return False
        return symbol.render() in self._entries

    def __iter__(self) -> Iterator[ScheduleEntry]:
        return iter(self.entries())

    def __len__(self) -> int:
        return len(self._entries)

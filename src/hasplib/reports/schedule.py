"""The printed keying schedule, which is the document the shop hands over.

Two forms of the same thing. The schedule proper is one line a symbol, in
hierarchy order, indented so the tree is visible at arm's length. The bitting
list is the same data sorted by cut, which is what somebody uses when they
have a key in their hand and no idea what it is.

Both are fixed width because they get printed, filed, and read on a bench
with a light on one side. Nothing here wraps.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.keys.schedule import KeyingSchedule, ScheduleEntry
from hasplib.keys.symbol import KeySymbol

SYMBOL_WIDTH = 10
SECTION_WIDTH = 6
CUT_WIDTH = 14


def schedule_line(entry: ScheduleEntry, indent: bool = True) -> str:
    """One line: the symbol, where it is cut, and what it is cut to."""
    lead = "  " * entry.symbol.level if indent else ""
    name = f"{lead}{entry.symbol.render()}"
    cut = entry.bitting.render() if entry.bitting else "open"
    left = f"{name:<{SYMBOL_WIDTH + 6}}{entry.section:<{SECTION_WIDTH}}"
    return f"{left}{cut:<{CUT_WIDTH}}{entry.note}"


def schedule_report(schedule: KeyingSchedule, title: str = "") -> tuple[str, ...]:
    """The whole schedule, in hierarchy order."""
    lines: list[str] = []
    if title:
        lines.append(title)
    lines.append(f"{'symbol':<{SYMBOL_WIDTH + 6}}{'kwy':<{SECTION_WIDTH}}{'cut':<{CUT_WIDTH}}note")
    lines.extend(schedule_line(entry).rstrip() for entry in schedule.entries())
    lines.append(f"{len(schedule)} symbols, {len(schedule.open_symbols())} of them open")
    return tuple(lines)


def bitting_list(schedule: KeyingSchedule) -> tuple[str, ...]:
    """The same keys sorted by cut, for reading a key nobody recognises."""
    cut = [entry for entry in schedule.entries() if entry.bitting is not None]
    cut.sort(key=lambda entry: entry.bitting.depths if entry.bitting else ())
    lines = [f"{'cut':<{CUT_WIDTH}}{'symbol':<{SYMBOL_WIDTH}}kwy"]
    for entry in cut:
        rendered = entry.bitting.render() if entry.bitting else ""
        lines.append(
            f"{rendered:<{CUT_WIDTH}}{entry.symbol.render():<{SYMBOL_WIDTH}}{entry.section}"
        )
    return tuple(lines)


def open_symbols_report(schedule: KeyingSchedule) -> tuple[str, ...]:
    """What is drawn and not cut, which is what the shop owes somebody."""
    open_ones = schedule.open_symbols()
    if not open_ones:
        return ("every symbol on this schedule is cut",)
    lines = [f"{len(open_ones)} symbols drawn and not cut"]
    lines.extend(f"  {symbol.render()}" for symbol in open_ones)
    return tuple(lines)


def tree_report(schedule: KeyingSchedule) -> tuple[str, ...]:
    """The hierarchy alone, with the count of what hangs under each key."""
    lines: list[str] = []
    for symbol in schedule.symbols():
        below = len(schedule.descendants(symbol))
        lead = "  " * symbol.level
        tail = f"{below} under it" if below else "leaf"
        lines.append(f"{lead}{symbol.render()}  {tail}")
    return tuple(lines)


def cross_reference(
    schedule: KeyingSchedule, openings_by_symbol: dict[KeySymbol, Sequence[str]]
) -> tuple[str, ...]:
    """Every symbol against the doors it opens, for the facilities office."""
    lines = [f"{'symbol':<{SYMBOL_WIDTH}}opens"]
    for symbol in schedule.symbols():
        doors = openings_by_symbol.get(symbol, ())
        rendered = ", ".join(doors) if doors else "nothing on record"
        lines.append(f"{symbol.render():<{SYMBOL_WIDTH}}{rendered}")
    return tuple(lines)


def as_text(lines: Sequence[str]) -> str:
    return "\n".join(lines)

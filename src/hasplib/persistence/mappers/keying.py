"""Schedules and keying groups, between the tables and the domain objects.

Loading a schedule uses the loading door rather than the front door. Every
line went through add and assign once, years ago, under whatever rules were
current then, and forcing a 2011 system to pass today's policy on the way out
of the database would mean the shop could not open half of what it maintains.
So rows come back through load, and the review functions are where anything
wrong gets said out loud.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.keys.schedule import KeyingSchedule, ScheduleEntry
from hasplib.keys.symbol import KeySymbol, sort_symbols
from hasplib.persistence.models.keying import GroupMemberRow, GroupRow, ScheduleEntryRow
from hasplib.progression.groups import KeyingGroup
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.rules import RuleSet


def entry_to_row(entry: ScheduleEntry, system_id: int) -> ScheduleEntryRow:
    return ScheduleEntryRow(
        system_id=system_id,
        symbol=entry.symbol,
        section=entry.section,
        bitting=entry.bitting,
        note=entry.note,
    )


def entry_from_row(row: ScheduleEntryRow) -> ScheduleEntry:
    return ScheduleEntry(
        symbol=row.symbol,
        section=row.section,
        bitting=row.bitting,
        note=row.note,
    )


def schedule_to_rows(schedule: KeyingSchedule, system_id: int) -> list[ScheduleEntryRow]:
    """Every line, shallow symbols first so a reader can follow the tree."""
    return [entry_to_row(entry, system_id) for entry in schedule.entries()]


def schedule_from_rows(
    name: str,
    ruleset: RuleSet,
    family: KeywayFamily,
    top_section: str,
    rows: Sequence[ScheduleEntryRow],
) -> KeyingSchedule:
    """Rebuild a schedule from its rows, checking nothing on the way in."""
    schedule = KeyingSchedule(name, ruleset, family, top_section)
    ordered = sorted(rows, key=lambda row: (row.symbol.level, row.symbol.render()))
    for row in ordered:
        schedule.load(entry_from_row(row))
    return schedule


def group_to_rows(group: KeyingGroup, system_id: int) -> tuple[GroupRow, list[GroupMemberRow]]:
    """A group row and one member row a symbol, ids left for the caller."""
    row = GroupRow(system_id=system_id, name=group.name)
    members = [GroupMemberRow(symbol=symbol) for symbol in group.ordered()]
    return row, members


def group_from_row(row: GroupRow) -> KeyingGroup:
    symbols = sort_symbols([member.symbol for member in row.members])
    return KeyingGroup(row.name, tuple(symbols))


def groups_from_rows(rows: Sequence[GroupRow]) -> dict[str, KeyingGroup]:
    """Every group in a system, keyed by name the way coverage wants them."""
    return {row.name: group_from_row(row) for row in rows}


def symbols_in(rows: Sequence[ScheduleEntryRow]) -> tuple[KeySymbol, ...]:
    """Just the symbols, in schedule order, for the cheap listings."""
    return tuple(sort_symbols([row.symbol for row in rows]))

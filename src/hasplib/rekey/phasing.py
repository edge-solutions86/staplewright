"""Spreading a rekey across an academic year, which is a calendar problem.

A four hundred door rekey is not a scheduling problem, it is a negotiation
with a calendar somebody else owns. Residence halls can only be touched in
the fortnight after the students leave. Teaching rooms cannot be touched in
an exam period at all. The plant room can be done any time and never gets
done, because it is never urgent.

So this takes a list of doors, a calendar of windows, and works out which
door goes in which window. It does not optimise anything. It refuses the ones
that will not fit and says why, which is the only useful thing a plan can do
in September, because the answer the shop needs is how much of this is not
happening this year.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry


class WindowKind(StrEnum):
    """What sort of gap in the calendar this is."""

    VACATION = "vacation"
    TERM = "term"
    WEEKEND = "weekend"
    SHUTDOWN = "shutdown"


class Restriction(StrEnum):
    """Why a building cannot be touched outside its window."""

    OCCUPIED = "occupied by residents"
    TEACHING = "in teaching use"
    EXAMS = "exams running"
    RESEARCH = "research that cannot be interrupted"
    NONE = "no restriction"


@dataclass(frozen=True)
class Window:
    """A stretch of days when work can happen, and how much fits in it."""

    name: str
    kind: WindowKind
    opens_on: date
    closes_on: date
    doors_per_day: int = 12

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise InputError("a window needs a name")
        if self.closes_on < self.opens_on:
            raise InputError("a window closes after it opens", window=self.name)
        if self.doors_per_day < 1:
            raise InputError("a window does at least a door a day", window=self.name)

    @property
    def days(self) -> int:
        return (self.closes_on - self.opens_on).days + 1

    @property
    def capacity(self) -> int:
        """How many doors fit, which is the number everybody argues about."""
        return self.days * self.doors_per_day

    def covers(self, day: date) -> bool:
        return self.opens_on <= day <= self.closes_on

    def overlaps(self, other: Window) -> bool:
        return self.opens_on <= other.closes_on and other.opens_on <= self.closes_on

    def day_for(self, position: int) -> date:
        """Which day the nth door of this window lands on."""
        if position < 0:
            raise InputError("a position counts from nothing", window=self.name)
        return self.opens_on + timedelta(days=position // self.doors_per_day)

    def describe(self) -> str:
        return (
            f"{self.name} {self.opens_on.isoformat()} to {self.closes_on.isoformat()}, "
            f"{self.capacity} doors"
        )


@dataclass(frozen=True)
class Door:
    """One opening in the plan, with whatever stops it being done today."""

    number: str
    building: str
    restriction: Restriction = Restriction.NONE
    minutes: int = 25

    def __post_init__(self) -> None:
        if self.minutes < 1:
            raise InputError("a door takes some minutes", opening=self.number)

    @property
    def is_free(self) -> bool:
        return self.restriction is Restriction.NONE


@dataclass(frozen=True)
class Placement:
    """One door, in one window, on one day."""

    door: Door
    window: str
    on: date

    def describe(self) -> str:
        return f"{self.door.number} {self.window} {self.on.isoformat()}"


@dataclass
class Phasing:
    """The whole plan: what got a day, and what did not."""

    placed: tuple[Placement, ...] = ()
    unplaced: tuple[tuple[Door, str], ...] = ()
    windows: tuple[Window, ...] = field(default_factory=tuple)

    @property
    def door_count(self) -> int:
        return len(self.placed) + len(self.unplaced)

    @property
    def landed(self) -> bool:
        return not self.unplaced

    def in_window(self, name: str) -> tuple[Placement, ...]:
        return tuple(item for item in self.placed if item.window == name)

    def on_day(self, day: date) -> tuple[Placement, ...]:
        return tuple(item for item in self.placed if item.on == day)

    def load_by_window(self) -> dict[str, int]:
        counts = {window.name: 0 for window in self.windows}
        for item in self.placed:
            counts[item.window] = counts.get(item.window, 0) + 1
        return counts

    def spare_capacity(self) -> dict[str, int]:
        used = self.load_by_window()
        return {window.name: window.capacity - used.get(window.name, 0) for window in self.windows}

    def reasons(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for _, reason in self.unplaced:
            counts[reason] = counts.get(reason, 0) + 1
        return dict(sorted(counts.items(), key=lambda pair: (-pair[1], pair[0])))


class Calendar:
    """The windows the shop is allowed to work in, and what each one allows."""

    def __init__(self) -> None:
        self._windows: Registry[Window] = Registry("window", "name")
        self._allowed: dict[str, set[Restriction]] = {}

    def add(self, window: Window, allows: Sequence[Restriction] = ()) -> Window:
        """Add a window, refusing one that runs over another."""
        for held in self._windows.values():
            if held.overlaps(window):
                raise ConflictError(
                    "that window runs over another one",
                    window=window.name,
                    against=held.name,
                )
        self._windows.add(window.name, window)
        self._allowed[window.name] = {Restriction.NONE, *allows}
        return window

    def window(self, name: str) -> Window:
        return self._windows.get(name)

    def allows(self, name: str, restriction: Restriction) -> bool:
        return restriction in self._allowed.get(name, set())

    def in_order(self) -> tuple[Window, ...]:
        found = list(self._windows.values())
        found.sort(key=lambda window: (window.opens_on, window.name))
        return tuple(found)

    def windows_for(self, restriction: Restriction) -> tuple[Window, ...]:
        return tuple(window for window in self.in_order() if self.allows(window.name, restriction))

    def total_capacity(self) -> int:
        return sum(window.capacity for window in self._windows.values())

    def __contains__(self, name: object) -> bool:
        return name in self._windows

    def __iter__(self) -> Iterator[Window]:
        return iter(self.in_order())

    def __len__(self) -> int:
        return len(self._windows)


def phase(calendar: Calendar, doors: Sequence[Door]) -> Phasing:
    """Put every door in the first window that will take it.

    First rather than best. A door that can only go in one window has to go
    there, and a door that could go anywhere may as well go early, because
    the thing that ruins a year long plan is a window that fills up in March
    with work that could have been done in August.
    """
    windows = calendar.in_order()
    used = {window.name: 0 for window in windows}
    placed: list[Placement] = []
    unplaced: list[tuple[Door, str]] = []
    for door in _hardest_first(calendar, doors):
        landed = False
        for window in windows:
            if not calendar.allows(window.name, door.restriction):
                continue
            if used[window.name] >= window.capacity:
                continue
            placed.append(Placement(door, window.name, window.day_for(used[window.name])))
            used[window.name] += 1
            landed = True
            break
        if not landed:
            unplaced.append((door, _why(calendar, door, used)))
    placed.sort(key=lambda item: (item.on, item.door.number))
    return Phasing(tuple(placed), tuple(unplaced), windows)


def _hardest_first(calendar: Calendar, doors: Sequence[Door]) -> list[Door]:
    """Doors with the fewest windows open to them go first, then by number."""
    return sorted(
        doors,
        key=lambda door: (len(calendar.windows_for(door.restriction)), door.number),
    )


def _why(calendar: Calendar, door: Door, used: dict[str, int]) -> str:
    """The reason a door did not land, which is one of exactly two things."""
    open_to_it = calendar.windows_for(door.restriction)
    if not open_to_it:
        return f"no window takes work that is {door.restriction.value}"
    return "every window that would take it is full"


def year_plan(calendar: Calendar, doors: Sequence[Door]) -> tuple[str, ...]:
    """The plan as the page that goes to the facilities meeting."""
    found = phase(calendar, doors)
    lines = [f"{found.door_count} doors across {len(calendar)} windows"]
    load = found.load_by_window()
    spare = found.spare_capacity()
    for window in calendar:
        lines.append(
            f"  {window.name:<16}{load.get(window.name, 0):>4} placed, "
            f"{spare.get(window.name, 0):>4} spare"
        )
    if found.landed:
        lines.append("everything fits this year")
        return tuple(lines)
    lines.append(f"{len(found.unplaced)} doors do not fit")
    lines.extend(f"  {count:>4}  {reason}" for reason, count in found.reasons().items())
    return tuple(lines)


def carry_over(found: Phasing) -> tuple[Door, ...]:
    """The doors for next year, in the order to put them in next year."""
    return tuple(door for door, _ in found.unplaced)

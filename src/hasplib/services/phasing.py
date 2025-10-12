"""Turning the campus into a phasing plan, which is where the doors come from.

The phasing module knows nothing about buildings, and that is deliberate: it
takes doors and windows and does arithmetic. What it cannot do is decide that
a residence hall room is occupied and a plant room is not, because that is a
fact about this campus and it lives in the opening schedule.

So this is the translation layer, and it is the only place in the library
where a door function turns into a calendar restriction. The mapping is a
dictionary rather than a chain of ifs because somebody will disagree with it
within a year and the argument should be about one table.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.campus.openings import Function, Opening
from hasplib.errors import NotFoundError
from hasplib.persistence.loaders import load_openings
from hasplib.persistence.models.rekey import WorkOrderRow
from hasplib.rekey.phasing import (
    Calendar,
    Door,
    Phasing,
    Restriction,
    Window,
    WindowKind,
    carry_over,
    phase,
    year_plan,
)

RESTRICTION_BY_FUNCTION = {
    Function.CLASSROOM: Restriction.TEACHING,
    Function.OFFICE: Restriction.OCCUPIED,
    Function.ENTRANCE: Restriction.NONE,
    Function.STOREROOM: Restriction.NONE,
    Function.PADLOCK: Restriction.NONE,
    Function.CABINET: Restriction.NONE,
    Function.PRIVACY: Restriction.OCCUPIED,
}
"""What each door function means for the calendar, on this campus.

Offices count as occupied because somebody is behind the door and wants
warning. A store room does not, because nobody is in it, and a store room
that somebody is living in is a different conversation.
"""

DEFAULT_RATE = 12
MINUTES_PER_DOOR = 25


def restriction_for(opening: Opening) -> Restriction:
    """What stops this door being done today."""
    return RESTRICTION_BY_FUNCTION.get(opening.function, Restriction.NONE)


def door_for(opening: Opening, minutes: int = MINUTES_PER_DOOR) -> Door:
    return Door(opening.number, opening.building, restriction_for(opening), minutes)


class PhasingService:
    """The campus, the calendar, and which door happens when."""

    def __init__(self, session: Session, calendar: Calendar | None = None) -> None:
        self.session = session
        self.calendar = calendar or Calendar()

    def add_window(
        self,
        name: str,
        kind: WindowKind,
        opens_on: date,
        closes_on: date,
        doors_per_day: int = DEFAULT_RATE,
        allows: Sequence[Restriction] = (),
    ) -> Window:
        window = Window(name, kind, opens_on, closes_on, doors_per_day)
        return self.calendar.add(window, allows)

    def doors_in(self, building: str) -> tuple[Door, ...]:
        """Every keyed door on a building, as the phasing module wants them."""
        schedule = load_openings(self.session)
        found = [
            door_for(opening) for opening in schedule.in_building(building) if opening.is_keyed
        ]
        if not found:
            raise NotFoundError("no keyed doors on that building", building=building)
        return tuple(sorted(found, key=lambda door: door.number))

    def doors_on(self, order_number: str) -> tuple[Door, ...]:
        """The doors a work order covers, which is the usual way in."""
        row = self.session.get(WorkOrderRow, order_number)
        if row is None:
            raise NotFoundError("no such work order", order=order_number)
        schedule = load_openings(self.session)
        found = []
        for line in row.lines:
            if not line.opening_number:
                continue
            found.append(door_for(schedule.opening(line.opening_number)))
        return tuple(sorted(found, key=lambda door: door.number))

    def every_keyed_door(self) -> tuple[Door, ...]:
        schedule = load_openings(self.session)
        found = [door_for(opening) for opening in schedule.keyed_openings()]
        return tuple(sorted(found, key=lambda door: door.number))

    def plan(self, doors: Sequence[Door]) -> Phasing:
        return phase(self.calendar, doors)

    def plan_building(self, building: str) -> Phasing:
        return self.plan(self.doors_in(building))

    def plan_order(self, order_number: str) -> Phasing:
        return self.plan(self.doors_on(order_number))

    def plan_campus(self) -> Phasing:
        return self.plan(self.every_keyed_door())

    def sheet(self, doors: Sequence[Door]) -> tuple[str, ...]:
        return year_plan(self.calendar, doors)

    def next_year(self, doors: Sequence[Door]) -> tuple[Door, ...]:
        return carry_over(self.plan(doors))

    def day_sheet(self, doors: Sequence[Door], day: date) -> tuple[str, ...]:
        """What a crew does on one day, in the order they walk it."""
        found = self.plan(doors).on_day(day)
        if not found:
            return (f"nothing booked for {day.isoformat()}",)
        lines = [f"{len(found)} doors on {day.isoformat()}"]
        for item in sorted(found, key=lambda entry: entry.door.number):
            lines.append(f"  {item.door.number:<12}{item.door.building}")
        return tuple(lines)

    def windows(self) -> tuple[Window, ...]:
        return self.calendar.in_order()

    def capacity_against(self, doors: Sequence[Door]) -> tuple[int, int]:
        """How many doors there are against how many the year can take."""
        return len(doors), self.calendar.total_capacity()


def academic_year(starting: int, rate: int = DEFAULT_RATE) -> Calendar:
    """The four windows a campus year actually has, which is not many.

    These are the windows the shop gets, not the windows it would like. The
    long summer one is the only one where residence work happens at all, and
    the fortnight after finals is the only one where teaching rooms open up.
    """
    calendar = Calendar()
    calendar.add(
        Window(
            "summer",
            WindowKind.VACATION,
            date(starting, 6, 1),
            date(starting, 8, 20),
            rate,
        ),
        [Restriction.OCCUPIED, Restriction.TEACHING, Restriction.RESEARCH],
    )
    calendar.add(
        Window(
            "reading week",
            WindowKind.TERM,
            date(starting, 10, 20),
            date(starting, 10, 26),
            rate // 2 or 1,
        ),
        [Restriction.TEACHING],
    )
    calendar.add(
        Window(
            "winter shutdown",
            WindowKind.SHUTDOWN,
            date(starting, 12, 20),
            date(starting + 1, 1, 3),
            rate,
        ),
        [Restriction.OCCUPIED, Restriction.TEACHING],
    )
    calendar.add(
        Window(
            "spring break",
            WindowKind.VACATION,
            date(starting + 1, 3, 14),
            date(starting + 1, 3, 22),
            rate,
        ),
        [Restriction.TEACHING],
    )
    return calendar


def buildings_with_work(session: Session) -> tuple[str, ...]:
    """Every building that has a keyed door, for the planning menu."""
    schedule = load_openings(session)
    found = {opening.building for opening in schedule.keyed_openings()}
    return tuple(sorted(found))


def orders_to_plan(session: Session) -> tuple[str, ...]:
    """Work orders that are not finished, oldest first."""
    statement = (
        select(WorkOrderRow)
        .where(WorkOrderRow.state.in_(("drafted", "scheduled", "started")))
        .order_by(WorkOrderRow.number)
    )
    return tuple(row.number for row in session.execute(statement).scalars().all())

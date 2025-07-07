"""Planning a technician's day, which is mostly about not walking twice.

A rekey day is a bench half and a door half. The bench half is predictable:
so many minutes a cylinder, done sitting down, and it does not matter what
order it happens in. The door half is walking, and walking is where a day
goes wrong, because a crew that does a door on the second floor, then one in
the basement, then another on the second floor has spent twenty minutes on
stairs for nothing.

So the plan is sorted by building and floor and nothing cleverer. A travelling
salesman solution would save perhaps four minutes on a real building and
would produce a route nobody can follow with a bag of pins in one hand.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.campus.openings import OpeningSchedule
from hasplib.errors import ConflictError, InputError
from hasplib.rekey.costing import RateCard
from hasplib.rekey.orders import OrderLine, WorkOrder


@dataclass(frozen=True)
class Stop:
    """One door on the route, with where it is."""

    serial: str
    opening: str
    building: str
    floor: str
    minutes: int

    def describe(self) -> str:
        return f"{self.building} {self.floor}  {self.opening}  {self.serial}  {self.minutes} min"


@dataclass(frozen=True)
class DayPlan:
    """One technician, one day, in the order the doors get walked."""

    technician: str
    stops: tuple[Stop, ...]
    bench_minutes: int
    travel_minutes: int

    @property
    def door_minutes(self) -> int:
        return sum(stop.minutes for stop in self.stops)

    @property
    def total_minutes(self) -> int:
        return self.bench_minutes + self.door_minutes + self.travel_minutes

    @property
    def buildings(self) -> tuple[str, ...]:
        return tuple(sorted({stop.building for stop in self.stops}))

    def lines(self) -> tuple[str, ...]:
        head = f"{self.technician}: {len(self.stops)} doors, {self.total_minutes} minutes"
        return (head, *(stop.describe() for stop in self.stops))


def route(
    lines: Sequence[OrderLine], openings: OpeningSchedule, rates: RateCard
) -> tuple[Stop, ...]:
    """Sort the doors into walking order: building, then floor, then room."""
    stops: list[Stop] = []
    for line in lines:
        opening = openings.opening(line.opening)
        stops.append(
            Stop(
                serial=line.serial,
                opening=line.opening,
                building=opening.building,
                floor=opening.floor,
                minutes=rates.door_minutes_per_cylinder,
            )
        )
    stops.sort(key=lambda stop: (stop.building, stop.floor, stop.opening))
    return tuple(stops)


def plan_day(
    order: WorkOrder,
    openings: OpeningSchedule,
    rates: RateCard,
    technician: str | None = None,
) -> DayPlan:
    """Everything left on one order, in the order it should be walked."""
    who = technician or order.technician
    if not who.strip():
        raise ConflictError("a day is planned for somebody", number=order.number)
    pending = order.pending
    if not pending:
        raise ConflictError("that order has nothing left on it", number=order.number)
    stops = route(pending, openings, rates)
    bench = rates.bench_minutes_per_cylinder * len(stops)
    travel = rates.travel_minutes_per_building * len({stop.building for stop in stops})
    return DayPlan(who, stops, bench, travel)


def fits_in(plan: DayPlan, minutes_available: int) -> bool:
    if minutes_available < 0:
        raise InputError("a day is not negative", minutes=minutes_available)
    return plan.total_minutes <= minutes_available


def split_day(plan: DayPlan, minutes_available: int) -> tuple[DayPlan, DayPlan | None]:
    """Cut a plan at the end of the last building that fits.

    Splitting inside a building is what produces two trips to the same
    address, so the cut goes on a building boundary even when that wastes
    half an hour of the first day.
    """
    if minutes_available <= 0:
        raise InputError("a day has some minutes in it", minutes=minutes_available)
    if fits_in(plan, minutes_available):
        return plan, None

    taken: list[Stop] = []
    for building in plan.buildings:
        candidate = [*taken, *[stop for stop in plan.stops if stop.building == building]]
        if _minutes_for(candidate, plan) > minutes_available and taken:
            break
        taken = candidate
    if not taken:
        taken = [stop for stop in plan.stops if stop.building == plan.buildings[0]]
    left = [stop for stop in plan.stops if stop not in taken]
    first = _rebuild(plan, tuple(taken))
    second = _rebuild(plan, tuple(left)) if left else None
    return first, second


def _minutes_for(stops: Sequence[Stop], plan: DayPlan) -> int:
    per_cylinder = plan.bench_minutes // max(len(plan.stops), 1)
    per_building = plan.travel_minutes // max(len(plan.buildings), 1)
    bench = per_cylinder * len(stops)
    travel = per_building * len({stop.building for stop in stops})
    return bench + travel + sum(stop.minutes for stop in stops)


def _rebuild(plan: DayPlan, stops: tuple[Stop, ...]) -> DayPlan:
    per_cylinder = plan.bench_minutes // max(len(plan.stops), 1)
    per_building = plan.travel_minutes // max(len(plan.buildings), 1)
    return DayPlan(
        technician=plan.technician,
        stops=stops,
        bench_minutes=per_cylinder * len(stops),
        travel_minutes=per_building * len({stop.building for stop in stops}),
    )

"""Phasing commands, which get typed once a year and argued about for months.

Every command here builds the standard academic year rather than taking a
calendar as an argument. A shop that wants different windows edits the year
in code, because a calendar typed at a prompt is a calendar nobody else can
reproduce, and the whole value of this plan is that two people get the same
answer out of it.
"""

from __future__ import annotations

from datetime import date

import click

from hasplib.cli.common import echo_lines, working
from hasplib.rekey.phasing import Calendar
from hasplib.services.phasing import (
    DEFAULT_RATE,
    PhasingService,
    academic_year,
    buildings_with_work,
    orders_to_plan,
)

RATE = click.option(
    "--rate",
    type=click.IntRange(min=1),
    default=DEFAULT_RATE,
    show_default=True,
    help="doors a crew gets through in a day",
)
YEAR = click.option(
    "--year",
    type=click.IntRange(min=2000, max=2100),
    default=None,
    help="the year the summer window falls in",
)


def _calendar(year, rate) -> Calendar:
    """The year to plan against, defaulting to the one we are in."""
    return academic_year(year if year is not None else date.today().year, rate)


@click.group("phasing")
def phasing() -> None:
    """Spreading a rekey across the windows a campus actually has."""


@phasing.command("windows")
@YEAR
@RATE
@click.pass_obj
def windows(shop, year, rate) -> None:
    """The windows in a year, and how many doors fit in each."""
    calendar = _calendar(year, rate)
    echo_lines(window.describe() for window in calendar.in_order())
    click.echo(f"{calendar.total_capacity()} doors in the year")


@phasing.command("buildings")
@click.pass_obj
def buildings(shop) -> None:
    """Which buildings have keyed doors to plan."""
    with working(shop, PhasingService) as service:
        found = buildings_with_work(service.session)
        if not found:
            click.echo("no keyed doors anywhere")
            return
        echo_lines(found)


@phasing.command("orders")
@click.pass_obj
def orders(shop) -> None:
    """Work orders that still need a slot."""
    with working(shop, PhasingService) as service:
        found = orders_to_plan(service.session)
        if not found:
            click.echo("nothing open to plan")
            return
        echo_lines(found)


@phasing.command("building")
@click.argument("code")
@YEAR
@RATE
@click.pass_obj
def one_building(shop, code, year, rate) -> None:
    """The year plan for one building."""
    with working(shop, PhasingService) as service:
        service.calendar = _calendar(year, rate)
        echo_lines(service.sheet(service.doors_in(code)))


@phasing.command("order")
@click.argument("number")
@YEAR
@RATE
@click.pass_obj
def one_order(shop, number, year, rate) -> None:
    """The year plan for the doors on one work order."""
    with working(shop, PhasingService) as service:
        service.calendar = _calendar(year, rate)
        echo_lines(service.sheet(service.doors_on(number)))


@phasing.command("campus")
@YEAR
@RATE
@click.pass_obj
def campus(shop, year, rate) -> None:
    """The year plan for every keyed door there is."""
    with working(shop, PhasingService) as service:
        service.calendar = _calendar(year, rate)
        doors = service.every_keyed_door()
        wanted, room = service.capacity_against(doors)
        click.echo(f"{wanted} doors against {room} the year can take")
        echo_lines(service.sheet(doors))


@phasing.command("day")
@click.argument("day", type=click.DateTime(["%Y-%m-%d"]))
@YEAR
@RATE
@click.pass_obj
def day(shop, day, year, rate) -> None:
    """What a crew is doing on one day."""
    with working(shop, PhasingService) as service:
        service.calendar = _calendar(year, rate)
        echo_lines(service.day_sheet(service.every_keyed_door(), day.date()))


@phasing.command("carry-over")
@YEAR
@RATE
@click.pass_obj
def carry(shop, year, rate) -> None:
    """The doors that do not fit this year, for next year."""
    with working(shop, PhasingService) as service:
        service.calendar = _calendar(year, rate)
        found = service.next_year(service.every_keyed_door())
        if not found:
            click.echo("nothing carries over")
            return
        echo_lines(f"{door.number:<12}{door.restriction.value}" for door in found)

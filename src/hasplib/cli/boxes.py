"""Box commands, typed while standing in front of the box.

The check command is the one that gets used. Somebody walks a corridor, reads
the number off the tag, types it in, and either nothing happens or the box
they are standing at turns out to have been opened. So it prints one line
either way and the line says which of those it was.

The coverage command is the only one here whose output is meant to leave the
building. It is separate from the report command for exactly that reason.
"""

from __future__ import annotations

import click
from sqlalchemy import select

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.control.escrow import BoxState, OpenedBy
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.models.campus import BuildingRow
from hasplib.reports.escrow import (
    coverage_sheet,
    exposure_sheet,
    key_found,
    seal_sheet,
    shop_report,
    walk_sheet,
)
from hasplib.services.escrow import EscrowService

TODAY = day_option("the day it happened", name="when")
OPENERS = click.Choice([item.value for item in OpenedBy])


def _buildings(session) -> list[str]:
    return sorted(session.execute(select(BuildingRow.code)).scalars().all())


@click.group("boxes")
def boxes() -> None:
    """Sealed key boxes, and what has happened to their seals."""


@boxes.command("install")
@click.argument("number")
@click.argument("building")
@click.argument("opening")
@click.argument("symbol")
@click.argument("seal")
@TODAY
@click.pass_obj
def install(shop, number, building, opening, symbol, seal, when) -> None:
    """Put a box on a wall with a key and a seal in it."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        service.install(number, building, opening, KeySymbol.parse(symbol), seal, as_day(when))
        click.echo(f"{number} sealed on {opening} with {symbol}")


@boxes.command("check")
@click.argument("number")
@click.argument("seal")
@TODAY
@click.pass_obj
def check(shop, number, seal, when) -> None:
    """Read the tag. Says nothing much, or says the box has been opened."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        row = service.check(number, seal, as_day(when))
        if row.state == BoxState.SEALED.value:
            click.echo(f"{number} still sealed on {seal}")
        else:
            click.echo(f"{number} has been opened, {row.note}")


@boxes.command("opened")
@click.argument("number")
@click.option("--by", type=OPENERS, default=OpenedBy.FIRE.value)
@click.option("--note", default="")
@TODAY
@click.pass_obj
def opened(shop, number, by, note, when) -> None:
    """Record that somebody opened a box and said so."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        service.report_opened(number, OpenedBy(by), as_day(when), note)
        click.echo(f"{number} open, {by}")


@boxes.command("empty")
@click.argument("number")
@click.option("--note", default="")
@TODAY
@click.pass_obj
def empty(shop, number, note, when) -> None:
    """Record that the key is gone out of an open box."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        service.empty(number, as_day(when), note)
        click.echo(f"{number} empty")


@boxes.command("reseal")
@click.argument("number")
@click.argument("seal")
@click.argument("symbol")
@TODAY
@click.pass_obj
def reseal(shop, number, seal, symbol, when) -> None:
    """New key, new seal, and a new row on the history."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        service.reseal(number, seal, KeySymbol.parse(symbol), as_day(when))
        click.echo(f"{number} sealed on {seal} with {symbol}")


@boxes.command("withdraw")
@click.argument("number")
@click.argument("reason")
@TODAY
@click.pass_obj
def withdraw(shop, number, reason, when) -> None:
    """Take a box off the wall for good."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        service.withdraw(number, as_day(when), reason)
        click.echo(f"{number} off the wall")


@boxes.command("seals")
@click.argument("number")
@TODAY
@click.pass_obj
def seals(shop, number, when) -> None:
    """Every seal a box has carried, oldest first."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(seal_sheet(service.history(number), as_day(when)))


@boxes.command("key-on")
@click.argument("number")
@click.argument("day", type=click.DateTime(["%Y-%m-%d"]))
@click.pass_obj
def key_on(shop, number, day) -> None:
    """Which key was in a box on a day."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        found = service.key_on(number, day.date())
        click.echo(found.render() if found is not None else "nothing was in it")


@boxes.command("open")
@TODAY
@click.pass_obj
def open_boxes(shop, when) -> None:
    """What is not sealed, and which keys are loose because of it."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(exposure_sheet(service.register(), as_day(when)))


@boxes.command("walk")
@TODAY
@click.pass_obj
def walk(shop, when) -> None:
    """The list to carry round the campus."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(walk_sheet(service.register(), as_day(when)))


@boxes.command("coverage")
@click.pass_obj
def coverage(shop) -> None:
    """Where the boxes are. This is the sheet the fire service gets."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(coverage_sheet(service.register(), _buildings(service.session)))


@boxes.command("found")
@click.argument("symbol")
@click.pass_obj
def found(shop, symbol) -> None:
    """Which boxes hold a symbol, for a key somebody handed in."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(key_found(service.register(), symbol))


@boxes.command("report")
@TODAY
@click.pass_obj
def report(shop, when) -> None:
    """The shop's half of the paperwork, which stays in the building."""
    with working(shop, EscrowService, shop.settings.boxes) as service:
        echo_lines(shop_report(service.register(), _buildings(service.session), as_day(when)))

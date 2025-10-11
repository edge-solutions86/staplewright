"""Stock commands, typed at the drawer with a blank already in hand.

Everything here takes the part number first because that is what the person
is holding and reading off. The quantity defaults to one, because the common
case at the drawer is one blank at a time, and making somebody type a one
forty times a day is how a shop stops using the ledger.

The reconcile command is the only one that takes a window, and it defaults to
the year so far, which is the window the annual return wants.
"""

from __future__ import annotations

from datetime import date

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.reports.stock import (
    movement_log,
    outstanding_orders,
    reconcile,
    reconciliation_sheet,
    reorder_sheet,
    restricted_position,
    shrinkage,
    stock_report,
)
from hasplib.services.stock import StockService

TODAY = day_option("the day it moved", name="when")
COUNT = click.option("--count", "quantity", type=click.IntRange(min=1), default=1)
REFERENCE = click.option("--against", "reference", default="", help="a tag, order or job")


@click.group("stock")
def stock() -> None:
    """Blank stock: what is in the drawer and where it went."""


@stock.command("show")
@click.argument("part")
@click.pass_obj
def show(shop, part) -> None:
    """What one part number stands at, both ways of counting it."""
    with working(shop, StockService) as service:
        row = service.blank(part)
        click.echo(f"{row.part_number} {row.manufacturer} {row.chambers} chamber")
        click.echo(f"drawer {row.stock}, ledger {service.ledger_total(part)}")
        if row.restricted:
            click.echo("restricted, every movement needs a reference")


@stock.command("receive")
@click.argument("part")
@COUNT
@REFERENCE
@TODAY
@click.pass_obj
def receive(shop, part, quantity, reference, when) -> None:
    """Book brass into the drawer."""
    with working(shop, StockService) as service:
        service.receive(part, quantity, as_day(when), reference)
        click.echo(f"{part} now {service.on_hand(part)}")


@stock.command("cut")
@click.argument("part")
@COUNT
@REFERENCE
@TODAY
@click.pass_obj
def cut(shop, part, quantity, reference, when) -> None:
    """Take brass out to cut it."""
    with working(shop, StockService) as service:
        service.cut(part, quantity, as_day(when), reference)
        click.echo(f"{part} now {service.on_hand(part)}")


@stock.command("scrap")
@click.argument("part")
@click.argument("reason")
@COUNT
@TODAY
@click.pass_obj
def scrap(shop, part, reason, quantity, when) -> None:
    """Write brass off, with the reason on the line."""
    with working(shop, StockService) as service:
        service.scrap(part, quantity, as_day(when), reason)
        click.echo(f"{part} now {service.on_hand(part)}")


@stock.command("count")
@click.argument("part")
@click.argument("counted", type=click.IntRange(min=0))
@TODAY
@click.pass_obj
def count(shop, part, counted, when) -> None:
    """Write down what is actually in the drawer."""
    with shop.session() as session:
        found = StockService(session).count(part, counted, as_day(when))
        click.echo(found.describe())


@stock.command("log")
@click.argument("part")
@click.pass_obj
def log(shop, part) -> None:
    """Every movement of one part, oldest first."""
    with shop.session() as session:
        echo_lines(movement_log(StockService(session).movements(part)))


@stock.command("reconcile")
@click.argument("part")
@click.option("--from", "since", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--to", "until", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def reconcile_part(shop, part, since, until) -> None:
    """Account for one part between two days, opening to closing."""
    today = date.today()
    opened = since.date() if since is not None else date(today.year, 1, 1)
    closed = until.date() if until is not None else today
    with working(shop, StockService) as service:
        found = reconcile(service.blank(part), service.movements(part), opened, closed)
        echo_lines(reconciliation_sheet(found))


@stock.command("reorder")
@click.pass_obj
def reorder(shop) -> None:
    """What to put on the next order."""
    with shop.session() as session:
        echo_lines(reorder_sheet(StockService(session).blanks.all()))


@stock.command("restricted")
@click.pass_obj
def restricted(shop) -> None:
    """How much restricted brass is in the building."""
    with shop.session() as session:
        echo_lines(restricted_position(StockService(session).blanks.all()))


@stock.command("orders")
@click.pass_obj
def orders(shop) -> None:
    """What is still on order and what is left on it."""
    with shop.session() as session:
        echo_lines(outstanding_orders(StockService(session).orders.all()))


@stock.command("shrinkage")
@click.option("--from", "since", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def losses(shop, since) -> None:
    """Which parts do not account for themselves this year."""
    today = date.today()
    opened = since.date() if since is not None else date(today.year, 1, 1)
    with working(shop, StockService) as service:
        found = [
            reconcile(row, service.movements(row.part_number), opened, today)
            for row in service.blanks.all()
        ]
        echo_lines(shrinkage(found))


@stock.command("report")
@click.option("--from", "since", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def report(shop, since) -> None:
    """The whole pack, in the order somebody signs it."""
    today = date.today()
    opened = since.date() if since is not None else date(today.year, 1, 1)
    with working(shop, StockService) as service:
        blanks = list(service.blanks.all())
        found = [
            reconcile(row, service.movements(row.part_number), opened, today) for row in blanks
        ]
        echo_lines(stock_report(blanks, found, service.orders.all(), opened, today))

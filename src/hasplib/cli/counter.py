"""Commands for the counter: request, approve, issue, take back, chase.

Dates default to today because that is what they are ninety times out of a
hundred, and every one of them can be given explicitly for the afternoon
somebody is working through a fortnight of paper.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.control.notices import NoticeLog, notices_due
from hasplib.persistence.workspace import Workspace
from hasplib.reports.control import (
    department_summary,
    deposit_statement,
    keyholder_statement,
    notice_run,
    overdue_report,
)
from hasplib.services.counter import CounterService

TODAY = day_option(name="when")


@click.group("counter")
def counter() -> None:
    """Requests, keys and deposits."""


@counter.command("series")
@click.pass_obj
def series(shop) -> None:
    """Open the tag and request counters. Once, when the shop is set up."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        service.open_series()
        click.echo("tag and request series opened")


@counter.command("request")
@click.argument("keyholder")
@click.argument("symbol")
@click.argument("building")
@click.argument("reason")
@click.option("--master", is_flag=True, help="this symbol masters something")
@TODAY
@click.pass_obj
def raise_request(shop, keyholder, symbol, building, reason, master, when) -> None:
    """Raise a request for one key."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        row = service.raise_request(keyholder, symbol, building, reason, as_day(when), master)
        click.echo(f"{row.number} raised for {keyholder}, {symbol}")


@counter.command("approve")
@click.argument("number")
@click.argument("signer")
@TODAY
@click.pass_obj
def approve(shop, number, signer, when) -> None:
    """Sign a request off."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        service.approve(number, signer, as_day(when))
        click.echo(f"{number} approved by {signer}")


@counter.command("deny")
@click.argument("number")
@click.argument("signer")
@click.argument("reason")
@TODAY
@click.pass_obj
def deny(shop, number, signer, reason, when) -> None:
    """Turn a request down, with a reason."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        service.deny(number, signer, as_day(when), reason)
        click.echo(f"{number} denied")


@counter.command("issue")
@click.argument("number")
@click.option("--due", type=click.DateTime(["%Y-%m-%d"]), default=None, help="due back")
@click.option("--deposit", type=int, default=None, help="cents, or the standing rate")
@TODAY
@click.pass_obj
def issue(shop, number, due, deposit, when) -> None:
    """Hand the key over and take the deposit."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        key = service.issue(number, as_day(when), due.date() if due else None, deposit)
        click.echo(f"tag {key.tag} issued to {key.keyholder}, deposit {key.deposit_cents}")


@counter.command("return")
@click.argument("tag")
@TODAY
@click.pass_obj
def take_back(shop, tag, when) -> None:
    """A key comes back and the deposit goes with it."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        service.take_back(tag, as_day(when))
        click.echo(f"tag {tag} back")


@counter.command("lost")
@click.argument("tag")
@click.argument("note")
@TODAY
@click.pass_obj
def report_lost(shop, tag, note, when) -> None:
    """A key is gone and the deposit stays here."""
    with working(shop, CounterService, shop.settings.deposits) as service:
        service.report_lost(tag, as_day(when), note)
        click.echo(f"tag {tag} written off")


@counter.command("statement")
@click.argument("keyholder")
@TODAY
@click.pass_obj
def statement(shop, keyholder, when) -> None:
    """The sheet a keyholder signs."""
    with working(shop, Workspace) as space:
        person = space.directory().person(keyholder)
        echo_lines(keyholder_statement(person, space.ledger(), space.deposits(), as_day(when)))


@counter.command("deposits")
@click.argument("keyholder")
@click.pass_obj
def deposits(shop, keyholder) -> None:
    """Every deposit line against one person."""
    with working(shop, Workspace) as space:
        echo_lines(deposit_statement(space.deposits(), keyholder))


@counter.command("department")
@click.argument("department")
@TODAY
@click.pass_obj
def department(shop, department, when) -> None:
    """Who in a department holds what."""
    with working(shop, Workspace) as space:
        echo_lines(department_summary(space.directory(), space.ledger(), department, as_day(when)))


@counter.command("overdue")
@TODAY
@click.pass_obj
def overdue(shop, when) -> None:
    """The chase list."""
    with working(shop, Workspace) as space:
        echo_lines(overdue_report(space.ledger(), as_day(when)))


@counter.command("letters")
@TODAY
@click.pass_obj
def letters(shop, when) -> None:
    """What the notices run would send today."""
    with working(shop, Workspace) as space:
        due = notices_due(space.ledger(), as_day(when), NoticeLog(), shop.settings.notices)
        echo_lines(notice_run(due))

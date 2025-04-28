"""Commands for the counter: request, approve, issue, take back, chase.

Dates default to today because that is what they are ninety times out of a
hundred, and every one of them can be given explicitly for the afternoon
somebody is working through a fortnight of paper.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from hasplib.control.notices import NoticeLog, notices_due
from hasplib.persistence.loaders import load_deposits, load_directory, load_ledger
from hasplib.reports.control import (
    department_summary,
    deposit_statement,
    keyholder_statement,
    notice_run,
    overdue_report,
)
from hasplib.services.counter import CounterService

TODAY = click.option(
    "--on", "when", type=click.DateTime(["%Y-%m-%d"]), default=None, help="the day it happened"
)


def _day(when: datetime | None) -> date:
    return when.date() if when is not None else date.today()


@click.group("counter")
def counter() -> None:
    """Requests, keys and deposits."""


@counter.command("series")
@click.pass_obj
def series(shop) -> None:
    """Open the tag and request counters. Once, when the shop is set up."""
    with shop.session() as session:
        CounterService(session, shop.settings.deposits).open_series()
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
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        row = service.raise_request(keyholder, symbol, building, reason, _day(when), master)
        click.echo(f"{row.number} raised for {keyholder}, {symbol}")


@counter.command("approve")
@click.argument("number")
@click.argument("signer")
@TODAY
@click.pass_obj
def approve(shop, number, signer, when) -> None:
    """Sign a request off."""
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        service.approve(number, signer, _day(when))
        click.echo(f"{number} approved by {signer}")


@counter.command("deny")
@click.argument("number")
@click.argument("signer")
@click.argument("reason")
@TODAY
@click.pass_obj
def deny(shop, number, signer, reason, when) -> None:
    """Turn a request down, with a reason."""
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        service.deny(number, signer, _day(when), reason)
        click.echo(f"{number} denied")


@counter.command("issue")
@click.argument("number")
@click.option("--due", type=click.DateTime(["%Y-%m-%d"]), default=None, help="due back")
@click.option("--deposit", type=int, default=None, help="cents, or the standing rate")
@TODAY
@click.pass_obj
def issue(shop, number, due, deposit, when) -> None:
    """Hand the key over and take the deposit."""
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        key = service.issue(number, _day(when), due.date() if due else None, deposit)
        click.echo(f"tag {key.tag} issued to {key.keyholder}, deposit {key.deposit_cents}")


@counter.command("return")
@click.argument("tag")
@TODAY
@click.pass_obj
def take_back(shop, tag, when) -> None:
    """A key comes back and the deposit goes with it."""
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        service.take_back(tag, _day(when))
        click.echo(f"tag {tag} back")


@counter.command("lost")
@click.argument("tag")
@click.argument("note")
@TODAY
@click.pass_obj
def report_lost(shop, tag, note, when) -> None:
    """A key is gone and the deposit stays here."""
    with shop.session() as session:
        service = CounterService(session, shop.settings.deposits)
        service.report_lost(tag, _day(when), note)
        click.echo(f"tag {tag} written off")


@counter.command("statement")
@click.argument("keyholder")
@TODAY
@click.pass_obj
def statement(shop, keyholder, when) -> None:
    """The sheet a keyholder signs."""
    with shop.session() as session:
        directory = load_directory(session)
        ledger = load_ledger(session)
        book = load_deposits(session)
        for line in keyholder_statement(directory.person(keyholder), ledger, book, _day(when)):
            click.echo(line)


@counter.command("deposits")
@click.argument("keyholder")
@click.pass_obj
def deposits(shop, keyholder) -> None:
    """Every deposit line against one person."""
    with shop.session() as session:
        for line in deposit_statement(load_deposits(session), keyholder):
            click.echo(line)


@counter.command("department")
@click.argument("department")
@TODAY
@click.pass_obj
def department(shop, department, when) -> None:
    """Who in a department holds what."""
    with shop.session() as session:
        directory = load_directory(session)
        ledger = load_ledger(session)
        for line in department_summary(directory, ledger, department, _day(when)):
            click.echo(line)


@counter.command("overdue")
@TODAY
@click.pass_obj
def overdue(shop, when) -> None:
    """The chase list."""
    with shop.session() as session:
        for line in overdue_report(load_ledger(session), _day(when)):
            click.echo(line)


@counter.command("letters")
@TODAY
@click.pass_obj
def letters(shop, when) -> None:
    """What the notices run would send today."""
    with shop.session() as session:
        ledger = load_ledger(session)
        due = notices_due(ledger, _day(when), NoticeLog(), shop.settings.notices)
        for line in notice_run(due):
            click.echo(line)

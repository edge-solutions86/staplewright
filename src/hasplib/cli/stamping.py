"""Stamping commands, one of which is the only reason anybody opens this group.

``identify`` is that one. Somebody has walked in with a key they found in a
car park and there is a number on it. Everything else here is setup for the
morning that happens.

The whole book is behind a command that says what it is, and it prints a
warning line before it prints anything else, because a terminal history is
not where that table should end up.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.keys.symbol import KeySymbol
from hasplib.services.stamping import StampingService, issued_symbols, unstamped_out

TODAY = day_option("the day it was stamped", name="when")
BY = click.option("--by", default="", help="who did it")


@click.group("stamping")
def stamping() -> None:
    """What goes on the brass, and the table that reads it back."""


@stamping.command("identify")
@click.argument("code")
@click.pass_obj
def identify(shop, code) -> None:
    """A number off a found key, and what it means."""
    with working(shop, StampingService) as service:
        click.echo(service.identify(code))


@stamping.command("code")
@click.argument("symbol")
@click.pass_obj
def code_for(shop, symbol) -> None:
    """The number a symbol is stamped with."""
    with working(shop, StampingService) as service:
        click.echo(service.code_for(KeySymbol.parse(symbol)))


@stamping.command("assign")
@click.argument("symbol")
@BY
@TODAY
@click.pass_obj
def assign(shop, symbol, by, when) -> None:
    """Give a symbol a blind code, or print the one it has."""
    with working(shop, StampingService) as service:
        row = service.assign(KeySymbol.parse(symbol), as_day(when), by)
        click.echo(f"{row.symbol} is {row.code}")


@stamping.command("key")
@click.argument("tag")
@BY
@TODAY
@click.pass_obj
def one_key(shop, tag, by, when) -> None:
    """What goes on one key."""
    with working(shop, StampingService) as service:
        click.echo(service.stamp_key(tag, as_day(when), by).describe())


@stamping.command("run")
@click.argument("tags", nargs=-1, required=True)
@BY
@TODAY
@click.pass_obj
def run(shop, tags, by, when) -> None:
    """A sheet for the stamping machine."""
    with working(shop, StampingService) as service:
        echo_lines(service.sheet(list(tags), as_day(when), by))


@stamping.command("everything")
@BY
@TODAY
@click.pass_obj
def everything(shop, by, when) -> None:
    """The stamping for every key that is out, for a re-marking run."""
    with working(shop, StampingService) as service:
        found = service.everything_out(as_day(when), by)
        echo_lines(stamp.describe() for stamp in found)


@stamping.command("blank")
@TODAY
@click.pass_obj
def blank(shop, when) -> None:
    """Keys this policy would leave unmarked, which nobody could identify."""
    with working(shop, StampingService) as service:
        found = unstamped_out(service.session, service.policy)
        if not found:
            click.echo("every key out carries something")
            return
        echo_lines(found)


@stamping.command("symbols")
@click.pass_obj
def symbols(shop) -> None:
    """Every symbol currently out, which is what needs a code."""
    with working(shop, StampingService) as service:
        echo_lines(issued_symbols(service.session))


@stamping.command("retire")
@click.argument("symbol")
@click.pass_obj
def retire(shop, symbol) -> None:
    """Take a symbol out of the book, once nobody carries it."""
    with working(shop, StampingService) as service:
        service.retire_code(KeySymbol.parse(symbol))
        click.echo(f"{symbol} is out of the book")


@stamping.command("the-whole-book")
@click.confirmation_option(prompt="print the whole blind code table?")
@click.pass_obj
def whole_book(shop) -> None:
    """Every pair in the table. This is the thing not to leave on a screen."""
    with working(shop, StampingService) as service:
        click.echo("this is the table that turns every stamped number into a symbol")
        echo_lines(f"{symbol:<10}{code}" for symbol, code in service.the_whole_book())

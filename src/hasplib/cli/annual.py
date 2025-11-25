"""Return commands, typed once a year with somebody from risk in the room.

``take`` and ``file`` are separate commands because they are separate
decisions. Everybody runs the numbers first, argues about two of them, fixes
whatever was wrong in the ledger, and runs them again. Filing is the last
thing that happens and it happens once.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.services.annual import AnnualService

TODAY = day_option("the day to measure against", name="when")


@click.group("return")
def annual() -> None:
    """The annual key control return."""


@annual.command("take")
@TODAY
@click.pass_obj
def take(shop, when) -> None:
    """Run the numbers without committing to them."""
    with working(shop, AnnualService) as service:
        echo_lines(service.sheet(as_day(when)))


@annual.command("file")
@click.option("--by", "filed_by", default="", help="who signed it off")
@click.option("--note", default="")
@TODAY
@click.pass_obj
def file_it(shop, filed_by, note, when) -> None:
    """Write this year down, once."""
    with working(shop, AnnualService) as service:
        row = service.file_return(as_day(when), filed_by, note)
        click.echo(f"{row.year} filed: {row.keys_out} out, {row.unaccounted} unaccounted")


@annual.command("withdraw")
@click.argument("year", type=int)
@click.argument("reason")
@click.pass_obj
def withdraw(shop, year, reason) -> None:
    """Mark a filed year as not to be used, with the reason on it."""
    with working(shop, AnnualService) as service:
        service.withdraw(year, reason)
        click.echo(f"{year} withdrawn")


@annual.command("trend")
@click.pass_obj
def trend(shop) -> None:
    """Every filed year side by side."""
    with working(shop, AnnualService) as service:
        echo_lines(service.trend())


@annual.command("masters")
@click.pass_obj
def masters(shop) -> None:
    """Every master key that is out, and who has it."""
    with working(shop, AnnualService) as service:
        echo_lines(service.masters())


@annual.command("unaccounted")
@TODAY
@click.pass_obj
def unaccounted(shop, when) -> None:
    """The keys nobody can produce."""
    with working(shop, AnnualService) as service:
        echo_lines(service.unaccounted(as_day(when)))


@annual.command("concentration")
@click.option("--top", type=click.IntRange(min=1), default=5, show_default=True)
@click.pass_obj
def concentration(shop, top) -> None:
    """Who holds the most keys, which is where one loss hurts most."""
    with working(shop, AnnualService) as service:
        echo_lines(f"{name:<18}{count}" for name, count in service.concentration(top))


@annual.command("movement")
@TODAY
@click.pass_obj
def movement(shop, when) -> None:
    """What changed against the last filed year."""
    with working(shop, AnnualService) as service:
        echo_lines(service.against_last_year(as_day(when)))

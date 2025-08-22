"""Keyway commands, which is where somebody finds out about a patent.

These exist because the patent question is one nobody asks on purpose. It
comes up when a locksmith notices a blank for sale on a website that should
not have it, and the first thing they want is a straight answer about which
sections have gone and what is sitting on them.

So the default command is the risk report across every family. Everything
else here is a way of narrowing that down, and none of it needs an argument
to be useful.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option
from hasplib.services.keyways import KeywayService
from hasplib.spec.patents import PLANNING_YEARS

TODAY = day_option("the day to read the patents against", name="when")


@click.group("keyways")
def keyways() -> None:
    """Keyway families, sections, and where the patents stand."""


@keyways.command("families")
@click.pass_obj
def families(shop) -> None:
    """List the families the shop holds, with how many sections each has."""
    with shop.session() as session:
        service = KeywayService(session)
        found = service.families()
        if not found:
            click.echo("no keyway families stored")
            return
        for name in found:
            click.echo(f"{name}  {len(service.family(name))} sections")


@keyways.command("sections")
@click.argument("family")
@TODAY
@click.pass_obj
def sections(shop, family, when) -> None:
    """Every section in a family, soonest patent expiry first."""
    today = as_day(when)
    with shop.session() as session:
        for item in KeywayService(session).exposures(family):
            click.echo(item.describe(today))


@keyways.command("tree")
@click.argument("family")
@click.pass_obj
def tree(shop, family) -> None:
    """The family as a tree, which is how the catalogue draws it."""
    with shop.session() as session:
        found = KeywayService(session).family(family)
        for code in found.codes():
            click.echo(f"{'  ' * found.depth_of(code)}{code}")


@keyways.command("patents")
@click.argument("family")
@TODAY
@click.pass_obj
def patents(shop, family, when) -> None:
    """Where one family stands: what is gone, what is going, what is next."""
    with shop.session() as session:
        for line in KeywayService(session).report(family, as_day(when)):
            click.echo(line)


@keyways.command("risk")
@TODAY
@click.pass_obj
def risk(shop, when) -> None:
    """Sections the shop keys on that have already gone open."""
    with shop.session() as session:
        for line in KeywayService(session).risk_report(as_day(when)):
            click.echo(line)


@keyways.command("check")
@click.argument("family")
@click.argument("section")
@click.option(
    "--years",
    type=click.IntRange(min=0),
    default=PLANNING_YEARS,
    show_default=True,
    help="how long the section has to stay restricted",
)
@TODAY
@click.pass_obj
def check(shop, family, section, years, when) -> None:
    """Say whether a new system may be laid down on a section."""
    today = as_day(when)
    with shop.session() as session:
        service = KeywayService(session)
        service.check_section(family, section, today, within_years=years)
        click.echo(f"{section} is good for {years} years")


@keyways.command("carrying")
@click.argument("family")
@click.argument("section")
@click.pass_obj
def carrying(shop, family, section) -> None:
    """Which live systems sit on one section."""
    with shop.session() as session:
        found = KeywayService(session).systems_on(family, section)
        if not found:
            click.echo(f"nothing is keyed on {section}")
            return
        for name in found:
            click.echo(name)

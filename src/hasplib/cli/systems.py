"""Commands for keying systems: draw, cut, print, review.

The cut command is the one that gets used forty times an afternoon while a
system is being laid out, so it takes the system, the symbol and the bitting
as three bare arguments in that order and prints one line back.
"""

from __future__ import annotations

import click

from hasplib.cli.common import echo_lines, working
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.reports.schedule import (
    bitting_list,
    open_symbols_report,
    schedule_report,
    tree_report,
)
from hasplib.services.systems import SystemService


@click.group("system")
def systems() -> None:
    """Keying systems."""


@systems.command("open")
@click.argument("name")
@click.argument("chart_id", type=int)
@click.argument("family")
@click.argument("section")
@click.option("--step", default=2, show_default=True, help="progression step")
@click.pass_obj
def open_system(shop, name: str, chart_id: int, family: str, section: str, step: int) -> None:
    """Open a keying system on a chart and a keyway family."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        row = service.open_system(name, chart_id, family, section, step)
        click.echo(f"{row.name} opened on chart {row.chart_id}, step {row.progression_step}")


@systems.command("draw")
@click.argument("name")
@click.argument("symbol")
@click.option("--section", default=None, help="keyway section, or the one above")
@click.pass_obj
def draw(shop, name: str, symbol: str, section: str | None) -> None:
    """Put a symbol on the schedule without cutting it."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        row = service.draw(name, KeySymbol.parse(symbol), section)
        click.echo(f"{row.symbol.render()} drawn on {row.section}")


@systems.command("cut")
@click.argument("name")
@click.argument("symbol")
@click.argument("bitting")
@click.pass_obj
def cut(shop, name: str, symbol: str, bitting: str) -> None:
    """Cut a symbol to a bitting."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        service.cut(name, KeySymbol.parse(symbol), Bitting.parse(bitting))
        click.echo(f"{symbol} cut {bitting}")


@systems.command("uncut")
@click.argument("name")
@click.argument("symbol")
@click.pass_obj
def uncut(shop, name: str, symbol: str) -> None:
    """Take the cuts off a symbol and leave it on the schedule."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        service.uncut(name, KeySymbol.parse(symbol))
        click.echo(f"{symbol} back on the open list")


@systems.command("schedule")
@click.argument("name")
@click.option("--bittings", is_flag=True, help="sort by cut instead of by symbol")
@click.option("--tree", is_flag=True, help="the hierarchy alone")
@click.pass_obj
def schedule(shop, name: str, bittings: bool, tree: bool) -> None:
    """Print the keying schedule."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        drawn = service.schedule(name)
        if bittings:
            lines = bitting_list(drawn)
        elif tree:
            lines = tree_report(drawn)
        else:
            lines = schedule_report(drawn, name)
        echo_lines(lines)


@systems.command("open-symbols")
@click.argument("name")
@click.pass_obj
def open_symbols(shop, name: str) -> None:
    """What is drawn and not cut."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        echo_lines(open_symbols_report(service.schedule(name)))


@systems.command("review")
@click.argument("name")
@click.pass_obj
def review(shop, name: str) -> None:
    """Everything wrong with a system, worst first."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        findings = service.review(name)
        if not findings:
            click.echo("nothing to report")
            return
        for finding in findings:
            click.echo(f"{finding.severity.value:<9}{finding.code:<16}{finding.message}")


@systems.command("archive")
@click.argument("name")
@click.pass_obj
def archive(shop, name: str) -> None:
    """Close a system so nothing else gets drawn on it."""
    with working(shop, SystemService, shop.settings.cutting) as service:
        service.archive(name)
        click.echo(f"{name} archived")

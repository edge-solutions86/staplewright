"""Export commands, which end with somebody dragging a file into a system.

Everything writes to standard output by default rather than to a file, so a
person can look at it before it goes anywhere, and a pipe does the rest. The
one command that writes files is the bulk one, because dropping four files in
a shared folder is the whole job it exists for.
"""

from __future__ import annotations

from datetime import date
from pathlib import Path

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.services.exports import FORMATS, ExportService

TODAY = day_option("the day to stamp on the file", name="when")
SHAPE = click.option(
    "--as",
    "shape",
    type=click.Choice(FORMATS),
    default="csv",
    show_default=True,
    help="the format to write",
)
SYSTEM = click.option("--system", default="", help="which keying system, for a schedule")


@click.group("export")
def export() -> None:
    """Tables out of the database, for whatever is asking for them."""


@export.command("list")
@click.pass_obj
def listing(shop) -> None:
    """What can be exported."""
    with working(shop, ExportService) as service:
        echo_lines(service.names())


@export.command("table")
@click.argument("name")
@SHAPE
@SYSTEM
@click.pass_obj
def one_table(shop, name, shape, system) -> None:
    """One table, written to standard output."""
    with working(shop, ExportService) as service:
        click.echo(service.text(name, shape, system), nl=False)


@export.command("columns")
@click.argument("name")
@click.pass_obj
def columns(shop, name) -> None:
    """The columns one export has, which is what a mapping needs."""
    with working(shop, ExportService) as service:
        wanted, _ = service.rows_for(
            name, service.systems_to_export()[0] if name == "schedule" else ""
        )
        echo_lines(wanted.header)


@export.command("all")
@click.argument("folder", type=click.Path(file_okay=False, path_type=Path))
@SHAPE
@TODAY
@click.pass_obj
def everything(shop, folder, shape, when) -> None:
    """Drop every table into a folder, stamped with the day."""
    day = as_day(when)
    folder.mkdir(parents=True, exist_ok=True)
    with working(shop, ExportService) as service:
        for name, text in service.every_table(day, shape).items():
            path = folder / name
            path.write_text(text, encoding="utf-8")
            click.echo(str(path))


@export.command("schedule")
@click.argument("system")
@SHAPE
@TODAY
@click.option("--to", "target", type=click.Path(dir_okay=False, path_type=Path), default=None)
@click.pass_obj
def schedule(shop, system, shape, when, target) -> None:
    """One system schedule, to standard output or to a file."""
    with working(shop, ExportService) as service:
        text = service.text("schedule", shape, system)
        if target is None:
            click.echo(text, nl=False)
            return
        target.write_text(text, encoding="utf-8")
        click.echo(str(target))


@export.command("name")
@click.argument("table")
@SHAPE
@TODAY
@click.pass_obj
def name_for(shop, table, shape, when) -> None:
    """What the file would be called, without writing it."""
    with working(shop, ExportService) as service:
        click.echo(service.filename(table, as_day(when) or date.today(), shape))

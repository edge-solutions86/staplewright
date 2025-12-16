"""Import commands, which somebody runs holding a file they did not make.

Every one of them dry runs by default. A load that writes on the first
invocation is a load somebody runs against production while working out what
the columns are called, and the person handing over the file is never the
person who knows what is in it.
"""

from __future__ import annotations

from pathlib import Path

import click

from hasplib.cli.common import echo_lines, working
from hasplib.services.imports import ImportService

WRITE = click.option(
    "--write",
    is_flag=True,
    help="actually load it, rather than saying what would load",
)


@click.group("import")
def bring_in() -> None:
    """Files into the database, nothing written until the whole file reads."""


@bring_in.command("kinds")
@click.pass_obj
def kinds(shop) -> None:
    """What can be loaded, and the columns each one wants."""
    with working(shop, ImportService) as service:
        for kind in service.kinds():
            click.echo(f"{kind:<14}{', '.join(service.columns_for(kind))}")


@bring_in.command("columns")
@click.argument("kind")
@click.pass_obj
def columns(shop, kind) -> None:
    """The columns one kind of file has to have."""
    with working(shop, ImportService) as service:
        echo_lines(service.columns_for(kind))


@bring_in.command("load")
@click.argument("kind")
@click.argument("path", type=click.Path(dir_okay=False, exists=True, path_type=Path))
@WRITE
@click.pass_obj
def load(shop, kind, path, write) -> None:
    """Read a file and say what it would do, or do it."""
    text = path.read_text(encoding="utf-8")
    with working(shop, ImportService) as service:
        found = service.load(kind, text, dry_run=not write)
        echo_lines(found.describe())
        if not found.is_clean:
            raise SystemExit(1)


@bring_in.command("check")
@click.argument("kind")
@click.argument("path", type=click.Path(dir_okay=False, exists=True, path_type=Path))
@click.pass_obj
def check(shop, kind, path) -> None:
    """Read a file and report only the problems."""
    text = path.read_text(encoding="utf-8")
    with working(shop, ImportService) as service:
        found = service.load(kind, text, dry_run=True)
        if found.is_clean:
            click.echo(f"{found.written} rows read, nothing to fix")
            return
        echo_lines(found.describe()[1:])
        raise SystemExit(1)

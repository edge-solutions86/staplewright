"""Finding a report, which is the first thing anybody needs to do.

``hasplib reports`` with nothing after it prints the whole index. That is the
command to type when somebody has described a page and nobody can remember
whether it exists.
"""

from __future__ import annotations

import click

from hasplib.cli.common import echo_lines
from hasplib.reports.index import (
    every_module,
    index_sheet,
    page,
    reader_sheet,
    readers,
    undocumented,
)


@click.group("reports", invoke_without_command=True)
@click.pass_context
def reports(context: click.Context) -> None:
    """Every page this library prints."""
    if context.invoked_subcommand is None:
        echo_lines(index_sheet())


@reports.command("module")
@click.argument("name")
def by_module(name) -> None:
    """The pages one module prints."""
    echo_lines(index_sheet(name))


@reports.command("modules")
def modules() -> None:
    """The report modules there are."""
    echo_lines(every_module())


@reports.command("for")
@click.argument("reader")
def by_reader(reader) -> None:
    """The pages one kind of person asks for."""
    echo_lines(reader_sheet(reader))


@reports.command("readers")
def who() -> None:
    """Who asks for a report, which is how people arrive at one."""
    echo_lines(readers())


@reports.command("find")
@click.argument("name")
def find(name) -> None:
    """Where one page lives, so it can be imported."""
    found = page(name)
    click.echo(found.path)
    click.echo(f"{found.answers}, asked for by {found.asked_by}")


@reports.command("unlisted")
def unlisted() -> None:
    """Report functions that are not on the index, which should be none."""
    found = {name: undocumented(name) for name in every_module()}
    missing = {name: items for name, items in found.items() if items}
    if not missing:
        click.echo("every report is on the index")
        return
    for name, items in missing.items():
        click.echo(f"{name}: {', '.join(items)}")
    raise SystemExit(1)

"""The commands that are about the database rather than about keys.

Building the schema, filling it with an example, listing the tables and
running the self check. None of them belong to a group because none of them
are about one part of the shop, and they were sitting in the entry point
module where they had to be imported before every group in the library.
"""

from __future__ import annotations

from datetime import date
from typing import Any

import click

from hasplib.cli.common import echo_lines
from hasplib.health import exit_code, health_report, run_checks
from hasplib.persistence.base import table_names
from hasplib.persistence.engine import create_schema
from hasplib.persistence.seed import seed, summary

DAY = click.option("--on", "when", type=click.DateTime(["%Y-%m-%d"]), default=None)


def _day(when: Any) -> date:
    return when.date() if when is not None else date.today()


@click.command("build")
@click.pass_obj
def build(shop: Any) -> None:
    """Create the tables. Migrations do this in production."""
    create_schema(shop.engine)
    click.echo(f"schema built at {shop.url}")


@click.command("settings")
@click.pass_obj
def settings(shop: Any) -> None:
    """Print the numbers this shop is running on."""
    echo_lines(shop.settings.describe())


@click.command("tables")
@click.pass_obj
def tables(shop: Any) -> None:
    """List the tables, which is the quickest check that a url is right."""
    echo_lines(table_names())


@click.command("demo")
@DAY
@click.pass_obj
def demo(shop: Any, when) -> None:
    """Build the tables and fill them with a worked example campus."""
    create_schema(shop.engine)
    with shop.session() as session:
        seed(session, _day(when))
    click.echo(f"demo campus written to {shop.url}")
    echo_lines(f"  {line}" for line in summary())


@click.command("check")
@DAY
@click.option("--quiet", is_flag=True, help="print nothing, just set the exit code")
@click.pass_obj
def check(shop: Any, when, quiet: bool) -> None:
    """Run the self checks over the database and say what is wrong.

    Exits one on a fault and nought on anything else, so a nightly run can be
    wired to something that shouts without anybody parsing the output.
    """
    today = _day(when)
    with shop.session() as session:
        findings = run_checks(session, today)
        if not quiet:
            echo_lines(health_report(session, today))
    raise SystemExit(exit_code(findings))


COMMANDS = (build, settings, tables, demo, check)
"""The commands that hang off the root rather than off a group."""

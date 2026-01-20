"""Release commands, for the afternoon somebody upgrades a shop two versions.

``upgrade`` is the one worth having. It says which alembic revisions the jump
crosses and which notes to read before starting, and both of those used to be
worked out by reading a folder and hoping.
"""

from __future__ import annotations

import click

from hasplib import __version__
from hasplib.cli.common import echo_lines
from hasplib.release import (
    describe_current,
    every_migration,
    history_sheet,
    matches_package,
    upgrade_sheet,
)


@click.group("release")
def release() -> None:
    """Versions, what each one changed, and what an upgrade crosses."""


@release.command("version")
def version() -> None:
    """This version and what it settled."""
    echo_lines(describe_current())
    if not matches_package(__version__):
        click.echo(f"the package says {__version__}, which is not the latest release")


@release.command("history")
def history() -> None:
    """Every release, oldest first."""
    echo_lines(history_sheet())


@release.command("upgrade")
@click.argument("older")
@click.option("--to", "newer", default="", help="the version being upgraded to")
def upgrade(older, newer) -> None:
    """What an upgrade crosses, and what to read before starting it."""
    echo_lines(upgrade_sheet(older, newer))


@release.command("migrations")
def migrations() -> None:
    """Every alembic revision any release has shipped, oldest first."""
    echo_lines(every_migration())

"""The command line, which is how the bench uses any of this.

Nobody in the shop opens a browser to look up a bitting. They have a terminal
on the wall by the key machine and they type four characters and a symbol, so
the commands are short, the output is plain text with no colour, and every
one of them prints something a person can read out over a telephone.

A refusal comes back as one line on standard error and an exit code of one.
Not a traceback: a traceback in front of somebody holding a key blank is a
support call.

The groups are attached from a list at the bottom. They used to be thirteen
imports at the foot of the file, each with a noqa on it because they had to
come after the group they attach to, and two of them had been imported and
never attached.
"""

from __future__ import annotations

import sys
from collections.abc import Iterator, Sequence
from contextlib import contextmanager

import click
from sqlalchemy.orm import Session

from hasplib import __version__
from hasplib.cli import service
from hasplib.cli.annual import annual
from hasplib.cli.boxes import boxes
from hasplib.cli.control import control
from hasplib.cli.counter import counter
from hasplib.cli.doors import doors
from hasplib.cli.export import export
from hasplib.cli.imports import bring_in
from hasplib.cli.keyways import keyways
from hasplib.cli.phasing import phasing
from hasplib.cli.rekey import rekey
from hasplib.cli.stamping import stamping
from hasplib.cli.stock import stock
from hasplib.cli.systems import systems
from hasplib.errors import HaspError
from hasplib.persistence.engine import build_engine, build_sessions
from hasplib.settings import from_env

DEFAULT_URL = "sqlite+pysqlite:///hasplib.sqlite3"

GROUPS: Sequence[click.Group] = (
    boxes,
    control,
    counter,
    doors,
    export,
    bring_in,
    keyways,
    phasing,
    rekey,
    annual,
    stamping,
    stock,
    systems,
)
"""Every command group, in the order they appear in the help.

Sorted by the name a person types rather than by the name of the variable,
because two of them do not match: the handover group is imported as
``bring_in`` and typed as ``import``, and the annual return group is imported
as ``annual`` and typed as ``return``. Both of those are keywords, which is
why they are not called what they are called.
"""


class Shop:
    """What every command needs: a database and the settings."""

    def __init__(self, url: str) -> None:
        self.url = url
        self.engine = build_engine(url)
        self.sessions = build_sessions(self.engine)
        self.settings = from_env()

    @contextmanager
    def session(self) -> Iterator[Session]:
        session = self.sessions()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()


def fail(error: HaspError) -> None:
    """One line, on standard error, and an exit code the shell can read."""
    click.echo(f"refused: {error}", err=True)
    raise SystemExit(1)


@click.group()
@click.option("--database", default=DEFAULT_URL, envvar="HASPLIB_DATABASE", show_default=True)
@click.version_option(__version__)
@click.pass_context
def cli(context: click.Context, database: str) -> None:
    """Key control and master key system design for the lock shop."""
    context.obj = Shop(database)


for command in service.COMMANDS:
    cli.add_command(command)

for group in GROUPS:
    cli.add_command(group)


def run(argv: list[str] | None = None) -> int:
    """The entry point, with refusals turned into one readable line."""
    try:
        cli.main(args=argv, standalone_mode=False)
    except HaspError as error:
        fail(error)
    except click.ClickException as error:
        error.show()
        return error.exit_code
    except click.Abort:
        click.echo("stopped", err=True)
        return 1
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(run())

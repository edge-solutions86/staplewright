"""The command line, which is how the bench uses any of this.

Nobody in the shop opens a browser to look up a bitting. They have a terminal
on the wall by the key machine and they type four characters and a symbol, so
the commands are short, the output is plain text with no colour, and every
one of them prints something a person can read out over a telephone.

A refusal comes back as one line on standard error and an exit code of one.
Not a traceback: a traceback in front of somebody holding a key blank is a
support call.
"""

from __future__ import annotations

import sys
from collections.abc import Iterator
from contextlib import contextmanager

import click
from sqlalchemy.orm import Session

from hasplib import __version__
from hasplib.errors import HaspError
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.settings import from_env

DEFAULT_URL = "sqlite+pysqlite:///hasplib.sqlite3"


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


@cli.command("build")
@click.pass_obj
def build(shop: Shop) -> None:
    """Create the tables. Migrations do this in production."""
    create_schema(shop.engine)
    click.echo(f"schema built at {shop.url}")


@cli.command("settings")
@click.pass_obj
def settings(shop: Shop) -> None:
    """Print the numbers this shop is running on."""
    for line in shop.settings.describe():
        click.echo(line)


@cli.command("tables")
@click.pass_obj
def tables(shop: Shop) -> None:
    """List the tables, which is the quickest check that a url is right."""
    from hasplib.persistence.base import table_names

    for name in table_names():
        click.echo(name)


from hasplib.cli.counter import counter  # noqa: E402
from hasplib.cli.systems import systems  # noqa: E402

cli.add_command(systems)
cli.add_command(counter)


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

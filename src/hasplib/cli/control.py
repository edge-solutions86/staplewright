"""Control key commands, and the one report nobody has ever run.

``crossings`` is that report. It takes about a second and it tells a shop
whether a core somebody pulls in one building can be put back in another
building on a different control key. On a campus that has bought locks from
two suppliers the answer is always yes, and the fix is a project.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.keys.bitting import Bitting
from hasplib.services.interchange import InterchangeService
from hasplib.spec.interchange import CoreFormat, HousingKind

TODAY = day_option("the day it was cut", name="when")
FORMAT = click.option(
    "--format",
    "core_format",
    type=click.Choice([item.name.lower() for item in CoreFormat]),
    default="sfic_a2",
    show_default=True,
    help="which family of housing it drops into",
)
HOUSING = click.option(
    "--housing",
    type=click.Choice([item.name.lower() for item in HousingKind]),
    default="mortise",
    show_default=True,
)


@click.group("control")
def control() -> None:
    """Control keys and interchangeable cores."""


@control.command("cut")
@click.argument("code")
@click.argument("section")
@click.argument("bitting")
@FORMAT
@click.option("--to", "holder", default="", help="who it goes to")
@TODAY
@click.pass_obj
def cut(shop, code, section, bitting, core_format, holder, when) -> None:
    """Cut a control key onto a section."""
    with working(shop, InterchangeService) as service:
        service.cut_key(
            code,
            section,
            CoreFormat[core_format.upper()],
            Bitting.parse(bitting),
            as_day(when),
            holder,
        )
        click.echo(f"{code} cut on {section}")


@control.command("hand-to")
@click.argument("code")
@click.argument("holder")
@click.pass_obj
def hand_to(shop, code, holder) -> None:
    """Record who has a control key."""
    with working(shop, InterchangeService) as service:
        service.hand_to(code, holder)
        click.echo(f"{code} is with {holder}")


@control.command("withdraw")
@click.argument("code")
@TODAY
@click.pass_obj
def withdraw(shop, code, when) -> None:
    """Take a control key out of service."""
    with working(shop, InterchangeService) as service:
        service.withdraw(code, as_day(when))
        click.echo(f"{code} withdrawn")


@control.command("core")
@click.argument("serial")
@click.argument("code")
@FORMAT
@HOUSING
@click.option("--in", "opening", default="", help="the door it is already in")
@click.pass_obj
def register_core(shop, serial, code, core_format, housing, opening) -> None:
    """Put a core on the register."""
    with working(shop, InterchangeService) as service:
        service.register_core(
            serial,
            CoreFormat[core_format.upper()],
            code,
            HousingKind[housing.upper()],
            opening,
        )
        click.echo(f"{serial} on the register, pulled by {code}")


@control.command("install")
@click.argument("serial")
@click.argument("opening")
@click.pass_obj
def install(shop, serial, opening) -> None:
    """Put a core into a housing."""
    with working(shop, InterchangeService) as service:
        service.install(serial, opening)
        click.echo(f"{serial} is in {opening}")


@control.command("pull")
@click.argument("serial")
@click.pass_obj
def pull(shop, serial) -> None:
    """Take a core out, which is what a control key is for."""
    with working(shop, InterchangeService) as service:
        service.pull(serial)
        click.echo(f"{serial} is off the wall")


@control.command("keys")
@click.pass_obj
def keys(shop) -> None:
    """Every control key, live first."""
    with working(shop, InterchangeService) as service:
        found = service.register()
        if not len(found):
            click.echo("no control keys on the register")
            return
        echo_lines(key.describe() for key in found)


@control.command("holders")
@click.pass_obj
def holders(shop) -> None:
    """Who holds a control key. The shortest page in the building."""
    with working(shop, InterchangeService) as service:
        echo_lines(service.holders())


@control.command("cores")
@click.argument("code", required=False, default="")
@click.pass_obj
def cores(shop, code) -> None:
    """Every core, or every core one control key pulls."""
    with working(shop, InterchangeService) as service:
        rows = service.cores.pulled_by(code) if code else service.cores.all()
        if not rows:
            click.echo("no cores on the register")
            return
        echo_lines(
            f"{row.serial:<10}{row.control_code:<8}{row.housing:<16}"
            f"{row.installed_at or 'on the shelf'}"
            for row in rows
        )


@control.command("crossings")
@click.pass_obj
def crossings(shop) -> None:
    """Where a core could be moved across a control key."""
    with working(shop, InterchangeService) as service:
        echo_lines(service.report())

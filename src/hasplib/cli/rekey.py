"""Commands for the rekey side: report, assess, plan, book, print the packet.

The order these appear in is the order they get used on the morning of an
incident, and the plan command is the one that gets run three times while
somebody argues about the scope. It writes nothing, so running it three times
costs nothing but the reading.
"""

from __future__ import annotations

import click

from hasplib.cli.common import as_day, day_option, echo_lines, working
from hasplib.persistence.loaders import load_coverage
from hasplib.rekey.compromise import Cause
from hasplib.rekey.costing import estimate
from hasplib.rekey.orders import OrderLine, WorkOrder
from hasplib.reports.rekey import cost_sheet, incident_sheet, order_packet, scope_sheet
from hasplib.services.incidents import IncidentService

TODAY = day_option(name="when")


@click.group("rekey")
def rekey() -> None:
    """Missing keys and the work they cause."""


@rekey.command("series")
@click.pass_obj
def series(shop) -> None:
    """Open the incident and work order counters."""
    with shop.session() as session:
        IncidentService(session, shop.settings.scope).open_series()
        click.echo("incident and work order series opened")


@rekey.command("report")
@click.argument("tag")
@click.option("--cause", type=click.Choice([item.value for item in Cause]), default="lost")
@click.option("--note", default="", help="required for a theft")
@click.option("--keep-cut", is_flag=True, help="do not retire the bitting")
@TODAY
@click.pass_obj
def report(shop, tag, cause, note, keep_cut, when) -> None:
    """Report a key missing and retire its cut."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        row = service.report(tag, Cause(cause), as_day(when), note, not keep_cut)
        click.echo(f"{row.number} logged, graded {row.severity}")


@rekey.command("assess")
@click.argument("number")
@click.pass_obj
def assess(shop, number) -> None:
    """What one incident exposes, on one page."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        echo_lines(incident_sheet(service.assessment(number)))


@rekey.command("plan")
@click.argument("number")
@click.option("--cost", is_flag=True, help="price it as well")
@click.pass_obj
def plan(shop, number, cost) -> None:
    """What the shop would do about it, and what that costs."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        decision = service.plan(number)
        echo_lines(scope_sheet(decision))
        if cost:
            found = service.assessment(number)
            coverage = load_coverage(service.session)
            priced = estimate(decision, coverage, list(found.keys_to_recut), shop.settings.rates)
            echo_lines(cost_sheet(priced))


@rekey.command("book")
@click.argument("number")
@click.option("--for", "wanted", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def book(shop, number, wanted) -> None:
    """Raise the work order off an incident."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        order = service.raise_order(number, wanted.date() if wanted else None)
        click.echo(f"{order.number} raised with {len(order.lines)} cylinders")


@rekey.command("packet")
@click.argument("number")
@click.pass_obj
def packet(shop, number) -> None:
    """Print the sheets for one work order."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        row = service.orders.get(number)
        order = WorkOrder(
            number=row.number,
            incident=row.incident_number or "",
            lines=tuple(
                OrderLine(line.serial, line.opening_number, note=line.note) for line in row.lines
            ),
            technician=row.technician,
        )
        echo_lines(order_packet(order))


@rekey.command("orders")
@click.option("--technician", default=None, help="only this person's work")
@click.pass_obj
def orders(shop, technician) -> None:
    """The open work orders."""
    with working(shop, IncidentService, shop.settings.scope) as service:
        rows = (
            service.orders.for_technician(technician)
            if technician
            else service.orders.open_orders()
        )
        if not rows:
            click.echo("nothing open")
            return
        for row in rows:
            who = row.technician or "unassigned"
            click.echo(f"{row.number:<10}{row.state:<12}{who:<14}{len(row.lines)} cylinders")

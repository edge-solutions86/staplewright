"""The work packet: what a technician carries out to do a rekey.

One sheet a cylinder, in the order the doors sit in the building, because a
crew that walks a floor twice has wasted twenty minutes. Then the summary
page with the cost, which is what goes back to the department, and the
incident sheet that says why any of this is happening.

The packet deliberately repeats itself. The cylinder serial is on the header
of every sheet and again on the parts line, because sheets get separated on
a bench and a page with no serial on it is a page nobody can use.
"""

from __future__ import annotations

from hasplib.rekey.compromise import Assessment
from hasplib.rekey.costing import Estimate
from hasplib.rekey.orders import OrderLine, WorkOrder
from hasplib.rekey.scope import ScopeDecision

SERIAL_WIDTH = 10
OPENING_WIDTH = 12


def incident_sheet(assessment: Assessment) -> tuple[str, ...]:
    """Why the work is happening, in the words the meeting used."""
    incident = assessment.incident
    lines = [
        f"incident {incident.number}  {incident.cause.value}  {incident.reported_on.isoformat()}",
        f"key {incident.symbol} on tag {incident.tag}, held by {incident.keyholder or 'nobody'}",
        f"grade {assessment.severity.label}",
        f"{assessment.opening_count} openings in {len(assessment.buildings)} buildings",
    ]
    if assessment.exterior_openings:
        lines.append(f"exterior doors exposed: {', '.join(assessment.exterior_openings)}")
    if assessment.keys_to_recut:
        lines.append(f"keys to recut: {', '.join(assessment.keys_to_recut)}")
    if incident.note:
        lines.append(f"note: {incident.note}")
    return tuple(lines)


def scope_sheet(decision: ScopeDecision) -> tuple[str, ...]:
    """What was decided and why, on one page."""
    lines = [
        f"scope {decision.scope.label}: {decision.reason}",
        f"{decision.cylinder_count} cylinders, {len(decision.symbols)} symbols recut",
    ]
    lines.extend(f"  recut {symbol.render()}" for symbol in decision.symbols)
    if decision.over_cap:
        lines.append("over the cap the shop set, wants a signature")
    return tuple(lines)


def line_sheet(line: OrderLine, order: WorkOrder) -> tuple[str, ...]:
    """One cylinder, one page, with room to write on it."""
    return (
        f"{order.number}  cylinder {line.serial}  door {line.opening}",
        f"state {line.state.value}",
        "pinned by ..............  date ..............",
        "installed by ..........  date ..............",
        f"note: {line.note}" if line.note else "note:",
        "",
    )


def order_packet(order: WorkOrder) -> tuple[str, ...]:
    """Every sheet for one work order, in door order."""
    lines = [
        f"work order {order.number}  {order.state.value}",
        f"technician {order.technician or 'unassigned'}",
        f"{len(order.lines)} cylinders across {len(order.openings)} doors",
        "",
    ]
    for line in sorted(order.lines, key=lambda item: (item.opening, item.serial)):
        lines.extend(line_sheet(line, order))
    return tuple(lines)


def cost_sheet(estimate: Estimate) -> tuple[str, ...]:
    """The page the department reads, in minutes and in money."""
    return (
        f"{estimate.cylinders} cylinders, {estimate.cores} of them cores",
        f"{estimate.keys} keys to cut",
        f"bench {estimate.bench_minutes} min, door {estimate.door_minutes} min, "
        f"travel {estimate.travel_minutes} min",
        f"{estimate.hours} hours",
        f"labour {estimate.labour_cents} cents, parts {estimate.parts_cents} cents",
        f"total {estimate.total_cents} cents",
    )


def packet(
    assessment: Assessment,
    decision: ScopeDecision,
    order: WorkOrder,
    estimate: Estimate,
) -> tuple[str, ...]:
    """The whole thing, stapled in the order it gets read."""
    lines: list[str] = []
    lines.extend(incident_sheet(assessment))
    lines.append("")
    lines.extend(scope_sheet(decision))
    lines.append("")
    lines.extend(cost_sheet(estimate))
    lines.append("")
    lines.extend(order_packet(order))
    return tuple(lines)

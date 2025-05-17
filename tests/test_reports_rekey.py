from datetime import date

from hasplib.rekey.compromise import Cause, Incident, assess
from hasplib.rekey.costing import estimate
from hasplib.rekey.orders import OrderLine, WorkOrder
from hasplib.rekey.scope import ScopePolicy, decide
from hasplib.reports.page import as_text
from hasplib.reports.rekey import (
    cost_sheet,
    incident_sheet,
    line_sheet,
    order_packet,
    packet,
    scope_sheet,
)
from keyed import a_coverage, a_ledger

WHEN = date(2025, 4, 8)


def an_incident(**overrides) -> Incident:
    settings = {
        "number": "I-2025-004",
        "tag": "41207",
        "symbol": "AA1",
        "cause": Cause.LOST,
        "reported_on": WHEN,
        "keyholder": "kmaki",
    }
    settings.update(overrides)
    return Incident(**settings)


def the_pieces(symbol: str = "AA1", tag: str = "41207", **policy):
    coverage = a_coverage()
    found = assess(an_incident(symbol=symbol, tag=tag), coverage, a_ledger())
    decision = decide(found, coverage, ScopePolicy(**policy) if policy else ScopePolicy())
    cost = estimate(decision, coverage, list(found.keys_to_recut))
    order = WorkOrder(
        number="W-1041",
        incident="I-2025-004",
        lines=tuple(
            OrderLine(serial, opening)
            for serial, opening in zip(decision.cylinders, found.openings, strict=True)
        ),
    )
    return found, decision, order, cost


def test_the_incident_sheet_names_the_key_and_the_grade():
    found, _, _, _ = the_pieces()
    lines = incident_sheet(found)
    assert lines[0].startswith("incident I-2025-004  lost")
    assert "key AA1 on tag 41207" in lines[1]
    assert "grade routine" in lines[2]


def test_the_incident_sheet_lists_the_keys_to_recut():
    found, _, _, _ = the_pieces()
    assert any("keys to recut: 41208" in line for line in incident_sheet(found))


def test_an_exterior_door_gets_its_own_line():
    found, _, _, _ = the_pieces(symbol="A", tag="41210")
    assert any("exterior doors exposed" in line for line in incident_sheet(found))


def test_a_note_is_carried_through():
    coverage = a_coverage()
    found = assess(an_incident(cause=Cause.STOLEN, note="taken from a coat"), coverage, a_ledger())
    assert any("taken from a coat" in line for line in incident_sheet(found))


def test_somebody_holding_nothing_is_named_as_nobody():
    coverage = a_coverage()
    found = assess(an_incident(keyholder=""), coverage, a_ledger())
    assert "held by nobody" in incident_sheet(found)[1]


def test_the_scope_sheet_names_the_decision_and_the_recuts():
    _, decision, _, _ = the_pieces()
    lines = scope_sheet(decision)
    assert lines[0].startswith("scope group:")
    assert any("recut AA1" in line for line in lines)


def test_going_over_the_cap_asks_for_a_signature():
    _, decision, _, _ = the_pieces(cylinder_cap=1)
    assert any("wants a signature" in line for line in scope_sheet(decision))


def test_a_line_sheet_has_room_to_write_on():
    _, _, order, _ = the_pieces()
    lines = line_sheet(order.lines[0], order)
    assert lines[0].startswith("W-1041  cylinder C-1001")
    assert any(line.startswith("pinned by") for line in lines)
    assert lines[-1] == ""


def test_the_packet_has_a_sheet_a_cylinder():
    _, _, order, _ = the_pieces()
    lines = order_packet(order)
    headers = [line for line in lines if line.startswith("W-1041  cylinder")]
    assert len(headers) == len(order.lines)


def test_the_packet_walks_the_doors_in_order():
    _, _, order, _ = the_pieces()
    lines = order_packet(order)
    doors = [line.split("door ")[1] for line in lines if "door " in line]
    assert doors == sorted(doors)


def test_an_unassigned_order_says_so():
    _, _, order, _ = the_pieces()
    assert "technician unassigned" in order_packet(order)


def test_the_cost_sheet_carries_the_totals():
    _, _, _, cost = the_pieces()
    lines = cost_sheet(cost)
    assert lines[0].startswith("2 cylinders")
    assert any(line.startswith("total ") for line in lines)


def test_the_whole_packet_is_stapled_in_reading_order():
    found, decision, order, cost = the_pieces()
    lines = packet(found, decision, order, cost)
    assert lines[0].startswith("incident")
    assert any(line.startswith("scope ") for line in lines)
    assert any(line.startswith("work order") for line in lines)


def test_the_packet_joins_into_a_page():
    found, decision, order, cost = the_pieces()
    page = as_text(packet(found, decision, order, cost))
    assert page.startswith("incident I-2025-004")
    assert "\n\n" in page

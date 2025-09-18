from datetime import date

from hasplib.persistence.models.reference import BlankRow
from hasplib.persistence.models.stock import (
    PurchaseLineRow,
    PurchaseOrderRow,
    StockLineRow,
)
from hasplib.reports.stock import (
    Reconciliation,
    movement_log,
    outstanding_orders,
    reconcile,
    reconciliation_sheet,
    reorder_sheet,
    restricted_position,
    shrinkage,
    stock_report,
)

SINCE = date(2025, 1, 1)
UNTIL = date(2025, 6, 30)


def a_blank(part: str = "1145A", stock: int = 200, **overrides) -> BlankRow:
    settings = {
        "part_number": part,
        "section_id": 1,
        "manufacturer": "Sargent",
        "chambers": 6,
        "restricted": True,
        "reorder_at": 50,
        "stock": stock,
    }
    settings.update(overrides)
    return BlankRow(**settings)


def a_line(movement: str, quantity: int, when: date, part: str = "1145A", **overrides):
    settings = {
        "id": 0,
        "part_number": part,
        "movement": movement,
        "quantity": quantity,
        "moved_on": when,
        "reference": "",
        "adjustment": None,
        "note": "",
    }
    settings.update(overrides)
    return StockLineRow(**settings)


def a_run() -> list[StockLineRow]:
    return [
        a_line("received", 100, date(2025, 2, 1), reference="PO-4412"),
        a_line("cut", 30, date(2025, 3, 4), reference="41207"),
        a_line("returned", 2, date(2025, 3, 5)),
        a_line("scrapped", 4, date(2025, 4, 9), note="milled wrong"),
    ]


def an_order(number: str = "PO-9001", ordered: int = 250, received: int = 0, **overrides):
    settings = {
        "number": number,
        "supplier": "lockwood supply",
        "placed_on": date(2025, 5, 1),
        "state": "placed",
        "note": "",
    }
    settings.update(overrides)
    order = PurchaseOrderRow(**settings)
    order.lines = [
        PurchaseLineRow(
            order_number=number, part_number="1145A", ordered=ordered, received=received
        )
    ]
    return order


def test_the_closing_total_is_where_the_drawer_is_now():
    found = reconcile(a_blank(), a_run(), SINCE, UNTIL)
    assert found.closing == 200


def test_the_opening_total_is_worked_backwards():
    found = reconcile(a_blank(), a_run(), SINCE, UNTIL)
    assert found.opening == 200 - (100 - 30 + 2 - 4)


def test_a_movement_after_the_period_is_taken_off_the_closing():
    lines = [*a_run(), a_line("cut", 10, date(2025, 8, 1), reference="41300")]
    found = reconcile(a_blank(), lines, SINCE, UNTIL)
    assert found.closing == 210


def test_a_movement_before_the_period_is_left_in_the_opening():
    lines = [a_line("received", 50, date(2024, 12, 1)), *a_run()]
    found = reconcile(a_blank(), lines, SINCE, UNTIL)
    assert found.received == 100


def test_the_movements_are_totalled_by_kind():
    found = reconcile(a_blank(), a_run(), SINCE, UNTIL)
    assert (found.received, found.cut, found.returned, found.scrapped) == (100, 30, 2, 4)


def test_a_clean_run_accounts_for_everything():
    found = reconcile(a_blank(), a_run(), SINCE, UNTIL)
    assert found.agrees
    assert found.unaccounted == 0


def test_a_count_inside_the_period_shows_up_as_a_gap():
    lines = [*a_run(), a_line("counted", 180, date(2025, 5, 2), adjustment=-12)]
    found = reconcile(a_blank(), lines, SINCE, UNTIL)
    assert found.counted == 180
    assert found.unaccounted == -12
    assert not found.agrees


def test_a_count_that_agreed_leaves_the_part_accounted_for():
    lines = [*a_run(), a_line("counted", 168, date(2025, 5, 2), adjustment=0)]
    found = reconcile(a_blank(), lines, SINCE, UNTIL)
    assert found.agrees


def test_two_counts_add_their_gaps_up():
    lines = [
        a_line("counted", 190, date(2025, 3, 2), adjustment=-10),
        a_line("counted", 188, date(2025, 5, 2), adjustment=-2),
    ]
    found = reconcile(a_blank(), lines, SINCE, UNTIL)
    assert found.unaccounted == -12


def test_a_period_with_nothing_in_it_opens_where_it_closes():
    found = reconcile(a_blank(), [], SINCE, UNTIL)
    assert found.opening == found.closing == 200


def test_a_reconciliation_that_is_short_says_how_short():
    found = Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 88)
    assert found.unaccounted == -12
    assert found.describe() == "1145A 12 unaccounted"


def test_a_reconciliation_that_is_over_says_over():
    assert Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 103).describe().endswith("3 over")


def test_a_sheet_reads_opening_to_closing():
    lines = reconciliation_sheet(reconcile(a_blank(), a_run(), SINCE, UNTIL))
    assert lines[0] == "1145A from 132 to 200"
    assert lines[-1].strip() == "1145A accounts for every blank"


def test_the_sheet_carries_every_movement_kind():
    lines = reconciliation_sheet(reconcile(a_blank(), a_run(), SINCE, UNTIL))
    assert any(line.strip().startswith("scrapped") for line in lines)


def test_the_log_is_oldest_first_with_a_heading():
    lines = movement_log(a_run())
    assert lines[0].startswith("day")
    assert lines[1].startswith("2025-02-01")


def test_the_log_shows_what_a_line_did_to_the_drawer():
    lines = movement_log(a_run())
    assert "-30" in lines[2]


def test_the_log_falls_back_to_the_note_when_there_is_no_reference():
    lines = movement_log(a_run())
    assert any("milled wrong" in line for line in lines)


def test_nothing_short_says_so():
    assert shrinkage([reconcile(a_blank(), a_run(), SINCE, UNTIL)]) == (
        "every part accounts for itself",
    )


def test_the_worst_shortfall_comes_first():
    lines = shrinkage(
        [
            Reconciliation("RA4", 100, 0, 0, 0, 0, 0, 97),
            Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 60),
        ]
    )
    assert lines[0] == "2 parts do not account for themselves"
    assert "1145A" in lines[1]


def test_nothing_below_its_level_says_so():
    assert reorder_sheet([a_blank()]) == ("nothing is below its reorder level",)


def test_the_reorder_sheet_works_out_what_to_order():
    lines = reorder_sheet([a_blank(stock=10)])
    assert lines[0].startswith("part")
    assert lines[1].split()[-1] == "90"


def test_the_shortest_part_is_first_on_the_sheet():
    lines = reorder_sheet([a_blank(stock=40), a_blank("RA4", stock=1, reorder_at=20)])
    assert lines[1].startswith("RA4")


def test_no_restricted_brass_says_so():
    assert restricted_position([a_blank(restricted=False)]) == ("no restricted blanks are stocked",)


def test_the_restricted_position_totals_the_building():
    lines = restricted_position([a_blank(), a_blank("RA4", stock=40)])
    assert lines[0] == "240 restricted blanks across 2 part numbers"
    assert lines[1].strip().startswith("1145A")


def test_nothing_on_order_says_so():
    assert outstanding_orders([]) == ("nothing is on order",)


def test_a_received_order_is_not_outstanding():
    assert outstanding_orders([an_order(state="received", received=250)]) == (
        "nothing is on order",
    )


def test_an_open_order_lists_what_is_still_coming():
    lines = outstanding_orders([an_order(received=100)])
    assert lines[0] == "1 orders outstanding"
    assert lines[-1].strip() == "1145A       150 to come"


def test_a_line_that_fully_arrived_is_left_off():
    lines = outstanding_orders([an_order(state="part received", ordered=10, received=10)])
    assert len(lines) == 2


def test_the_pack_reads_in_the_order_somebody_signs_it():
    found = reconcile(a_blank(), a_run(), SINCE, UNTIL)
    lines = stock_report([a_blank(stock=10)], [found], [an_order()], SINCE, UNTIL)
    assert lines[0] == "blank stock 2025-01-01 to 2025-06-30"
    assert any(line.startswith("1 orders outstanding") for line in lines)
    assert any(line.startswith("part") for line in lines)

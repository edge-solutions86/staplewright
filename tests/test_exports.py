from datetime import date

import pytest

from campus import a_campus
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger
from hasplib.errors import NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.reports.exports import (
    CYLINDERS,
    HOLDINGS,
    OPENINGS,
    SCHEDULE,
    filename,
    holding_rows,
    schedule_rows,
    table,
    table_names,
    to_records,
    to_text,
)
from keyed import a_register
from people import TODAY, a_directory
from systems import small_schedule


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def a_cut_schedule():
    schedule = small_schedule()
    schedule.assign(sym("AA1"), Bitting.parse("620446"))
    return schedule


def a_ledger():
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY, deposit_cents=2500)
    ledger.issue("41208", "AA", "dsalo", TODAY, due_on=date(2025, 5, 1))
    return ledger


def lines(text: str) -> list[str]:
    return text.strip().split("\n")


def test_a_document_starts_with_its_header():
    found = to_text(("one", "two"), [(1, 2)])
    assert lines(found)[0] == "one,two"


def test_a_none_comes_out_as_an_empty_field():
    assert to_text(("one", "two"), [(1, None)]).strip().endswith("1,")


def test_a_comma_in_a_value_is_quoted():
    found = to_text(("one",), [("a room, shared",)])
    assert '"a room, shared"' in found


def test_the_line_endings_are_unix():
    assert "\r" not in to_text(("one",), [(1,)])


def test_a_document_with_no_rows_is_just_a_header():
    assert lines(to_text(("one", "two"), [])) == ["one,two"]


def test_the_schedule_rows_carry_the_level_and_the_cut():
    rows = schedule_rows(a_cut_schedule().entries())
    by_symbol = {row[0]: row for row in rows}
    assert by_symbol["AA1"][1] == 3
    assert by_symbol["AA1"][3] == "620446"
    assert by_symbol["AA2"][3] is None


def test_the_schedule_document_has_a_line_a_symbol():
    schedule = a_cut_schedule()
    found = lines(SCHEDULE.csv(schedule.entries()))
    assert found[0].startswith("symbol,")
    assert len(found) == len(schedule) + 1


def test_the_openings_document_says_yes_and_no_for_exterior():
    found = lines(OPENINGS.csv(a_campus()))
    outside = [line for line in found if line.startswith("MOO-101")]
    assert outside[0].endswith("north door")
    assert ",yes," in outside[0]


def test_an_interior_door_says_no():
    found = lines(OPENINGS.csv(a_campus()))
    inside = [line for line in found if line.startswith("MOO-214")]
    assert ",no," in inside[0]


def test_the_cylinder_document_leaves_a_shelved_cylinder_blank():
    found = lines(CYLINDERS.csv(a_register()))
    shelved = [line for line in found if line.startswith("C-1099")]
    assert shelved[0].endswith(",,,")


def test_an_installed_cylinder_names_its_door():
    found = lines(CYLINDERS.csv(a_register()))
    installed = [line for line in found if line.startswith("C-1001")]
    assert "MOO-214" in installed[0]
    assert "lockset" in installed[0]


def test_the_holdings_document_carries_the_dates():
    found = lines(HOLDINGS.csv(a_ledger().out()))
    assert TODAY.isoformat() in found[1]
    assert "2025-05-01" in found[2]


def test_a_key_with_no_due_date_leaves_it_blank():
    rows = holding_rows(a_ledger().out())
    assert rows[0][4] is None


def test_the_deposit_comes_off_the_book_when_one_is_given():
    ledger = a_ledger()
    book = DepositBook()
    book.charge("41207", "kmaki", 2500, TODAY)
    book.refund("41207", date(2025, 6, 1))
    rows = holding_rows(ledger.out(), book)
    assert rows[0][5] == 0


def test_without_a_book_the_deposit_is_what_the_key_says():
    rows = holding_rows(a_ledger().out())
    assert rows[0][5] == 2500


def test_the_filename_carries_the_kind_and_the_day():
    assert filename("holdings", date(2025, 4, 8)) == "hasplib-holdings-2025-04-08.csv"


def test_a_table_knows_its_own_columns():
    assert SCHEDULE.header[0] == "symbol"
    assert OPENINGS.name == "openings"


def test_a_table_hands_back_records_for_json():
    found = SCHEDULE.records(a_cut_schedule().entries())
    assert found[0]["symbol"] == "GGM"
    assert set(found[0]) == set(SCHEDULE.header)


def test_a_table_writes_json_a_person_can_read():
    import json

    found = json.loads(OPENINGS.json(a_campus()))
    assert found[0]["building"] == "HEI"


def test_a_missing_value_stays_null_in_json():
    import json

    from systems import empty_schedule

    schedule = empty_schedule()
    schedule.add(KeySymbol.parse("A"), section="LA")
    found = json.loads(SCHEDULE.json(schedule.entries()))
    assert found[-1]["bitting"] is None


def test_a_missing_value_is_empty_in_csv():
    from systems import empty_schedule

    schedule = empty_schedule()
    schedule.add(KeySymbol.parse("A"), section="LA")
    assert lines(SCHEDULE.csv(schedule.entries()))[-1].endswith(",,")


def test_a_table_names_its_own_file():
    assert CYLINDERS.filename(date(2025, 4, 8)) == "hasplib-cylinders-2025-04-08.csv"
    assert CYLINDERS.filename(date(2025, 4, 8), "json").endswith(".json")


def test_the_tables_can_be_looked_up_by_name():
    assert table("holdings") is HOLDINGS


def test_a_table_nobody_exports_is_a_404():
    with pytest.raises(NotFoundError):
        table("payroll")


def test_the_table_names_come_back_sorted():
    assert table_names() == ("cylinders", "holdings", "openings", "schedule")


def test_a_row_with_the_wrong_width_is_caught():
    with pytest.raises(ValueError):
        to_records(("a", "b"), [("only one",)])

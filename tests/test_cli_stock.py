from datetime import date

import pytest
from click.testing import CliRunner

from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.persistence.models.reference import BlankRow, KeywaySectionRow
from hasplib.persistence.models.stock import PurchaseLineRow, PurchaseOrderRow

WHEN = "2025-04-08"
LATER = "2025-05-20"


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    section = KeywaySectionRow(family_name="Sargent LA", code="L", manufacturer="Sargent")
    session.add(section)
    session.flush()
    session.add_all(
        [
            BlankRow(
                part_number="1145A",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=True,
                reorder_at=50,
                stock=200,
            ),
            BlankRow(
                part_number="RA4",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=False,
                reorder_at=20,
                stock=40,
            ),
        ]
    )
    order = PurchaseOrderRow(
        number="PO-9001", supplier="lockwood supply", placed_on=date(2025, 5, 1)
    )
    session.add(order)
    session.flush()
    session.add(
        PurchaseLineRow(order_number="PO-9001", part_number="1145A", ordered=250, received=100)
    )
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def test_showing_a_part_prints_both_totals(shop):
    lines = call(shop, "stock", "show", "1145A").output.splitlines()
    assert lines[0] == "1145A Sargent 6 chamber"
    assert lines[1] == "drawer 200, ledger 0"


def test_a_restricted_part_says_it_is_restricted(shop):
    assert "restricted" in call(shop, "stock", "show", "1145A").output


def test_an_open_part_says_nothing_about_restriction(shop):
    assert "restricted" not in call(shop, "stock", "show", "RA4").output


def test_a_part_nobody_stocks_is_refused(shop):
    assert call(shop, "stock", "show", "9999Z").exit_code != 0


def test_receiving_defaults_to_one(shop):
    assert "1145A now 201" in call(shop, "stock", "receive", "1145A", "--on", WHEN).output


def test_receiving_a_batch_takes_a_count(shop):
    found = call(shop, "stock", "receive", "1145A", "--count", "100", "--on", WHEN)
    assert "1145A now 300" in found.output


def test_cutting_a_restricted_blank_without_a_reference_is_refused(shop):
    assert call(shop, "stock", "cut", "1145A", "--on", LATER).exit_code != 0


def test_cutting_with_a_reference_goes_through(shop):
    found = call(shop, "stock", "cut", "1145A", "--against", "41207", "--on", LATER)
    assert "1145A now 199" in found.output


def test_cutting_an_open_blank_needs_nothing(shop):
    assert "RA4 now 39" in call(shop, "stock", "cut", "RA4", "--on", LATER).output


def test_cutting_more_than_the_drawer_holds_is_refused(shop):
    found = call(shop, "stock", "cut", "RA4", "--count", "41", "--on", LATER)
    assert found.exit_code != 0


def test_scrapping_takes_the_reason_as_an_argument(shop):
    found = call(shop, "stock", "scrap", "RA4", "milled wrong", "--count", "2", "--on", LATER)
    assert "RA4 now 38" in found.output


def test_counting_reports_the_gap(shop):
    found = call(shop, "stock", "count", "1145A", "188", "--on", LATER)
    assert found.output.strip() == "1145A counted 188, 12 short"


def test_a_count_that_agrees_says_so(shop):
    found = call(shop, "stock", "count", "RA4", "40", "--on", LATER)
    assert found.output.strip().endswith("agrees")


def test_a_negative_count_is_rejected_by_click(shop):
    assert call(shop, "stock", "count", "RA4", "-1").exit_code != 0


def test_the_log_has_a_heading_and_the_movements(shop):
    call(shop, "stock", "receive", "RA4", "--count", "5", "--on", WHEN)
    lines = call(shop, "stock", "log", "RA4").output.splitlines()
    assert lines[0].startswith("day")
    assert lines[1].startswith("2025-04-08")


def test_a_part_with_no_movements_prints_just_the_heading(shop):
    assert len(call(shop, "stock", "log", "RA4").output.splitlines()) == 1


def test_reconciling_reads_opening_to_closing(shop):
    call(shop, "stock", "receive", "RA4", "--count", "5", "--on", WHEN)
    lines = call(
        shop, "stock", "reconcile", "RA4", "--from", "2025-01-01", "--to", "2025-12-31"
    ).output.splitlines()
    assert lines[0] == "RA4 from 40 to 45"


def test_reconciling_defaults_to_the_year_so_far(shop):
    assert call(shop, "stock", "reconcile", "RA4").exit_code == 0


def test_the_reorder_sheet_names_what_is_short(shop):
    call(shop, "stock", "cut", "RA4", "--count", "25", "--on", LATER)
    lines = call(shop, "stock", "reorder").output.splitlines()
    assert lines[0].startswith("part")
    assert lines[1].startswith("RA4")


def test_nothing_short_says_so(shop):
    assert "nothing is below" in call(shop, "stock", "reorder").output


def test_the_restricted_position_totals_the_building(shop):
    assert "200 restricted blanks across 1 part numbers" in call(shop, "stock", "restricted").output


def test_the_open_orders_list_what_is_still_coming(shop):
    lines = call(shop, "stock", "orders").output.splitlines()
    assert lines[0] == "1 orders outstanding"
    assert "150 to come" in lines[-1]


def test_the_shrinkage_report_is_quiet_when_everything_adds_up(shop):
    assert "every part accounts for itself" in call(shop, "stock", "shrinkage").output


def test_a_count_that_did_not_agree_shows_up_as_shrinkage(shop):
    call(shop, "stock", "count", "1145A", "188", "--on", LATER)
    found = call(shop, "stock", "shrinkage", "--from", "2025-01-01")
    assert "1145A" in found.output


def test_the_pack_prints_top_to_bottom(shop):
    lines = call(shop, "stock", "report", "--from", "2025-01-01").output.splitlines()
    assert lines[0].startswith("blank stock 2025-01-01 to ")
    assert any(line.startswith("1 orders outstanding") for line in lines)

import pytest
from click.testing import CliRunner

import dbcampus
from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema

WHEN = "2025-04-08"


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    dbcampus.build(session)
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def with_series(shop):
    call(shop, "rekey", "series")
    return shop


def reported(shop, tag: str = "41207"):
    found = call(shop, "rekey", "report", tag, "--on", WHEN)
    return found.output.split()[0]


def test_the_series_open_once(shop):
    found = call(shop, "rekey", "series")
    assert "series opened" in found.output
    assert call(shop, "rekey", "series").exit_code != 0


def test_reporting_gives_a_number_and_a_grade(shop):
    with_series(shop)
    found = call(shop, "rekey", "report", "41207", "--on", WHEN)
    assert found.output.startswith("I-0001 logged, graded routine")


def test_a_grand_master_is_graded_critical(shop):
    with_series(shop)
    found = call(shop, "rekey", "report", "41211", "--on", WHEN)
    assert "critical" in found.output


def test_a_theft_without_a_note_fails(shop):
    with_series(shop)
    found = call(shop, "rekey", "report", "41207", "--cause", "stolen", "--on", WHEN)
    assert found.exit_code != 0


def test_a_cause_nobody_has_heard_of_fails(shop):
    with_series(shop)
    found = call(shop, "rekey", "report", "41207", "--cause", "mislaid")
    assert found.exit_code != 0


def test_the_cut_can_be_kept(shop):
    with_series(shop)
    found = call(shop, "rekey", "report", "41207", "--keep-cut", "--on", WHEN)
    assert found.exit_code == 0


def test_the_assessment_prints(shop):
    with_series(shop)
    number = reported(shop)
    found = call(shop, "rekey", "assess", number)
    assert found.output.startswith(f"incident {number}")
    assert "grade routine" in found.output


def test_the_plan_prints_the_scope(shop):
    with_series(shop)
    number = reported(shop)
    found = call(shop, "rekey", "plan", number)
    assert "scope group" in found.output


def test_the_plan_can_be_priced(shop):
    with_series(shop)
    number = reported(shop)
    found = call(shop, "rekey", "plan", number, "--cost")
    assert "total" in found.output
    assert "cents" in found.output


def test_booking_raises_an_order(shop):
    with_series(shop)
    number = reported(shop)
    found = call(shop, "rekey", "book", number)
    assert "W-1041 raised with 2 cylinders" in found.output


def test_an_order_can_be_booked_for_a_day(shop):
    with_series(shop)
    number = reported(shop)
    found = call(shop, "rekey", "book", number, "--for", "2025-04-15")
    assert found.exit_code == 0


def test_the_packet_prints_a_sheet_a_cylinder(shop):
    with_series(shop)
    number = reported(shop)
    call(shop, "rekey", "book", number)
    found = call(shop, "rekey", "packet", "W-1041")
    assert found.output.count("cylinder ") == 2


def test_a_packet_for_an_order_that_is_not_there_fails(shop):
    with_series(shop)
    assert call(shop, "rekey", "packet", "W-9999").exit_code != 0


def test_the_open_orders_list(shop):
    with_series(shop)
    number = reported(shop)
    call(shop, "rekey", "book", number)
    found = call(shop, "rekey", "orders")
    assert "W-1041" in found.output
    assert "unassigned" in found.output


def test_nothing_open_says_so(shop):
    with_series(shop)
    assert call(shop, "rekey", "orders").output.strip() == "nothing open"


def test_orders_can_be_filtered_by_technician(shop):
    with_series(shop)
    number = reported(shop)
    call(shop, "rekey", "book", number)
    found = call(shop, "rekey", "orders", "--technician", "jkoivu")
    assert found.output.strip() == "nothing open"

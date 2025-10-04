from datetime import date

import pytest
from click.testing import CliRunner

import dbcampus
from hasplib.cli.main import cli
from hasplib.control.escrow import BoxRegister, BoxState, KeyBox, OpenedBy
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.persistence.models.escrow import SealRow
from hasplib.reports.escrow import (
    coverage_sheet,
    exposure_sheet,
    key_found,
    overdue_sheet,
    seal_sheet,
    shop_report,
    state_counts,
    walk_sheet,
)

SEALED = date(2025, 1, 6)
WALKED = date(2025, 6, 30)
LATE = date(2025, 12, 1)
BUILDINGS = ["HEI", "MOO", "VOS"]
CHANGE_KEY = KeySymbol.parse("AA1")


def a_box(number: str = "KB-01", **overrides) -> KeyBox:
    settings = {
        "number": number,
        "building": "MOO",
        "opening": "MOO-101",
        "symbol": CHANGE_KEY,
        "seal": "44012",
        "sealed_on": SEALED,
    }
    settings.update(overrides)
    return KeyBox(**settings)


def a_register() -> BoxRegister:
    register = BoxRegister()
    register.install(a_box())
    register.install(a_box("KB-02", building="VOS", opening="VOS-100", seal="44013"))
    return register


def a_seal(seal: str = "44012", **overrides) -> SealRow:
    settings = {
        "id": 0,
        "box_number": "KB-01",
        "seal": seal,
        "symbol": CHANGE_KEY,
        "sealed_on": SEALED,
        "broken_on": None,
        "broken_by": "",
        "note": "",
    }
    settings.update(overrides)
    return SealRow(**settings)


def test_the_coverage_sheet_names_every_building():
    lines = coverage_sheet(a_register(), BUILDINGS)
    assert lines[0].startswith("building")
    assert lines[1].startswith("HEI")
    assert "none" in lines[1]


def test_the_coverage_sheet_says_nothing_about_keys():
    lines = coverage_sheet(a_register(), BUILDINGS)
    assert not any("AA1" in line for line in lines)


def test_a_withdrawn_box_leaves_a_building_uncovered():
    register = a_register()
    register.withdraw("KB-01", LATE, "gone")
    lines = coverage_sheet(register, BUILDINGS)
    assert any(line.startswith("MOO") and "none" in line for line in lines)


def test_a_sealed_campus_has_no_exposure():
    assert exposure_sheet(a_register(), LATE) == ("every box is sealed",)


def test_the_exposure_sheet_counts_the_days():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    lines = exposure_sheet(register, date(2025, 7, 10))
    assert lines[0] == "1 boxes are not sealed"
    assert "10 days" in lines[1]


def test_the_exposure_sheet_names_the_loose_keys():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert exposure_sheet(register, LATE)[-1] == "keys loose: AA1"


def test_an_empty_box_puts_no_key_loose():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    register.empty("KB-01", WALKED)
    lines = exposure_sheet(register, LATE)
    assert "nothing in it" in lines[1]
    assert not any(line.startswith("keys loose") for line in lines)


def test_a_quiet_week_has_nothing_to_walk():
    assert walk_sheet(a_register(), date(2025, 1, 20)) == ("nothing to walk this week",)


def test_the_walk_sheet_leads_with_the_reseals():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    lines = walk_sheet(register, LATE)
    assert lines[0] == "1 to reseal"


def test_the_walk_sheet_counts_the_days_since_a_check():
    lines = walk_sheet(a_register(), date(2025, 7, 6))
    assert any("181 days ago" in line for line in lines)


def test_no_seals_says_so():
    assert seal_sheet([], LATE) == ("no seal was ever recorded on that box",)


def test_the_seal_sheet_shows_the_open_seal_as_on_now():
    lines = seal_sheet([a_seal()], LATE)
    assert "on now" in lines[1]


def test_the_seal_sheet_is_oldest_first():
    rows = [a_seal("44120", sealed_on=WALKED), a_seal("44012", broken_on=WALKED)]
    lines = seal_sheet(rows, LATE)
    assert lines[1].startswith("44012")


def test_nothing_overdue_says_so():
    register = a_register()
    assert overdue_sheet(register, LATE, register.policy)[0] == "no box is overdue a reseal"


def test_a_box_open_too_long_is_on_the_overdue_sheet():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    lines = overdue_sheet(register, date(2025, 7, 10), register.policy)
    assert lines[0] == "1 boxes are past the 2 day reseal window"
    assert "open 10 days" in lines[1]


def test_the_state_counts_cover_every_state():
    assert set(state_counts(a_register())) == {item.value for item in BoxState}


def test_the_state_counts_add_up():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    counts = state_counts(register)
    assert counts["sealed"] == 1
    assert counts["opened"] == 1


def test_the_shop_report_reads_top_to_bottom():
    register = a_register()
    register.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    lines = shop_report(register, BUILDINGS, LATE)
    assert lines[0] == "key boxes at 2025-12-01, 2 on record"
    assert lines[1] == "1 sealed, 1 opened"
    assert any(line.startswith("1 buildings have no box") for line in lines)


def test_a_fully_covered_campus_says_so():
    lines = shop_report(a_register(), ["MOO", "VOS"], LATE)
    assert lines[-1] == "every building has a box"


def test_a_key_nobody_holds_says_so():
    assert key_found(a_register(), "ZZ9") == ("no box holds ZZ9",)


def test_a_found_key_names_the_boxes_it_belongs_to():
    lines = key_found(a_register(), "AA1")
    assert lines[0] == "2 boxes hold AA1"


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


def put_up(shop, number: str = "KB-01", opening: str = "MOO-101", building: str = "MOO"):
    return call(
        shop,
        "boxes",
        "install",
        number,
        building,
        opening,
        "AA1",
        "44012",
        "--on",
        "2025-01-06",
    )


def test_a_box_goes_up_from_the_command_line(shop):
    found = put_up(shop)
    assert found.output.strip() == "KB-01 sealed on MOO-101 with AA1"


def test_a_master_is_refused_from_the_command_line(shop):
    found = call(
        shop, "boxes", "install", "KB-01", "MOO", "MOO-101", "A", "44012", "--on", "2025-01-06"
    )
    assert found.exit_code != 0


def test_reading_the_right_seal_says_still_sealed(shop):
    put_up(shop)
    found = call(shop, "boxes", "check", "KB-01", "44012", "--on", "2025-06-30")
    assert found.output.strip() == "KB-01 still sealed on 44012"


def test_reading_the_wrong_seal_says_it_was_opened(shop):
    put_up(shop)
    found = call(shop, "boxes", "check", "KB-01", "44099", "--on", "2025-06-30")
    assert "has been opened" in found.output
    assert "expected 44012" in found.output


def test_reporting_an_opening_names_who(shop):
    put_up(shop)
    found = call(shop, "boxes", "opened", "KB-01", "--by", "fire service", "--on", "2025-06-30")
    assert found.output.strip() == "KB-01 open, fire service"


def test_an_opener_nobody_recognises_is_rejected_by_click(shop):
    put_up(shop)
    assert call(shop, "boxes", "opened", "KB-01", "--by", "the caretaker").exit_code != 0


def test_an_open_box_can_be_emptied(shop):
    put_up(shop)
    call(shop, "boxes", "opened", "KB-01", "--on", "2025-06-30")
    found = call(shop, "boxes", "empty", "KB-01", "--on", "2025-12-01")
    assert found.output.strip() == "KB-01 empty"


def test_resealing_says_what_went_in(shop):
    put_up(shop)
    call(shop, "boxes", "opened", "KB-01", "--on", "2025-06-30")
    found = call(shop, "boxes", "reseal", "KB-01", "44120", "AA2", "--on", "2025-12-01")
    assert found.output.strip() == "KB-01 sealed on 44120 with AA2"


def test_withdrawing_takes_it_off_the_wall(shop):
    put_up(shop)
    found = call(shop, "boxes", "withdraw", "KB-01", "building came down", "--on", "2025-12-01")
    assert found.output.strip() == "KB-01 off the wall"


def test_the_seals_command_shows_the_history(shop):
    put_up(shop)
    call(shop, "boxes", "opened", "KB-01", "--on", "2025-06-30")
    call(shop, "boxes", "reseal", "KB-01", "44120", "AA2", "--on", "2025-12-01")
    lines = call(shop, "boxes", "seals", "KB-01", "--on", "2025-12-01").output.splitlines()
    assert lines[0].startswith("seal")
    assert lines[1].startswith("44012")
    assert "on now" in lines[2]


def test_which_key_was_in_it_on_a_day(shop):
    put_up(shop)
    found = call(shop, "boxes", "key-on", "KB-01", "2025-03-01")
    assert found.output.strip() == "AA1"


def test_a_day_before_the_box_went_up_has_nothing_in_it(shop):
    put_up(shop)
    found = call(shop, "boxes", "key-on", "KB-01", "2024-01-01")
    assert found.output.strip() == "nothing was in it"


def test_the_open_command_is_quiet_when_everything_is_sealed(shop):
    put_up(shop)
    assert "every box is sealed" in call(shop, "boxes", "open").output


def test_the_open_command_names_the_loose_key(shop):
    put_up(shop)
    call(shop, "boxes", "opened", "KB-01", "--on", "2025-06-30")
    assert "keys loose: AA1" in call(shop, "boxes", "open", "--on", "2025-12-01").output


def test_the_walk_list_names_what_to_reseal(shop):
    put_up(shop)
    call(shop, "boxes", "opened", "KB-01", "--on", "2025-06-30")
    assert "1 to reseal" in call(shop, "boxes", "walk", "--on", "2025-12-01").output


def test_the_coverage_sheet_is_safe_to_hand_over(shop):
    put_up(shop)
    output = call(shop, "boxes", "coverage").output
    assert "MOO" in output
    assert "AA1" not in output


def test_a_found_key_is_traced_to_its_boxes(shop):
    put_up(shop)
    assert "1 boxes hold AA1" in call(shop, "boxes", "found", "AA1").output


def test_the_shop_report_prints_top_to_bottom(shop):
    put_up(shop)
    lines = call(shop, "boxes", "report", "--on", "2025-12-01").output.splitlines()
    assert lines[0] == "key boxes at 2025-12-01, 1 on record"
    assert lines[1] == "1 sealed"

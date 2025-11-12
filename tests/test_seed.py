from datetime import date

import pytest
from click.testing import CliRunner
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.cli.main import cli
from hasplib.errors import ConflictError
from hasplib.persistence.models import (
    BlankRow,
    GroupRow,
    IssuedKeyRow,
    KeyBoxRow,
    KeywaySectionRow,
    OpeningRow,
    ScheduleEntryRow,
    SystemRow,
)
from hasplib.persistence.seed import demo_family, seed, summary

TODAY = date(2025, 6, 30)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


@pytest.fixture()
def seeded(session):
    seed(session, TODAY)
    return session


def test_the_demo_family_has_a_section_whose_patent_ran_out():
    family = demo_family()
    assert family.section("LD").patent_expires == "2023-11-30"


def test_the_demo_family_has_one_that_has_not():
    assert demo_family().section("LC").patent_expires == "2031-04-01"


def test_seeding_hands_back_the_system(session):
    assert seed(session, TODAY).name == "chester park"


def test_the_system_is_stamped_with_the_day_it_opened(seeded):
    assert seeded.get(SystemRow, 1).opened_on == date(2024, 8, 19)


def test_seeding_twice_is_refused(seeded):
    with pytest.raises(ConflictError):
        seed(seeded, TODAY)


def test_the_sections_come_out_as_a_tree(seeded):
    rows = seeded.execute(select(KeywaySectionRow)).scalars().all()
    assert len(rows) == 6
    roots = [row for row in rows if row.parent_id is None]
    assert [row.code for row in roots] == ["L"]


def test_the_patent_dates_survive_the_seed(seeded):
    row = seeded.execute(select(KeywaySectionRow).where(KeywaySectionRow.code == "LD")).scalar_one()
    assert row.patent_expires == date(2023, 11, 30)


def test_every_symbol_is_cut(seeded):
    rows = seeded.execute(select(ScheduleEntryRow)).scalars().all()
    assert len(rows) == 10
    assert all(row.bitting is not None for row in rows)


def test_the_keying_groups_have_members(seeded):
    rows = seeded.execute(select(GroupRow)).scalars().all()
    assert {row.name for row in rows} == {
        "moore offices",
        "moore entrance",
        "voss offices",
        "shops",
    }
    assert all(row.members for row in rows)


def test_the_openings_are_written(seeded):
    assert len(seeded.execute(select(OpeningRow)).scalars().all()) == 11


def test_a_cylinder_is_left_on_the_shelf(seeded):
    from hasplib.persistence.models import CylinderRow

    rows = seeded.execute(select(CylinderRow)).scalars().all()
    assert [row.serial for row in rows if row.installed_at is None] == ["C-1007"]


def test_the_keys_are_out_to_people(seeded):
    rows = seeded.execute(select(IssuedKeyRow)).scalars().all()
    assert len(rows) == 6
    assert all(row.state == "out" for row in rows)


def test_one_key_is_out_to_somebody_whose_appointment_ended(seeded):
    from hasplib.health import keys_held_by_lapsed_people

    assert [item.subject for item in keys_held_by_lapsed_people(seeded, TODAY)] == ["41212"]


def test_the_blanks_are_stocked(seeded):
    rows = seeded.execute(select(BlankRow)).scalars().all()
    assert {row.part_number for row in rows} == {"1145A", "RA4"}


def test_the_stocktake_came_up_short(seeded):
    from hasplib.services.stock import StockService

    service = StockService(seeded)
    lines = service.movements("1145A")
    assert [line.movement for line in lines] == ["received", "cut", "counted"]
    assert lines[-1].adjustment == -12


def test_the_drawer_matches_what_was_counted(seeded):
    from hasplib.services.stock import StockService

    assert StockService(seeded).on_hand("1145A") == 182


def test_two_boxes_are_on_the_wall(seeded):
    rows = seeded.execute(select(KeyBoxRow)).scalars().all()
    assert [row.number for row in rows] == ["KB-01", "KB-02"]


def test_one_box_is_already_due_a_check(seeded):
    from hasplib.services.escrow import EscrowService

    found = EscrowService(seeded).due_for_check(TODAY)
    assert [box.number for box in found] == ["KB-01"]


def test_the_self_check_finds_something(seeded):
    from hasplib.health import run_checks

    assert run_checks(seeded, TODAY) != []


def test_the_summary_says_what_is_in_it():
    lines = summary()
    assert lines[0].startswith("3 buildings")
    assert lines[-1].endswith("on purpose")


def test_the_demo_command_builds_and_fills(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'demo.sqlite3'}"
    found = CliRunner().invoke(cli, ["--database", url, "demo", "--on", "2025-06-30"])
    assert found.exit_code == 0
    assert "demo campus written to" in found.output
    assert "3 buildings" in found.output


def test_the_demo_command_refuses_a_database_that_has_one(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'demo.sqlite3'}"
    CliRunner().invoke(cli, ["--database", url, "demo", "--on", "2025-06-30"])
    again = CliRunner().invoke(cli, ["--database", url, "demo"])
    assert again.exit_code != 0


def test_the_self_check_runs_on_the_demo(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'demo.sqlite3'}"
    CliRunner().invoke(cli, ["--database", url, "demo", "--on", "2025-06-30"])
    found = CliRunner().invoke(cli, ["--database", url, "check", "--on", "2025-06-30"])
    assert found.output.startswith("self check at 2025-06-30")
    assert found.exit_code == 1

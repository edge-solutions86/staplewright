import json
from datetime import date

import pytest
from click.testing import CliRunner

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.cli.main import cli
from hasplib.errors import InputError, NotFoundError
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.services.exports import ExportService

SYSTEM = "chester park"


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> ExportService:
    return ExportService(session)


def test_the_names_are_the_tables_that_exist(session):
    assert a_service(session).names() == ("cylinders", "holdings", "openings", "schedule")


def test_the_openings_export_carries_every_door(session):
    rows = a_service(session).rows_for("openings")[1]
    assert len(rows) == 11


def test_the_cylinders_export_carries_the_register(session):
    rows = a_service(session).rows_for("cylinders")[1]
    assert [row[0] for row in rows][:2] == ["C-1001", "C-1002"]


def test_the_holdings_export_only_has_what_is_out(session):
    rows = a_service(session).rows_for("holdings")[1]
    assert all(row[-1] == "out" for row in rows)


def test_the_schedule_export_needs_a_system(session):
    with pytest.raises(InputError):
        a_service(session).rows_for("schedule")


def test_a_system_nobody_opened_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).rows_for("schedule", "nowhere")


def test_the_schedule_export_carries_the_cuts(session):
    rows = a_service(session).rows_for("schedule", SYSTEM)[1]
    assert rows[0][0] == "GGM"
    assert rows[0][3] == "442446"


def test_a_table_nobody_exports_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).rows_for("payroll")


def test_the_csv_has_a_header_first(session):
    text = a_service(session).text("openings")
    assert text.splitlines()[0].startswith("number,building")


def test_the_json_is_a_list_of_objects(session):
    found = json.loads(a_service(session).text("openings", "json"))
    assert found[0]["number"]
    assert isinstance(found, list)


def test_a_format_nobody_writes_is_refused(session):
    with pytest.raises(InputError):
        a_service(session).text("openings", "xml")


def test_the_records_come_back_keyed_by_column(session):
    found = a_service(session).records("cylinders")
    assert set(found[0]) == {
        "serial",
        "format",
        "section",
        "chambers",
        "opening",
        "position",
        "group",
    }


def test_the_filename_carries_the_day_and_the_format(session):
    service = a_service(session)
    assert service.filename("openings", date(2025, 4, 8)) == "hasplib-openings-2025-04-08.csv"
    assert service.filename("openings", date(2025, 4, 8), "json").endswith(".json")


def test_the_bulk_export_leaves_the_schedule_out(session):
    found = a_service(session).every_table(date(2025, 4, 8))
    assert len(found) == 3
    assert not any("schedule" in name for name in found)


def test_the_bulk_export_is_keyed_by_filename(session):
    found = a_service(session).every_table(date(2025, 4, 8))
    assert "hasplib-openings-2025-04-08.csv" in found


def test_the_live_systems_are_listed(session):
    assert a_service(session).systems_to_export() == (SYSTEM,)


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    made = build_sessions(engine)()
    dbcampus.build(made)
    made.commit()
    made.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def test_the_list_command_names_the_tables(shop):
    assert call(shop, "export", "list").output.splitlines() == [
        "cylinders",
        "holdings",
        "openings",
        "schedule",
    ]


def test_one_table_goes_to_standard_output(shop):
    lines = call(shop, "export", "table", "openings").output.splitlines()
    assert lines[0].startswith("number,building")
    assert len(lines) == 12


def test_a_table_can_come_out_as_json(shop):
    found = json.loads(call(shop, "export", "table", "openings", "--as", "json").output)
    assert found[0]["building"]


def test_a_format_nobody_writes_is_rejected_by_click(shop):
    assert call(shop, "export", "table", "openings", "--as", "xml").exit_code != 0


def test_a_schedule_needs_a_system_named(shop):
    assert call(shop, "export", "table", "schedule").exit_code != 0


def test_a_schedule_with_a_system_comes_out(shop):
    lines = call(shop, "export", "table", "schedule", "--system", SYSTEM).output.splitlines()
    assert lines[1].startswith("GGM")


def test_the_columns_command_names_them(shop):
    assert call(shop, "export", "columns", "cylinders").output.splitlines()[0] == "serial"


def test_the_schedule_command_writes_a_file(shop, tmp_path):
    target = tmp_path / "schedule.csv"
    found = call(shop, "export", "schedule", SYSTEM, "--to", str(target))
    assert target.exists()
    assert str(target) in found.output


def test_the_schedule_command_prints_when_there_is_no_file(shop):
    output = call(shop, "export", "schedule", SYSTEM).output
    assert output.splitlines()[0].startswith("symbol,")


def test_the_bulk_export_drops_files_in_a_folder(shop, tmp_path):
    folder = tmp_path / "drop"
    found = call(shop, "export", "all", str(folder), "--on", "2025-04-08")
    assert (folder / "hasplib-openings-2025-04-08.csv").exists()
    assert len(found.output.splitlines()) == 3


def test_the_bulk_export_can_write_json(shop, tmp_path):
    folder = tmp_path / "drop"
    call(shop, "export", "all", str(folder), "--as", "json", "--on", "2025-04-08")
    assert (folder / "hasplib-holdings-2025-04-08.json").exists()


def test_the_name_command_says_what_a_file_would_be_called(shop):
    found = call(shop, "export", "name", "cylinders", "--on", "2025-04-08")
    assert found.output.strip() == "hasplib-cylinders-2025-04-08.csv"


def test_a_table_nobody_exports_is_refused_from_the_command_line(shop):
    assert call(shop, "export", "table", "payroll").exit_code != 0

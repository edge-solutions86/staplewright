import pytest
from click.testing import CliRunner
from sqlalchemy import select

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.cli.main import cli
from hasplib.errors import InputError
from hasplib.persistence.engine import build_engine, create_schema
from hasplib.persistence.models.campus import BuildingRow, OpeningRow
from hasplib.persistence.models.control import KeyholderRow
from hasplib.reports.imports import as_csv
from hasplib.services.imports import ImportService

BUILDINGS = as_csv(
    [
        ("code", "name", "built"),
        ("MOO", "Moore Hall", "1953"),
        ("VOS", "Voss Hall", "1971"),
    ]
)

OPENINGS = as_csv(
    [
        ("number", "building", "floor", "room", "function"),
        ("MOO-101", "MOO", "1", "101", "entrance"),
        ("MOO-110", "MOO", "1", "110", "classroom"),
    ]
)

PEOPLE = as_csv(
    [
        ("identifier", "name", "affiliation", "department"),
        ("kmaki", "K Maki", "staff", "facilities"),
        ("rvirta", "R Virta", "faculty", "geography"),
    ]
)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> ImportService:
    return ImportService(session)


def loaded_buildings(session) -> ImportService:
    service = a_service(session)
    service.load_buildings(BUILDINGS)
    return service


def test_a_dry_run_writes_nothing(session):
    service = a_service(session)
    found = service.load_buildings(BUILDINGS, dry_run=True)
    assert found.written == 2
    assert found.dry_run
    assert session.execute(select(BuildingRow)).scalars().all() == []


def test_a_write_puts_the_rows_in(session):
    loaded_buildings(session)
    rows = session.execute(select(BuildingRow)).scalars().all()
    assert [row.code for row in rows] == ["MOO", "VOS"]


def test_a_building_already_there_is_a_problem(session):
    service = loaded_buildings(session)
    found = service.load_buildings(BUILDINGS)
    assert not found.is_clean
    assert "already there" in found.problems[0].message


def test_a_missing_column_is_named(session):
    found = a_service(session).load_buildings(as_csv([("code",), ("MOO",)]))
    assert found.problems[0].column == "name"


def test_the_openings_load_onto_their_buildings(session):
    service = loaded_buildings(session)
    found = service.load_openings(OPENINGS)
    assert found.written == 2
    assert len(session.execute(select(OpeningRow)).scalars().all()) == 2


def test_an_opening_on_a_building_nobody_has_is_a_problem(session):
    found = a_service(session).load_openings(OPENINGS)
    assert not found.is_clean
    assert "no such building" in found.problems[0].message


def test_an_opening_already_there_is_a_problem(session):
    service = loaded_buildings(session)
    service.load_openings(OPENINGS)
    found = service.load_openings(OPENINGS)
    assert "already there" in found.problems[0].message


def test_a_bad_function_stops_the_whole_file(session):
    service = loaded_buildings(session)
    text = as_csv(
        [
            ("number", "building", "floor", "room", "function"),
            ("MOO-101", "MOO", "1", "101", "entrance"),
            ("MOO-110", "MOO", "1", "110", "hinged"),
        ]
    )
    found = service.load_openings(text)
    assert found.written == 0
    assert session.execute(select(OpeningRow)).scalars().all() == []


def test_the_keyholders_load(session):
    found = a_service(session).load_keyholders(PEOPLE)
    assert found.written == 2
    assert len(session.execute(select(KeyholderRow)).scalars().all()) == 2


def test_a_keyholder_already_there_is_a_problem(session):
    service = a_service(session)
    service.load_keyholders(PEOPLE)
    assert not service.load_keyholders(PEOPLE).is_clean


def test_an_affiliation_nobody_recognises_is_a_problem(session):
    text = as_csv(
        [
            ("identifier", "name", "affiliation", "department"),
            ("kmaki", "K Maki", "wizard", "facilities"),
        ]
    )
    found = a_service(session).load_keyholders(text)
    assert found.written == 0
    assert not found.is_clean


def test_a_temporary_person_can_carry_an_end_date(session):
    text = as_csv(
        [
            ("identifier", "name", "affiliation", "department", "ends_on"),
            ("jwex", "J Wexler", "contractor", "facilities", "2026-01-31"),
        ]
    )
    assert a_service(session).load_keyholders(text).written == 1


def test_a_kind_nobody_loads_is_refused(session):
    with pytest.raises(InputError):
        a_service(session).load("payroll", BUILDINGS)


def test_the_kinds_are_listed(session):
    assert a_service(session).kinds() == ("buildings", "keyholders", "openings")


def test_the_columns_for_a_kind_come_back(session):
    assert a_service(session).columns_for("keyholders")[0] == "identifier"


def test_the_columns_for_a_kind_nobody_loads_is_refused(session):
    with pytest.raises(InputError):
        a_service(session).columns_for("payroll")


def test_a_clean_load_reads_back_as_one_line(session):
    found = a_service(session).load_buildings(BUILDINGS, dry_run=True)
    assert found.describe() == ("buildings: 2 rows would load",)


def test_a_dirty_load_lists_the_problems(session):
    found = a_service(session).load_openings(OPENINGS)
    lines = found.describe()
    assert lines[1].endswith("problems")


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def a_file(tmp_path, name: str, text: str) -> str:
    path = tmp_path / name
    path.write_text(text, encoding="utf-8")
    return str(path)


def test_the_kinds_command_names_the_columns(shop):
    lines = call(shop, "import", "kinds").output.splitlines()
    assert lines[0].startswith("buildings")
    assert "code, name" in lines[0]


def test_the_columns_command_lists_them(shop):
    assert call(shop, "import", "columns", "keyholders").output.splitlines()[0] == "identifier"


def test_a_load_dry_runs_by_default(shop, tmp_path):
    path = a_file(tmp_path, "b.csv", BUILDINGS)
    found = call(shop, "import", "load", "buildings", path)
    assert "would load" in found.output
    again = call(shop, "import", "load", "buildings", path)
    assert "would load" in again.output


def test_a_load_with_write_puts_them_in(shop, tmp_path):
    path = a_file(tmp_path, "b.csv", BUILDINGS)
    found = call(shop, "import", "load", "buildings", path, "--write")
    assert found.output.strip() == "buildings: 2 rows"
    again = call(shop, "import", "load", "buildings", path, "--write")
    assert again.exit_code == 1


def test_a_dirty_load_exits_one(shop, tmp_path):
    path = a_file(tmp_path, "o.csv", OPENINGS)
    found = call(shop, "import", "load", "openings", path)
    assert found.exit_code == 1
    assert "no such building" in found.output


def test_the_check_command_is_quiet_on_a_clean_file(shop, tmp_path):
    path = a_file(tmp_path, "b.csv", BUILDINGS)
    found = call(shop, "import", "check", "buildings", path)
    assert found.output.strip() == "2 rows read, nothing to fix"


def test_the_check_command_lists_only_the_problems(shop, tmp_path):
    path = a_file(tmp_path, "o.csv", OPENINGS)
    found = call(shop, "import", "check", "openings", path)
    assert found.exit_code == 1
    assert found.output.splitlines()[0] == "2 problems"


def test_a_file_that_is_not_there_is_refused(shop):
    assert call(shop, "import", "load", "buildings", "nowhere.csv").exit_code != 0


def test_a_whole_campus_loads_in_order(shop, tmp_path):
    call(shop, "import", "load", "buildings", a_file(tmp_path, "b.csv", BUILDINGS), "--write")
    found = call(shop, "import", "load", "openings", a_file(tmp_path, "o.csv", OPENINGS), "--write")
    assert found.output.strip() == "openings: 2 rows"

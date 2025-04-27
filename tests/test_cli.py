import pytest
from click.testing import CliRunner

from charts import six_pin_chart
from hasplib.cli.main import cli, run
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.persistence.mappers.reference import chart_to_row
from hasplib.persistence.models import KeywaySectionRow
from systems import campus_family


@pytest.fixture()
def shop(tmp_path):
    """A file backed database so the command line can reopen it."""
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    session.add(chart_to_row(six_pin_chart()))
    session.flush()
    family = campus_family()
    ids: dict[str, int] = {}
    for code in sorted(family.codes(), key=family.depth_of):
        section = family.section(code)
        parent = family.parent_of(code)
        row = KeywaySectionRow(
            family_name=family.name,
            code=section.code,
            manufacturer=section.manufacturer,
            restricted=section.restricted,
            parent_id=ids.get(parent.code) if parent else None,
        )
        session.add(row)
        session.flush()
        ids[code] = row.id
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def opened(shop, name: str = "chester park"):
    return call(shop, "system", "open", name, "1", "Sargent LA", "L")


def test_the_settings_print(shop):
    found = call(shop, "settings")
    assert found.exit_code == 0
    assert "deposits:" in found.output


def test_the_tables_print(shop):
    found = call(shop, "tables")
    assert "keying_systems" in found.output


def test_the_schema_can_be_built(shop):
    found = call(shop, "build")
    assert found.exit_code == 0
    assert "schema built" in found.output


def test_opening_a_system_says_so(shop):
    found = opened(shop)
    assert found.exit_code == 0
    assert "chester park opened" in found.output


def test_a_system_cannot_be_opened_twice(shop):
    opened(shop)
    found = opened(shop)
    assert found.exit_code != 0


def test_drawing_a_symbol_says_where_it_landed(shop):
    opened(shop)
    found = call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    assert "A drawn on LA" in found.output


def test_a_symbol_inherits_the_section_above_it(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    found = call(shop, "system", "draw", "chester park", "AA")
    assert "AA drawn on LA" in found.output


def test_cutting_a_symbol_says_so(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    found = call(shop, "system", "cut", "chester park", "A", "642446")
    assert found.output.strip() == "A cut 642446"


def test_a_cut_the_rules_refuse_fails(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    found = call(shop, "system", "cut", "chester park", "A", "192156")
    assert found.exit_code != 0


def test_uncutting_puts_it_back_on_the_open_list(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    call(shop, "system", "cut", "chester park", "A", "642446")
    found = call(shop, "system", "uncut", "chester park", "A")
    assert "open list" in found.output


def test_the_schedule_prints(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    found = call(shop, "system", "schedule", "chester park")
    assert "chester park" in found.output
    assert "GGM" in found.output


def test_the_schedule_can_be_sorted_by_cut(shop):
    opened(shop)
    call(shop, "system", "draw", "chester park", "A", "--section", "LA")
    call(shop, "system", "cut", "chester park", "A", "642446")
    found = call(shop, "system", "schedule", "chester park", "--bittings")
    assert found.output.splitlines()[0].startswith("cut")


def test_the_tree_prints_on_its_own(shop):
    opened(shop)
    found = call(shop, "system", "schedule", "chester park", "--tree")
    assert "GGM" in found.output
    assert "leaf" in found.output


def test_the_open_symbols_print(shop):
    opened(shop)
    found = call(shop, "system", "open-symbols", "chester park")
    assert "1 symbols drawn and not cut" in found.output


def test_the_review_reports_nothing_on_a_finished_system(shop):
    opened(shop)
    call(shop, "system", "cut", "chester park", "GGM", "442446")
    found = call(shop, "system", "review", "chester park")
    assert found.output.strip() == "nothing to report"


def test_the_review_lists_findings(shop):
    opened(shop)
    found = call(shop, "system", "review", "chester park")
    assert "uncut" in found.output


def test_archiving_says_so(shop):
    opened(shop)
    found = call(shop, "system", "archive", "chester park")
    assert "archived" in found.output


def test_a_system_that_is_not_there_fails(shop):
    found = call(shop, "system", "schedule", "nothing at all")
    assert found.exit_code != 0


def test_the_entry_point_returns_zero_on_success(shop):
    assert run(["--database", shop, "settings"]) == 0


def test_the_entry_point_returns_two_on_a_bad_command(shop):
    assert run(["--database", shop, "nonsense"]) == 2

import pytest
from click.testing import CliRunner

import dbcampus
from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.persistence.models.keying import SystemRow
from hasplib.services.keyways import KeywayService
from hasplib.spec.keyway import KeywayFamily, KeywaySection

TODAY = "2025-06-30"


def everest() -> KeywayFamily:
    family = KeywayFamily(
        "everest 29",
        KeywaySection("C", "Schlage", restricted=True, patent_expires="2031-04-01"),
    )
    family.add(KeywaySection("CE", "Schlage", restricted=True, patent_expires="2027-02-14"), "C")
    family.add(KeywaySection("CF", "Schlage", restricted=True, patent_expires="2023-11-30"), "C")
    family.add(KeywaySection("CEA", "Schlage"), "CE")
    return family


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    dbcampus.build(session)
    KeywayService(session).store(everest())
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def keyed_on(shop, name: str, section: str):
    engine = build_engine(shop)
    session = build_sessions(engine)()
    chart_id = session.query(SystemRow).first().chart_id
    session.add(
        SystemRow(name=name, chart_id=chart_id, family_name="everest 29", top_section=section)
    )
    session.commit()
    session.close()
    engine.dispose()
    return shop


def test_the_families_list_counts_the_sections(shop):
    found = call(shop, "keyways", "families")
    assert "everest 29  4 sections" in found.output
    assert "Sargent LA  6 sections" in found.output


def test_the_sections_come_out_soonest_first(shop):
    lines = call(shop, "keyways", "sections", "everest 29", "--on", TODAY).output.splitlines()
    assert lines[0] == "CF patent ran out 2023-11-30"
    assert lines[1] == "CE restricted until 2027-02-14"


def test_an_unrestricted_section_says_so(shop):
    output = call(shop, "keyways", "sections", "everest 29", "--on", TODAY).output
    assert "CEA open keyway" in output


def test_a_family_nobody_stored_is_refused(shop):
    found = call(shop, "keyways", "sections", "nothing")
    assert found.exit_code != 0


def test_the_tree_is_indented_by_depth(shop):
    lines = call(shop, "keyways", "tree", "everest 29").output.splitlines()
    assert lines[0] == "C"
    assert "  CE" in lines
    assert "    CEA" in lines


def test_the_patent_report_leads_with_what_is_gone(shop):
    lines = call(shop, "keyways", "patents", "everest 29", "--on", TODAY).output.splitlines()
    assert lines[0] == "everest 29 patent position at 2025-06-30"
    assert lines[1] == "1 sections are open and were not meant to be"


def test_the_risk_report_is_quiet_when_nothing_is_keyed_on_a_gone_section(shop):
    output = call(shop, "keyways", "risk", "--on", TODAY).output
    assert "nothing the shop keys on has gone open" in output


def test_the_risk_report_names_the_system_sitting_on_it(shop):
    keyed_on(shop, "voss hall", "CF")
    output = call(shop, "keyways", "risk", "--on", TODAY).output
    assert "1 sections in use have gone open" in output
    assert "carrying voss hall" in output


def test_checking_a_section_with_years_left_passes(shop):
    found = call(shop, "keyways", "check", "everest 29", "C", "--on", TODAY)
    assert found.output.strip() == "C is good for 5 years"


def test_checking_a_section_that_goes_first_is_refused(shop):
    found = call(shop, "keyways", "check", "everest 29", "CE", "--on", TODAY)
    assert found.exit_code != 0


def test_a_shorter_horizon_lets_it_through(shop):
    found = call(shop, "keyways", "check", "everest 29", "CE", "--on", TODAY, "--years", "1")
    assert found.output.strip() == "CE is good for 1 years"


def test_a_horizon_that_runs_backwards_is_rejected_by_click(shop):
    found = call(shop, "keyways", "check", "everest 29", "C", "--years", "-1")
    assert found.exit_code != 0


def test_nothing_keyed_on_a_section_says_so(shop):
    found = call(shop, "keyways", "carrying", "everest 29", "CF")
    assert found.output.strip() == "nothing is keyed on CF"


def test_the_systems_on_a_section_are_listed(shop):
    keyed_on(shop, "voss hall", "CF")
    found = call(shop, "keyways", "carrying", "everest 29", "CF")
    assert found.output.strip() == "voss hall"


def test_the_sections_default_to_today_when_nobody_says(shop):
    found = call(shop, "keyways", "sections", "everest 29")
    assert found.exit_code == 0
    assert "CF" in found.output

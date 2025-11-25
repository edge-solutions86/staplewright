import pytest
from click.testing import CliRunner
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema

TODAY = "2025-06-30"
LAST_YEAR = "2024-06-30"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    dbcampus.build(session)
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


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


def test_the_measurement_comes_back_without_filing(client):
    body = client.get("/returns/measure", params={"on": TODAY}).json()
    assert body["year"] == 2025
    assert body["keys_out"] == 5
    assert client.get("/returns").json()["returns"] == []


def test_the_average_is_rounded(client):
    assert client.get("/returns/measure", params={"on": TODAY}).json()["keys_per_holder"] == 1.25


def test_filing_writes_the_year_down(client):
    found = client.post("/returns", json={"measured_on": TODAY, "filed_by": "rvirta"})
    assert found.status_code == 201
    assert found.json()["year"] == 2025
    assert found.json()["filed_by"] == "rvirta"


def test_a_year_cannot_be_filed_twice(client):
    client.post("/returns", json={"measured_on": TODAY})
    assert client.post("/returns", json={"measured_on": TODAY}).status_code == 409


def test_a_year_nobody_filed_is_a_404(client):
    assert client.get("/returns/2019").status_code == 404


def test_a_filed_year_reads_back(client):
    client.post("/returns", json={"measured_on": TODAY})
    assert client.get("/returns/2025").json()["keys_out"] == 5


def test_withdrawing_needs_a_reason(client):
    client.post("/returns", json={"measured_on": TODAY})
    assert client.post("/returns/2025/withdrawal", json={"note": ""}).status_code == 422


def test_a_withdrawn_year_says_so(client):
    client.post("/returns", json={"measured_on": TODAY})
    found = client.post("/returns/2025/withdrawal", json={"note": "counted twice"})
    assert found.json()["note"].startswith("withdrawn:")


def test_the_sheet_comes_back_as_lines(client):
    body = client.get("/returns/sheet", params={"on": TODAY}).json()
    assert body["lines"][0] == "key control return for 2025"


def test_the_sheet_comes_back_as_paper_when_asked(client):
    found = client.get("/returns/sheet", params={"on": TODAY, "plain": True})
    assert found.headers["content-type"].startswith("text/plain")


def test_the_trend_says_when_nothing_is_filed(client):
    assert client.get("/returns/trend").json()["lines"] == ["no returns have been filed"]


def test_the_trend_grows_a_row_per_year(client):
    client.post("/returns", json={"measured_on": LAST_YEAR})
    client.post("/returns", json={"measured_on": TODAY})
    assert len(client.get("/returns/trend").json()["lines"]) == 3


def test_the_masters_come_back(client):
    body = client.get("/returns/masters").json()
    assert body["lines"][0] == "3 master keys are out"


def test_the_concentration_is_most_first(client):
    body = client.get("/returns/concentration").json()
    assert body["holders"][0] == {"keyholder": "phamm", "keys": 2}


def test_the_concentration_can_be_cut_short(client):
    body = client.get("/returns/concentration", params={"top": 1}).json()
    assert len(body["holders"]) == 1


def test_a_top_of_nothing_is_a_422(client):
    assert client.get("/returns/concentration", params={"top": 0}).status_code == 422


def test_the_take_command_prints_the_sheet(shop):
    lines = call(shop, "return", "take", "--on", TODAY).output.splitlines()
    assert lines[0] == "key control return for 2025"


def test_the_file_command_writes_the_year(shop):
    found = call(shop, "return", "file", "--on", TODAY, "--by", "rvirta")
    assert found.output.strip() == "2025 filed: 5 out, 0 unaccounted"


def test_filing_twice_is_refused_from_the_command_line(shop):
    call(shop, "return", "file", "--on", TODAY)
    assert call(shop, "return", "file", "--on", TODAY).exit_code != 0


def test_withdrawing_says_so(shop):
    call(shop, "return", "file", "--on", TODAY)
    found = call(shop, "return", "withdraw", "2025", "counted the shops twice")
    assert found.output.strip() == "2025 withdrawn"


def test_withdrawing_a_year_nobody_filed_is_refused(shop):
    assert call(shop, "return", "withdraw", "2019", "whatever").exit_code != 0


def test_the_trend_command_prints_the_years(shop):
    call(shop, "return", "file", "--on", LAST_YEAR)
    call(shop, "return", "file", "--on", TODAY)
    lines = call(shop, "return", "trend").output.splitlines()
    assert lines[0].startswith("year")
    assert lines[1].startswith("2024")


def test_the_masters_command_lists_them(shop):
    assert "3 master keys are out" in call(shop, "return", "masters").output


def test_the_unaccounted_command_is_quiet_on_a_clean_year(shop):
    assert (
        "every key out is accounted for"
        in call(shop, "return", "unaccounted", "--on", TODAY).output
    )


def test_the_concentration_command_names_the_top_holder(shop):
    assert call(shop, "return", "concentration").output.splitlines()[0].startswith("phamm")


def test_the_movement_command_says_there_is_nothing_to_compare(shop):
    output = call(shop, "return", "movement", "--on", TODAY).output
    assert "no return was filed last year" in output


def test_the_movement_command_compares_against_a_filed_year(shop):
    call(shop, "return", "file", "--on", LAST_YEAR)
    lines = call(shop, "return", "movement", "--on", TODAY).output.splitlines()
    assert lines[0] == "against 2024"
    assert "level 0" in lines[1]

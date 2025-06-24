import pytest
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.persistence.models import DepositLineRow

WHEN = "2025-03-04"
LATE = "2025-12-01"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    dbcampus.build(session)
    session.add(
        DepositLineRow(
            tag="41207",
            keyholder="kmaki",
            kind="charge",
            cents=2500,
            happened_on=dbcampus.TODAY,
        )
    )
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def test_a_schedule_comes_back_as_lines(client):
    found = client.get("/reports/systems/chester park/schedule")
    assert found.status_code == 200
    assert found.json()["lines"][0] == "chester park"


def test_a_schedule_can_come_back_as_text(client):
    found = client.get("/reports/systems/chester park/schedule", params={"plain": True})
    assert found.headers["content-type"].startswith("text/plain")
    assert found.text.startswith("chester park")


def test_the_bitting_list_is_another_shape_of_the_same_page(client):
    found = client.get("/reports/systems/chester park/schedule", params={"shape": "bittings"})
    assert found.json()["lines"][0].startswith("cut")


def test_the_tree_is_another_shape_again(client):
    found = client.get("/reports/systems/chester park/schedule", params={"shape": "tree"})
    assert any("leaf" in line for line in found.json()["lines"])


def test_a_shape_nobody_has_heard_of_is_refused(client):
    found = client.get("/reports/systems/chester park/schedule", params={"shape": "pie"})
    assert found.status_code == 422


def test_a_system_that_is_not_there_is_a_404(client):
    assert client.get("/reports/systems/nothing/schedule").status_code == 404


def test_a_statement_carries_the_keys_and_a_place_to_sign(client):
    found = client.get("/reports/keyholders/kmaki/statement")
    lines = found.json()["lines"]
    assert lines[0].startswith("Kirsi Maki")
    assert lines[-1].startswith("signed")


def test_a_statement_can_be_dated(client):
    found = client.get("/reports/keyholders/kmaki/statement", params={"on": WHEN})
    assert f"as at {WHEN}" in found.json()["lines"]


def test_a_keyholder_that_is_not_there_is_a_404(client):
    assert client.get("/reports/keyholders/ghost/statement").status_code == 404


def test_the_deposit_statement_shows_the_balance(client):
    found = client.get("/reports/keyholders/kmaki/deposits")
    assert found.json()["lines"][-1] == "held $25.00"


def test_a_department_summary_needs_a_day(client):
    assert client.get("/reports/departments/geography").status_code == 422


def test_a_department_summary_counts_its_people(client):
    found = client.get("/reports/departments/geography", params={"on": WHEN})
    assert found.json()["lines"][0].startswith("geography: 4 on the books")


def test_the_chase_list_is_quiet_when_nothing_is_late(client):
    found = client.get("/reports/overdue", params={"on": WHEN})
    assert found.json()["lines"][0].startswith("nothing overdue")


def test_the_letters_run_is_quiet_when_nothing_is_late(client):
    found = client.get("/reports/letters", params={"on": WHEN})
    assert found.json()["lines"][0] == "no letters to send today"


def test_the_campus_numbers_come_back(client):
    found = client.get("/reports/campus")
    assert any("buildings" in line for line in found.json()["lines"])


def test_the_campus_numbers_can_be_plain(client):
    found = client.get("/reports/campus", params={"plain": True})
    assert "buildings" in found.text


def test_the_widest_keys_come_back_ranked(client):
    found = client.get("/reports/exposure").json()["keys"]
    assert found[0]["symbol"] == "GGM"
    assert found[0]["openings"] == 6


def test_the_exposure_list_can_be_cut_short(client):
    assert len(client.get("/reports/exposure", params={"limit": 2}).json()["keys"]) == 2


def test_a_limit_outside_the_range_is_refused(client):
    assert client.get("/reports/exposure", params={"limit": 0}).status_code == 422


def test_the_keys_by_building_come_back(client):
    found = client.get("/reports/buildings").json()["keys_by_building"]
    assert found["MOO"] >= found["HEI"]

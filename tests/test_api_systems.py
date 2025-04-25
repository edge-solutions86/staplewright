import pytest
from fastapi.testclient import TestClient

from charts import six_pin_chart
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app, status_for
from hasplib.errors import CapacityError, ConflictError, HaspError, NotFoundError
from hasplib.persistence.mappers.reference import chart_to_row
from hasplib.persistence.models import KeywaySectionRow
from systems import campus_family


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    chart = chart_to_row(six_pin_chart())
    session.add(chart)
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
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def open_system(client, name: str = "chester park"):
    body = {
        "name": name,
        "chart_id": 1,
        "family_name": "Sargent LA",
        "top_section": "L",
    }
    return client.post("/systems", json=body)


@pytest.mark.parametrize(
    ("error", "status"),
    [
        (NotFoundError("no"), 404),
        (ConflictError("no"), 409),
        (CapacityError("no"), 409),
        (HaspError("no"), 400),
    ],
)
def test_every_refusal_gets_the_status_it_deserves(error, status):
    assert status_for(error) == status


def test_health_says_up(client):
    found = client.get("/health")
    assert found.status_code == 200
    assert found.json()["status"] == "up"


def test_the_settings_can_be_read(client):
    found = client.get("/settings")
    assert found.status_code == 200
    assert len(found.json()["settings"]) == 6


def test_opening_a_system_gives_it_back(client):
    found = open_system(client)
    assert found.status_code == 201
    assert found.json()["name"] == "chester park"


def test_a_system_cannot_be_opened_twice(client):
    open_system(client)
    found = open_system(client)
    assert found.status_code == 409
    assert found.json()["code"] == "conflict"


def test_a_system_that_is_not_there_is_a_404(client):
    found = client.get("/systems/nothing")
    assert found.status_code == 404
    assert found.json()["code"] == "not_found"


def test_a_new_system_holds_only_the_top_key(client):
    open_system(client)
    found = client.get("/systems/chester park")
    assert found.json()["symbols"] == 1
    assert found.json()["open_symbols"] == 1


def test_drawing_a_symbol_puts_it_on_the_schedule(client):
    open_system(client)
    found = client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    assert found.status_code == 201
    assert found.json() == {"symbol": "A", "section": "LA"}


def test_a_symbol_with_no_parent_is_refused(client):
    open_system(client)
    found = client.post("/systems/chester park/symbols", json={"symbol": "AA1"})
    assert found.status_code == 400
    assert found.json()["code"] == "hierarchy"


def test_the_schedule_reads_back_in_order(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    client.post("/systems/chester park/symbols", json={"symbol": "AA", "section": "LC"})
    found = client.get("/systems/chester park/schedule")
    assert [entry["symbol"] for entry in found.json()["entries"]] == ["GGM", "A", "AA"]


def test_cutting_a_symbol_writes_the_cut(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    found = client.put("/systems/chester park/symbols/A/cut", json={"bitting": "642446"})
    assert found.status_code == 200
    assert found.json()["bitting"] == "642446"


def test_a_cut_the_rules_refuse_is_a_conflict(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    found = client.put("/systems/chester park/symbols/A/cut", json={"bitting": "192156"})
    assert found.status_code == 409


def test_a_bitting_that_is_not_a_bitting_is_refused(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    found = client.put("/systems/chester park/symbols/A/cut", json={"bitting": "64q446"})
    assert found.status_code == 422


def test_an_empty_bitting_never_reaches_the_domain(client):
    open_system(client)
    found = client.put("/systems/chester park/symbols/A/cut", json={"bitting": ""})
    assert found.status_code == 422


def test_uncutting_clears_the_cut(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    client.put("/systems/chester park/symbols/A/cut", json={"bitting": "642446"})
    found = client.delete("/systems/chester park/symbols/A/cut")
    assert found.json()["bitting"] is None


def test_the_review_reports_what_is_open(client):
    open_system(client)
    client.post("/systems/chester park/symbols", json={"symbol": "A", "section": "LA"})
    found = client.get("/systems/chester park/review")
    codes = {finding["code"] for finding in found.json()["findings"]}
    assert codes == {"uncut"}


def test_a_step_outside_the_range_is_refused(client):
    body = {
        "name": "voss",
        "chart_id": 1,
        "family_name": "Sargent LA",
        "top_section": "L",
        "progression_step": 99,
    }
    assert client.post("/systems", json=body).status_code == 422

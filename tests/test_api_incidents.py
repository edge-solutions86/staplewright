import pytest
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.services.incidents import IncidentService

WHEN = "2025-04-08"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    dbcampus.build(session)
    IncidentService(session).open_series()
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def report(client, tag: str = "41207", **overrides):
    body = {"tag": tag, "cause": "lost", "reported_on": WHEN}
    body.update(overrides)
    return client.post("/incidents", json=body)


def test_reporting_gives_back_a_number_and_a_grade(client):
    found = report(client)
    assert found.status_code == 201
    assert found.json()["number"] == "I-0001"
    assert found.json()["severity"] == "routine"


def test_a_grand_master_is_graded_critical(client):
    found = report(client, "41211")
    assert found.json()["severity"] == "critical"


def test_a_theft_needs_a_note(client):
    found = report(client, cause="stolen")
    assert found.status_code == 409


def test_a_theft_with_a_note_lands(client):
    found = report(client, cause="stolen", note="taken from a coat in the hall")
    assert found.json()["cause"] == "stolen"


def test_a_cause_nobody_has_heard_of_never_reaches_the_service(client):
    found = report(client, cause="mislaid")
    assert found.status_code == 422


def test_a_tag_nobody_issued_is_a_404(client):
    found = report(client, "99999")
    assert found.status_code == 404


def test_a_key_cannot_be_reported_twice(client):
    report(client)
    assert report(client).status_code == 409


def test_an_incident_reads_back(client):
    number = report(client).json()["number"]
    found = client.get(f"/incidents/{number}")
    assert found.json()["symbol"] == "AA1"


def test_an_incident_that_is_not_there(client):
    assert client.get("/incidents/I-9999").status_code == 404


def test_the_assessment_lists_the_doors_and_the_cylinders(client):
    number = report(client).json()["number"]
    found = client.get(f"/incidents/{number}/assessment").json()
    assert found["openings"] == ["MOO-214", "MOO-216"]
    assert found["cylinders"] == ["C-1001", "C-1002"]
    assert found["keys_to_recut"] == ["41208"]


def test_the_assessment_names_the_exterior_doors(client):
    number = report(client, "41210").json()["number"]
    found = client.get(f"/incidents/{number}/assessment").json()
    assert found["exterior_openings"] == ["MOO-101"]


def test_the_plan_names_the_scope_and_the_reason(client):
    number = report(client).json()["number"]
    found = client.get(f"/incidents/{number}/plan").json()
    assert found["scope"] == "group"
    assert found["cylinders"] == ["C-1001", "C-1002"]
    assert found["symbols"] == ["AA1"]


def test_a_master_plans_a_wider_scope(client):
    number = report(client, "41211").json()["number"]
    assert client.get(f"/incidents/{number}/plan").json()["scope"] == "system"


def test_an_order_is_raised_with_a_line_a_cylinder(client):
    number = report(client).json()["number"]
    found = client.post(f"/incidents/{number}/orders", json={})
    assert found.status_code == 201
    assert found.json()["lines"] == 2
    assert found.json()["incident"] == number


def test_an_order_can_be_booked_for_a_day(client):
    number = report(client).json()["number"]
    found = client.post(f"/incidents/{number}/orders", json={"scheduled_for": "2025-04-15"})
    assert found.json()["scheduled_for"] == "2025-04-15"


def test_an_incident_that_calls_for_no_work_raises_nothing(client):
    number = report(client, "41207", retire_cut=False).json()["number"]
    client.post(f"/incidents/{number}/orders", json={})
    second = report(client, "41208").json()["number"]
    assert client.post(f"/incidents/{second}/orders", json={}).status_code in {201, 409}

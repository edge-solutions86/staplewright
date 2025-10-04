import pytest
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app

SEALED = "2025-01-06"
WALKED = "2025-06-30"
LATE = "2025-12-01"


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


def install(client, number: str = "KB-01", **overrides):
    body = {
        "number": number,
        "building": "MOO",
        "opening": "MOO-101",
        "symbol": "AA1",
        "seal": "44012",
        "sealed_on": SEALED,
    }
    body.update(overrides)
    return client.post("/boxes", json=body)


def opened(client, number: str = "KB-01"):
    return client.post(
        f"/boxes/{number}/openings",
        json={"opened_by": "fire service", "opened_on": WALKED, "note": "kitchen fire"},
    )


def test_a_box_goes_up(client):
    found = install(client)
    assert found.status_code == 201
    assert found.json()["state"] == "sealed"
    assert found.json()["symbol"] == "AA1"


def test_a_box_on_a_door_nobody_has_is_a_404(client):
    assert install(client, opening="MOO-999").status_code == 404


def test_a_second_box_on_one_door_is_a_409(client):
    install(client)
    assert install(client, "KB-02").status_code == 409


def test_a_master_on_a_wall_is_a_409(client):
    assert install(client, symbol="A").status_code == 409


def test_a_symbol_nobody_can_parse_is_a_422(client):
    assert install(client, symbol="!!").status_code == 400


def test_the_boxes_come_back_in_number_order(client):
    install(client)
    install(client, "KB-02", opening="MOO-110")
    body = client.get("/boxes").json()
    assert [row["number"] for row in body["boxes"]] == ["KB-01", "KB-02"]


def test_the_listing_can_be_narrowed_to_a_building(client):
    install(client)
    assert client.get("/boxes", params={"building": "VOS"}).json()["boxes"] == []


def test_a_box_nobody_installed_is_a_404(client):
    assert client.get("/boxes/KB-99").status_code == 404


def test_reading_the_right_seal_records_the_walk(client):
    install(client)
    found = client.post("/boxes/KB-01/checks", json={"seal": "44012", "read_on": WALKED})
    assert found.json()["state"] == "sealed"
    assert found.json()["checked_on"] == WALKED


def test_a_seal_that_does_not_match_opens_the_box(client):
    install(client)
    found = client.post("/boxes/KB-01/checks", json={"seal": "44099", "read_on": WALKED})
    assert found.json()["state"] == "opened"
    assert found.json()["opened_by"] == "nobody signed for it"


def test_a_reading_with_no_seal_is_a_422(client):
    install(client)
    found = client.post("/boxes/KB-01/checks", json={"seal": "", "read_on": WALKED})
    assert found.status_code == 422


def test_reporting_an_opening_names_who(client):
    install(client)
    assert opened(client).json()["opened_by"] == "fire service"


def test_an_opener_nobody_recognises_is_a_422(client):
    install(client)
    found = client.post(
        "/boxes/KB-01/openings",
        json={"opened_by": "the caretaker", "opened_on": WALKED},
    )
    assert found.status_code == 422


def test_a_box_cannot_be_opened_twice(client):
    install(client)
    opened(client)
    assert opened(client).status_code == 409


def test_an_open_box_can_be_found_empty(client):
    install(client)
    opened(client)
    found = client.post("/boxes/KB-01/emptied", json={"found_on": LATE, "note": "gone"})
    assert found.json()["state"] == "empty"


def test_a_sealed_box_cannot_be_found_empty(client):
    install(client)
    found = client.post("/boxes/KB-01/emptied", json={"found_on": LATE})
    assert found.status_code == 409


def test_resealing_puts_it_back(client):
    install(client)
    opened(client)
    found = client.post(
        "/boxes/KB-01/reseal",
        json={"seal": "44120", "symbol": "AA2", "sealed_on": LATE},
    )
    assert found.json()["state"] == "sealed"
    assert found.json()["seal"] == "44120"
    assert found.json()["opened_on"] is None


def test_resealing_with_the_same_number_is_a_409(client):
    install(client)
    opened(client)
    found = client.post(
        "/boxes/KB-01/reseal",
        json={"seal": "44012", "symbol": "AA1", "sealed_on": LATE},
    )
    assert found.status_code == 409


def test_taking_a_box_down_says_why(client):
    install(client)
    found = client.post("/boxes/KB-01/withdrawal", json={"taken_on": LATE, "note": ""})
    assert found.status_code == 422


def test_a_withdrawn_box_is_off_the_wall(client):
    install(client)
    found = client.post(
        "/boxes/KB-01/withdrawal", json={"taken_on": LATE, "note": "building came down"}
    )
    assert found.json()["state"] == "withdrawn"


def test_the_seal_history_carries_both_seals(client):
    install(client)
    opened(client)
    client.post("/boxes/KB-01/reseal", json={"seal": "44120", "symbol": "AA2", "sealed_on": LATE})
    body = client.get("/boxes/KB-01/seals", params={"on": LATE}).json()
    assert [row["seal"] for row in body["seals"]] == ["44012", "44120"]
    assert body["seals"][0]["broken_by"] == "fire service"
    assert body["seals"][1]["current"] is True


def test_which_key_was_in_the_box_on_a_day(client):
    install(client)
    opened(client)
    client.post("/boxes/KB-01/reseal", json={"seal": "44120", "symbol": "AA2", "sealed_on": LATE})
    assert client.get("/boxes/KB-01/key-on/2025-03-01").json()["symbol"] == "AA1"
    assert client.get("/boxes/KB-01/key-on/2025-12-02").json()["symbol"] == "AA2"


def test_a_day_before_the_box_went_up_has_no_key(client):
    install(client)
    assert client.get("/boxes/KB-01/key-on/2024-01-01").json()["symbol"] is None


def test_the_open_boxes_come_with_the_exposure(client):
    install(client)
    opened(client)
    body = client.get("/boxes/open").json()
    assert [row["number"] for row in body["open"]] == ["KB-01"]
    assert body["exposure"] == {"MOO": 1}


def test_a_sealed_campus_has_nothing_open(client):
    install(client)
    assert client.get("/boxes/open").json()["open"] == []


def test_the_buildings_with_no_box_are_named(client):
    install(client)
    assert client.get("/boxes/uncovered").json()["buildings"] == ["HEI", "VOS"]


def test_the_walk_round_lists_what_needs_doing(client):
    install(client)
    opened(client)
    body = client.get("/boxes/walk-round", params={"on": LATE}).json()
    assert [row["number"] for row in body["boxes"]] == ["KB-01"]
    assert body["overdue_reseal"] == ["KB-01"]

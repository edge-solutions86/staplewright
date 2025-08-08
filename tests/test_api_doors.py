import pytest
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.services.facilities import LockoutService

WALKED = "2025-05-12"
CALLED = "2025-05-12T21:40:00"
LATER = "2025-05-12T22:05:00"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    dbcampus.build(session)
    LockoutService(session).open_series()
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def call(client, opening: str = "MOO-214", **overrides):
    body = {"opening": opening, "keyholder": "38210", "called_at": CALLED}
    body.update(overrides)
    return client.post("/lockouts", json=body)


def attend(client, number: str = "L-00001"):
    return client.post(
        f"/lockouts/{number}/attendance",
        json={"technician": "rlaakso", "attended_at": LATER},
    )


def start(client, building: str = "MOO"):
    return client.post("/surveys", json={"building": building, "walked_on": WALKED})


def line(client, opening: str = "MOO-214", building: str = "MOO", **overrides):
    body = {"surveyor": "jkoivu", "condition": 1, "deficiencies": [], "note": ""}
    body.update(overrides)
    return client.put(f"/surveys/{building}/{WALKED}/{opening}", json=body)


def test_logging_a_call_gives_back_a_number(client):
    found = call(client)
    assert found.status_code == 201
    assert found.json()["number"] == "L-00001"
    assert found.json()["state"] == "logged"


def test_a_fresh_call_has_no_technician_and_no_times(client):
    body = call(client).json()
    assert body["technician"] == ""
    assert body["attended_at"] is None
    assert body["closed_at"] is None


def test_a_call_on_a_door_nobody_has_is_refused(client):
    assert call(client, "MOO-999").status_code == 404


def test_attending_puts_a_name_and_a_time_on_it(client):
    call(client)
    body = attend(client).json()
    assert body["state"] == "attended"
    assert body["technician"] == "rlaakso"
    assert body["attended_at"].startswith("2025-05-12T22:05")


def test_attending_a_call_nobody_logged_is_refused(client):
    assert attend(client, "L-09000").status_code == 404


def test_a_door_cannot_be_opened_before_anybody_attends(client):
    call(client)
    found = client.post(
        "/lockouts/L-00001/entry",
        json={"identified_by": "campus card", "closed_at": LATER},
    )
    assert found.status_code == 409


def test_opening_the_door_records_how_they_were_identified(client):
    call(client)
    attend(client)
    found = client.post(
        "/lockouts/L-00001/entry",
        json={"identified_by": "campus card", "closed_at": LATER},
    )
    assert found.json()["state"] == "opened"
    assert found.json()["identified_by"] == "campus card"


def test_an_identification_nobody_accepts_is_refused(client):
    call(client)
    attend(client)
    found = client.post(
        "/lockouts/L-00001/entry",
        json={"identified_by": "he seemed alright", "closed_at": LATER},
    )
    assert found.status_code == 422


def test_refusing_needs_a_reason_written_down(client):
    call(client)
    attend(client)
    found = client.post("/lockouts/L-00001/refusal", json={"closed_at": LATER, "note": ""})
    assert found.status_code == 422


def test_a_refusal_closes_the_call(client):
    call(client)
    attend(client)
    found = client.post(
        "/lockouts/L-00001/refusal",
        json={"closed_at": LATER, "note": "no card and not on the room list"},
    )
    assert found.json()["state"] == "refused"


def test_the_open_calls_are_the_ones_nobody_closed(client):
    call(client)
    call(client, "MOO-216")
    attend(client, "L-00002")
    client.post(
        "/lockouts/L-00002/entry",
        json={"identified_by": "campus card", "closed_at": LATER},
    )
    body = client.get("/lockouts").json()
    assert body["total"] == 2
    assert len(body["open"]) == 1
    assert "L-00001" in body["open"][0]


def test_starting_a_survey_names_the_day(client):
    found = start(client)
    assert found.status_code == 201
    assert found.json() == {"building": "MOO", "walked_on": WALKED}


def test_a_building_cannot_be_walked_twice_in_one_day(client):
    start(client)
    assert start(client).status_code == 409


def test_a_line_needs_a_survey_to_hang_off(client):
    assert line(client).status_code == 404


def test_a_line_comes_back_with_the_condition_in_words(client):
    start(client)
    body = line(client, condition=4, deficiencies=["frame or strike needs work first"]).json()
    assert body["condition"] == "broken"
    assert body["deficiencies"] == ["frame or strike needs work first"]


def test_sending_the_same_line_twice_leaves_one_reading(client):
    start(client)
    line(client, condition=2)
    line(client, condition=4, note="second look, it is worse than that")
    body = client.get(f"/surveys/MOO/{WALKED}").json()
    assert body["doors"] == 1
    assert body["conditions"]["broken"] == 1


def test_a_deficiency_nobody_recognises_is_refused(client):
    start(client)
    assert line(client, deficiencies=["the door is haunted"]).status_code == 422


def test_the_survey_reads_back_the_blocking_doors(client):
    start(client)
    line(client, condition=1)
    line(
        client,
        "MOO-216",
        condition=4,
        deficiencies=["hardware needs replacing before it is rekeyed"],
    )
    body = client.get(f"/surveys/MOO/{WALKED}").json()
    assert body["doors"] == 2
    assert body["blocking"] == ["MOO-216"]


def test_a_survey_nobody_walked_is_a_404(client):
    assert client.get(f"/surveys/MOO/{WALKED}").status_code == 404


def test_the_latest_survey_is_the_one_most_recently_walked(client):
    start(client)
    client.post("/surveys", json={"building": "MOO", "walked_on": "2025-09-30"})
    body = client.get("/buildings/MOO/latest-survey").json()
    assert body["walked_on"] == "2025-09-30"


def test_a_building_nobody_walked_says_so_without_failing(client):
    body = client.get("/buildings/VOS/latest-survey").json()
    assert body == {"building": "VOS", "walked_on": None, "doors": 0}

import pytest
from fastapi.testclient import TestClient

from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.persistence.mappers.control import authorizer_to_row, keyholder_to_row
from hasplib.services.counter import CounterService
from people import TODAY, a_directory

WHEN = TODAY.isoformat()
LATER = "2025-06-02"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    directory = a_directory()
    for person in directory:
        session.add(keyholder_to_row(person))
    session.flush()
    for signer in directory.authorizers():
        session.add(authorizer_to_row(signer))
    session.flush()
    CounterService(session).open_series()
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def raise_request(client, **overrides):
    body = {
        "keyholder": "kmaki",
        "symbol": "AA1",
        "building": "MOO",
        "reason": "moved into 214",
        "raised_on": WHEN,
    }
    body.update(overrides)
    return client.post("/requests", json=body)


def approved(client):
    number = raise_request(client).json()["number"]
    client.post(f"/requests/{number}/approval", json={"signer": "rvirta", "decided_on": WHEN})
    return number


def test_a_request_comes_back_with_its_number(client):
    found = raise_request(client)
    assert found.status_code == 201
    assert found.json()["number"] == "R-0001"
    assert found.json()["state"] == "submitted"


def test_a_request_needs_a_reason(client):
    assert raise_request(client, reason="").status_code == 422


def test_a_request_for_somebody_who_is_not_there(client):
    assert raise_request(client, keyholder="ghost").status_code == 404


def test_a_request_reads_back(client):
    number = raise_request(client).json()["number"]
    found = client.get(f"/requests/{number}")
    assert found.json()["symbol"] == "AA1"


def test_a_request_that_is_not_there(client):
    assert client.get("/requests/R-9999").status_code == 404


def test_the_right_signature_approves(client):
    number = raise_request(client).json()["number"]
    found = client.post(
        f"/requests/{number}/approval", json={"signer": "rvirta", "decided_on": WHEN}
    )
    assert found.json()["state"] == "approved"
    assert found.json()["decided_by"] == "rvirta"


def test_a_signature_that_does_not_reach_is_a_conflict(client):
    number = raise_request(client).json()["number"]
    found = client.post(
        f"/requests/{number}/approval", json={"signer": "phamm", "decided_on": WHEN}
    )
    assert found.status_code == 409


def test_a_denial_needs_a_note(client):
    number = raise_request(client).json()["number"]
    found = client.post(f"/requests/{number}/denial", json={"signer": "rvirta", "decided_on": WHEN})
    assert found.status_code == 422


def test_a_denial_with_a_note_lands(client):
    number = raise_request(client).json()["number"]
    body = {"signer": "rvirta", "decided_on": WHEN, "note": "the room is shared"}
    found = client.post(f"/requests/{number}/denial", json=body)
    assert found.json()["state"] == "denied"


def test_issuing_gives_back_a_tag_and_a_deposit(client):
    number = approved(client)
    found = client.post(f"/requests/{number}/key", json={"issued_on": WHEN})
    assert found.status_code == 201
    assert found.json()["tag"] == "41000"
    assert found.json()["deposit_cents"] == 2500


def test_a_deposit_can_be_waived(client):
    number = approved(client)
    body = {"issued_on": WHEN, "deposit_cents": 0}
    assert client.post(f"/requests/{number}/key", json=body).json()["deposit_cents"] == 0


def test_a_negative_deposit_never_reaches_the_service(client):
    number = approved(client)
    body = {"issued_on": WHEN, "deposit_cents": -1}
    assert client.post(f"/requests/{number}/key", json=body).status_code == 422


def test_an_unapproved_request_is_not_filled(client):
    number = raise_request(client).json()["number"]
    found = client.post(f"/requests/{number}/key", json={"issued_on": WHEN})
    assert found.status_code == 409


def test_a_key_reads_back(client):
    number = approved(client)
    tag = client.post(f"/requests/{number}/key", json={"issued_on": WHEN}).json()["tag"]
    assert client.get(f"/keys/{tag}").json()["state"] == "out"


def test_a_returned_key_says_so(client):
    number = approved(client)
    tag = client.post(f"/requests/{number}/key", json={"issued_on": WHEN}).json()["tag"]
    found = client.post(f"/keys/{tag}/return", json={"closed_on": LATER})
    assert found.json()["state"] == "returned"


def test_a_lost_key_says_so(client):
    number = approved(client)
    tag = client.post(f"/requests/{number}/key", json={"issued_on": WHEN}).json()["tag"]
    body = {"closed_on": LATER, "note": "left on a bus"}
    assert client.post(f"/keys/{tag}/loss", json=body).json()["state"] == "lost"


def test_a_key_cannot_come_back_twice(client):
    number = approved(client)
    tag = client.post(f"/requests/{number}/key", json={"issued_on": WHEN}).json()["tag"]
    client.post(f"/keys/{tag}/return", json={"closed_on": LATER})
    assert client.post(f"/keys/{tag}/return", json={"closed_on": LATER}).status_code == 409


def test_holdings_list_what_somebody_has(client):
    number = approved(client)
    client.post(f"/requests/{number}/key", json={"issued_on": WHEN})
    found = client.get("/keyholders/kmaki/keys")
    assert [key["tag"] for key in found.json()["keys"]] == ["41000"]
    assert found.json()["deposits_held"] == 2500


def test_somebody_holding_nothing_lists_nothing(client):
    found = client.get("/keyholders/dsalo/keys")
    assert found.json()["keys"] == []
    assert found.json()["deposits_held"] == 0

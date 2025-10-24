from datetime import date

import pytest
from fastapi.testclient import TestClient

from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.services.transfer import TransferService

ISSUED = "2025-01-06"
CLAIMED = "2025-03-04"
SOON = "2025-03-10"
LATE = "2025-05-01"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    for name in ("kmaki", "rlaakso", "jkoivu"):
        session.add(
            KeyholderRow(
                identifier=name,
                name=name,
                affiliation="staff",
                department="grounds",
                standing="active",
            )
        )
    session.flush()
    session.add_all(
        [
            IssuedKeyRow(
                tag="41207",
                symbol="AA1",
                keyholder="kmaki",
                issued_on=date(2025, 1, 6),
                due_on=date(2026, 1, 6),
                state="out",
            ),
            IssuedKeyRow(
                tag="41211",
                symbol="A",
                keyholder="kmaki",
                issued_on=date(2025, 1, 6),
                due_on=date(2026, 1, 6),
                state="out",
            ),
        ]
    )
    TransferService(session).open_series()
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def claim(client, tag: str = "41207", passed_to: str = "rlaakso"):
    return client.post(
        "/handovers",
        json={"tag": tag, "passed_to": passed_to, "claimed_on": CLAIMED},
    )


def test_a_claim_gets_a_number(client):
    found = claim(client)
    assert found.status_code == 201
    assert found.json()["number"] == "T-00001"
    assert found.json()["state"] == "claimed"


def test_a_claim_names_who_the_ledger_says_had_it(client):
    assert claim(client).json()["passed_by"] == "kmaki"


def test_a_key_nobody_issued_is_a_404(client):
    assert claim(client, "99999").status_code == 404


def test_a_master_does_not_move_between_people(client):
    assert claim(client, "41211").status_code == 409


def test_a_handover_to_somebody_nobody_has_is_a_404(client):
    assert claim(client, passed_to="nobody").status_code == 404


def test_one_key_cannot_be_claimed_twice(client):
    claim(client)
    assert claim(client, passed_to="jkoivu").status_code == 409


def test_a_claim_does_not_move_the_key(client):
    claim(client)
    body = client.get("/keys/41207/holder", params={"on": LATE}).json()
    assert body["holder"] == "kmaki"
    assert body["chain"] == []


def test_confirming_moves_the_key(client):
    claim(client)
    found = client.post(
        "/handovers/T-00001/confirmation",
        json={"confirmed_on": SOON, "note": "picked it up"},
    )
    assert found.json()["state"] == "confirmed"
    body = client.get("/keys/41207/holder", params={"on": LATE}).json()
    assert body["holder"] == "rlaakso"
    assert body["chain"] == ["rlaakso"]


def test_a_refusal_needs_a_reason(client):
    claim(client)
    found = client.post("/handovers/T-00001/refusal", json={"refused_on": SOON, "note": ""})
    assert found.status_code == 422


def test_a_refusal_leaves_the_key_where_it_was(client):
    claim(client)
    client.post("/handovers/T-00001/refusal", json={"refused_on": SOON, "note": "never got it"})
    assert client.get("/keys/41207/holder", params={"on": LATE}).json()["holder"] == "kmaki"


def test_a_claim_can_be_taken_back(client):
    claim(client)
    found = client.post("/handovers/T-00001/cancellation", json={"cancelled_on": SOON})
    assert found.json()["state"] == "cancelled"


def test_a_handover_nobody_claimed_is_a_404(client):
    assert client.get("/handovers/T-99999").status_code == 404


def test_the_open_claims_are_listed(client):
    claim(client)
    body = client.get("/handovers").json()
    assert [row["number"] for row in body["handovers"]] == ["T-00001"]


def test_a_state_nobody_recognises_is_a_422(client):
    assert client.get("/handovers", params={"state": "posted"}).status_code == 422


def test_the_settled_claims_can_be_listed_too(client):
    claim(client)
    client.post("/handovers/T-00001/confirmation", json={"confirmed_on": SOON})
    body = client.get("/handovers", params={"state": "confirmed"}).json()
    assert [row["number"] for row in body["handovers"]] == ["T-00001"]


def test_the_late_claims_come_with_the_exposure(client):
    claim(client)
    body = client.get("/handovers/late", params={"on": LATE}).json()
    assert len(body["late"]) == 1
    assert body["exposure"] == {"rlaakso": 1}


def test_nothing_is_late_inside_the_window(client):
    claim(client)
    assert client.get("/handovers/late", params={"on": SOON}).json()["late"] == []


def test_the_strays_name_the_ledger_and_the_truth(client):
    claim(client)
    client.post("/handovers/T-00001/confirmation", json={"confirmed_on": SOON})
    body = client.get("/handovers/strays", params={"on": LATE}).json()
    assert body["strays"] == [{"tag": "41207", "ledger": "kmaki", "actually": "rlaakso"}]


def test_a_shop_with_no_handovers_has_no_strays(client):
    assert client.get("/handovers/strays", params={"on": LATE}).json()["strays"] == []


def test_the_sweep_closes_every_late_claim(client):
    claim(client)
    body = client.post("/handovers/lapse", params={"on": LATE}).json()
    assert body["lapsed"] == ["T-00001"]
    assert client.get("/handovers/T-00001").json()["state"] == "lapsed"


def test_the_sweep_leaves_the_recent_claims_alone(client):
    claim(client)
    assert client.post("/handovers/lapse", params={"on": SOON}).json()["count"] == 0


def test_what_a_person_actually_holds(client):
    claim(client)
    client.post("/handovers/T-00001/confirmation", json={"confirmed_on": SOON})
    body = client.get("/keyholders/rlaakso/holdings", params={"on": LATE}).json()
    assert body["tags"] == ["41207"]


def test_what_a_person_still_owes_a_signature_on(client):
    claim(client)
    body = client.get("/keyholders/rlaakso/holdings", params={"on": SOON}).json()
    assert body["waiting_on"] == ["T-00001"]


def test_the_self_check_reports_a_key_that_wandered(client):
    claim(client)
    client.post("/handovers/T-00001/confirmation", json={"confirmed_on": SOON})
    from datetime import date as day

    from hasplib.health import health_report

    with client.app.state.shop.sessions() as session:
        lines = health_report(session, day(2025, 5, 1))
    assert any("keys that changed hands" in line for line in lines)


def test_the_self_check_reports_a_claim_nobody_confirmed(client):
    claim(client)
    from datetime import date as day

    from hasplib.health import exit_code, run_checks

    with client.app.state.shop.sessions() as session:
        findings = run_checks(session, day(2025, 5, 1))
    assert exit_code(findings) == 1
    assert any(item.check == "handovers nobody confirmed" for item in findings)

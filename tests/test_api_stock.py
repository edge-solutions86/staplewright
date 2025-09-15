import pytest
from fastapi.testclient import TestClient

from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.persistence.models.reference import BlankRow, KeywaySectionRow

WHEN = "2025-04-08"
LATER = "2025-05-20"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    section = KeywaySectionRow(family_name="Sargent LA", code="L", manufacturer="Sargent")
    session.add(section)
    session.flush()
    session.add_all(
        [
            BlankRow(
                part_number="1145A",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=True,
                reorder_at=50,
                stock=200,
            ),
            BlankRow(
                part_number="RA4",
                section_id=section.id,
                manufacturer="Sargent",
                chambers=6,
                restricted=False,
                reorder_at=20,
                stock=40,
            ),
        ]
    )
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def test_the_blanks_come_back_in_part_number_order(client):
    body = client.get("/stock/blanks").json()
    assert [row["part_number"] for row in body["blanks"]] == ["1145A", "RA4"]


def test_only_the_restricted_parts_when_asked(client):
    body = client.get("/stock/blanks", params={"restricted": True}).json()
    assert [row["part_number"] for row in body["blanks"]] == ["1145A"]


def test_one_blank_carries_both_totals(client):
    body = client.get("/stock/blanks/1145A").json()
    assert body["stock"] == 200
    assert body["ledger_total"] == 0


def test_a_part_nobody_stocks_is_a_404(client):
    assert client.get("/stock/blanks/9999Z").status_code == 404


def test_receiving_brass_leaves_a_movement(client):
    found = client.post(
        "/stock/blanks/1145A/receipts",
        json={"quantity": 100, "moved_on": WHEN, "reference": "PO-4412"},
    )
    assert found.status_code == 201
    assert found.json()["signed"] == 100
    assert client.get("/stock/blanks/1145A").json()["stock"] == 300


def test_receiving_nothing_is_rejected_before_it_reaches_the_service(client):
    found = client.post("/stock/blanks/1145A/receipts", json={"quantity": 0, "moved_on": WHEN})
    assert found.status_code == 422


def test_cutting_a_restricted_blank_needs_a_reference(client):
    found = client.post("/stock/blanks/1145A/cuts", json={"quantity": 2, "moved_on": LATER})
    assert found.status_code == 409


def test_cutting_with_a_reference_takes_it_out(client):
    found = client.post(
        "/stock/blanks/1145A/cuts",
        json={"quantity": 2, "moved_on": LATER, "reference": "41207"},
    )
    assert found.json()["signed"] == -2


def test_cutting_an_open_blank_needs_nothing(client):
    found = client.post("/stock/blanks/RA4/cuts", json={"quantity": 2, "moved_on": LATER})
    assert found.status_code == 201


def test_cutting_more_than_the_drawer_holds_is_a_409(client):
    found = client.post("/stock/blanks/RA4/cuts", json={"quantity": 41, "moved_on": LATER})
    assert found.status_code == 409
    assert found.json()["facts"]["held"] == 40


def test_scrapping_says_why_or_it_is_rejected(client):
    found = client.post(
        "/stock/blanks/RA4/scrappings", json={"quantity": 1, "moved_on": LATER, "note": ""}
    )
    assert found.status_code == 422


def test_scrapping_with_a_reason_takes_it_off(client):
    found = client.post(
        "/stock/blanks/RA4/scrappings",
        json={"quantity": 1, "moved_on": LATER, "note": "milled wrong"},
    )
    assert found.json()["signed"] == -1


def test_a_blank_that_comes_back_goes_in_again(client):
    found = client.post("/stock/blanks/RA4/returns", json={"quantity": 1, "moved_on": LATER})
    assert found.json()["movement"] == "returned"


def test_a_count_reports_the_gap(client):
    found = client.post(
        "/stock/blanks/1145A/count", json={"counted": 188, "moved_on": LATER}
    ).json()
    assert found["expected"] == 200
    assert found["difference"] == -12
    assert found["agrees"] is False


def test_a_count_that_agrees_says_so(client):
    found = client.post("/stock/blanks/RA4/count", json={"counted": 40, "moved_on": LATER}).json()
    assert found["agrees"] is True
    assert found["note"].endswith("agrees")


def test_a_count_moves_the_total(client):
    client.post("/stock/blanks/1145A/count", json={"counted": 188, "moved_on": LATER})
    assert client.get("/stock/blanks/1145A").json()["stock"] == 188


def test_the_movements_come_back_in_order(client):
    client.post("/stock/blanks/RA4/cuts", json={"quantity": 1, "moved_on": LATER})
    client.post("/stock/blanks/RA4/returns", json={"quantity": 1, "moved_on": LATER})
    body = client.get("/stock/blanks/RA4/movements").json()
    assert [row["movement"] for row in body["movements"]] == ["cut", "returned"]


def test_the_reorder_list_names_what_is_short(client):
    client.post("/stock/blanks/RA4/cuts", json={"quantity": 25, "moved_on": LATER})
    body = client.get("/stock/reorder").json()
    assert [row["part_number"] for row in body["below"]] == ["RA4"]


def test_what_was_cut_against_a_tag_comes_back(client):
    client.post(
        "/stock/blanks/1145A/cuts",
        json={"quantity": 1, "moved_on": LATER, "reference": "41207"},
    )
    body = client.get("/stock/cut-against/41207").json()
    assert [row["reference"] for row in body["cuts"]] == ["41207"]


def test_an_order_goes_in_with_its_lines(client):
    found = client.post(
        "/stock/orders",
        json={
            "number": "PO-9001",
            "supplier": "lockwood supply",
            "placed_on": WHEN,
            "lines": [{"part_number": "1145A", "quantity": 250}],
        },
    )
    assert found.status_code == 201
    assert found.json()["state"] == "placed"
    assert found.json()["lines"][0]["outstanding"] == 250


def test_an_order_with_no_lines_is_rejected(client):
    found = client.post(
        "/stock/orders",
        json={
            "number": "PO-9001",
            "supplier": "lockwood supply",
            "placed_on": WHEN,
            "lines": [],
        },
    )
    assert found.status_code == 422


def an_order(client, number: str = "PO-9001", quantity: int = 250):
    return client.post(
        "/stock/orders",
        json={
            "number": number,
            "supplier": "lockwood supply",
            "placed_on": WHEN,
            "lines": [{"part_number": "1145A", "quantity": quantity}],
        },
    )


def test_an_order_number_is_not_reused(client):
    an_order(client)
    assert an_order(client).status_code == 409


def test_a_part_delivery_leaves_the_order_open(client):
    an_order(client)
    found = client.post(
        "/stock/orders/PO-9001/deliveries",
        json={"part_number": "1145A", "quantity": 100, "arrived_on": LATER},
    ).json()
    assert found["state"] == "part received"
    assert found["lines"][0]["outstanding"] == 150


def test_the_last_delivery_closes_the_order(client):
    an_order(client, quantity=10)
    found = client.post(
        "/stock/orders/PO-9001/deliveries",
        json={"part_number": "1145A", "quantity": 10, "arrived_on": LATER},
    ).json()
    assert found["state"] == "received"


def test_more_arriving_than_was_ordered_is_a_409(client):
    an_order(client, quantity=10)
    found = client.post(
        "/stock/orders/PO-9001/deliveries",
        json={"part_number": "1145A", "quantity": 11, "arrived_on": LATER},
    )
    assert found.status_code == 409


def test_only_the_open_orders_are_listed(client):
    an_order(client, "PO-9001", 10)
    an_order(client, "PO-9002", 10)
    client.post(
        "/stock/orders/PO-9002/deliveries",
        json={"part_number": "1145A", "quantity": 10, "arrived_on": LATER},
    )
    body = client.get("/stock/orders").json()
    assert [row["number"] for row in body["orders"]] == ["PO-9001"]


def test_cancelling_needs_a_reason(client):
    an_order(client)
    assert client.post("/stock/orders/PO-9001/cancellation", json={"note": ""}).status_code == 422


def test_a_cancelled_order_says_so(client):
    an_order(client)
    found = client.post(
        "/stock/orders/PO-9001/cancellation", json={"note": "supplier cannot get it"}
    )
    assert found.json()["state"] == "cancelled"


def test_an_order_nobody_placed_is_a_404(client):
    assert client.get("/stock/orders/PO-0000").status_code == 404

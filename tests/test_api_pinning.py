import pytest
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app

SYSTEM = "chester park"
GROUP = "moore offices"
KIT = {
    "bottoms": [150 + 15 * step for step in range(10)],
    "wafers": [15 * step for step in range(1, 10)],
    "drivers": [100 + 5 * step for step in range(80)],
    "count": 40,
}


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


def order(**overrides):
    body = {"system": SYSTEM, "group": GROUP, "kit": KIT}
    body.update(overrides)
    return body


def test_the_stacks_come_back_a_chamber_at_a_time(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}").json()
    assert body["chambers"] == 6
    assert [stack["chamber"] for stack in body["stacks"]] == [1, 2, 3, 4, 5, 6]


def test_every_stack_reaches_the_shear_line_exactly(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}").json()
    assert {stack["height"] for stack in body["stacks"]} == {480}


def test_the_breaks_start_at_the_bottom_pin(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}").json()
    for stack in body["stacks"]:
        assert stack["breaks"][0] == stack["bottom"]


def test_the_mastered_chambers_are_the_ones_with_wafers(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}").json()
    wafered = [stack["chamber"] for stack in body["stacks"] if stack["wafers"]]
    assert body["mastered"] == wafered


def test_spooling_reaches_every_driver(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}", params={"profile": "spool"}).json()
    assert {stack["profile"] for stack in body["stacks"]} == {"spool"}


def test_a_family_nobody_stocks_is_refused(client):
    found = client.get(f"/pinning/{SYSTEM}/{GROUP}", params={"manufacturer": "Yale"})
    assert found.status_code == 404


def test_another_family_gives_another_column(client):
    body = client.get(
        f"/pinning/{SYSTEM}/{GROUP}", params={"manufacturer": "Sargent", "family": "LA six pin"}
    ).json()
    assert {stack["height"] for stack in body["stacks"]} == {480}


def test_a_group_nobody_defined_is_a_404(client):
    assert client.get(f"/pinning/{SYSTEM}/nothing at all").status_code == 404


def test_a_system_nobody_opened_is_a_404(client):
    assert client.get(f"/pinning/nowhere/{GROUP}").status_code == 404


def test_the_chart_comes_back_as_lines(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}/chart", params={"serial": "C-1001"}).json()
    assert body["lines"][0] == "moore offices  C-1001"


def test_the_chart_comes_back_as_paper_when_asked(client):
    found = client.get(f"/pinning/{SYSTEM}/{GROUP}/chart", params={"plain": True})
    assert found.headers["content-type"].startswith("text/plain")
    assert found.text.splitlines()[0] == "moore offices"


def test_the_keys_come_back_in_schedule_order(client):
    body = client.get(f"/pinning/{SYSTEM}/{GROUP}/keys").json()
    assert body["symbols"] == ["GGM", "A", "AA", "AA1"]
    assert body["bittings"] == ["442446", "642446", "622446", "620446"]


def test_the_cylinders_in_a_group_come_back_with_where_they_are(client):
    body = client.get(f"/pinning/groups/{GROUP}/cylinders").json()
    assert [row["serial"] for row in body["cylinders"]] == ["C-1001", "C-1002"]
    assert body["cylinders"][0]["installed_at"]


def test_drawing_brass_takes_it_out_of_the_kit(client):
    body = client.post("/pinning/draw", json=order()).json()
    assert body["drawn"] == body["pieces"]
    assert body["left"] == 40 * (10 + 9 + 80) - body["drawn"]


def test_the_draw_counts_the_bins_it_emptied(client):
    body = client.post("/pinning/draw", json=order()).json()
    assert body["bins"]["bottom"] == 6
    assert body["bins"]["driver"] == 6
    assert body["bins"]["bottom"] + body["bins"]["driver"] + body["bins"]["wafer"] == body["pieces"]


def test_a_kit_that_cannot_fill_it_is_a_409(client):
    thin = {"bottoms": [195], "wafers": [30], "drivers": [285], "count": 1}
    found = client.post("/pinning/draw", json=order(kit=thin))
    assert found.status_code == 409
    assert found.json()["message"] == "the kit cannot fill that"


def test_a_kit_with_an_empty_bin_list_is_refused(client):
    thin = {"bottoms": [], "wafers": [30], "drivers": [285], "count": 1}
    assert client.post("/pinning/draw", json=order(kit=thin)).status_code == 422


def test_drawing_for_a_group_nobody_defined_is_a_404(client):
    assert client.post("/pinning/draw", json=order(group="nothing at all")).status_code == 404

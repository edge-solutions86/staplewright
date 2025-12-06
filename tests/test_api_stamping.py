import pytest
from click.testing import CliRunner
from fastapi.testclient import TestClient

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib.api.app import build_app
from hasplib.cli.main import cli
from hasplib.errors import InputError
from hasplib.keys.stamping import Marking
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.settings import DEFAULTS, from_mapping

TODAY = "2025-06-30"


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


def test_the_stamping_policy_comes_off_the_environment():
    settings = from_mapping(
        {
            "HASPLIB_STAMP_CHANGE_KEYS": "blind",
            "HASPLIB_STAMP_MASTERS": "nothing",
            "HASPLIB_STAMP_DND": "no",
            "HASPLIB_BOW_CHARACTERS": "6",
        }
    )
    assert settings.stamping.change_keys is Marking.BLIND
    assert settings.stamping.masters is Marking.NOTHING
    assert settings.stamping.add_do_not_duplicate is False
    assert settings.stamping.bow_characters == 6


def test_a_marking_can_be_named_the_long_way():
    settings = from_mapping({"HASPLIB_STAMP_MASTERS": "the key symbol"})
    assert settings.stamping.masters is Marking.SYMBOL


def test_a_marking_nobody_recognises_is_refused():
    with pytest.raises(InputError):
        from_mapping({"HASPLIB_STAMP_MASTERS": "engraved"})


def test_a_bow_that_holds_nothing_is_refused():
    with pytest.raises(InputError):
        from_mapping({"HASPLIB_BOW_CHARACTERS": "2"})


def test_the_description_says_what_gets_stamped():
    assert any(line.startswith("stamping:") for line in DEFAULTS.describe())


def test_assigning_a_code_gives_a_number(client):
    found = client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    assert found.status_code == 201
    assert found.json() == {"symbol": "AA", "code": "4000"}


def test_a_symbol_keeps_the_code_it_was_given(client):
    client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    again = client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    assert again.json()["code"] == "4000"


def test_a_number_reads_back_as_a_symbol(client):
    client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    assert client.get("/stamping/identify/4000").json()["symbol"] == "AA"


def test_a_number_nobody_stamped_is_a_404(client):
    assert client.get("/stamping/identify/9999").status_code == 404


def test_a_symbol_nobody_stamped_has_no_code(client):
    assert client.get("/stamping/codes/AA").status_code == 404


def test_the_symbols_out_are_listed(client):
    assert client.get("/stamping/symbols").json()["out"] == ["A", "AA", "AA1", "GGM"]


def test_the_default_policy_leaves_nothing_blank(client):
    assert client.get("/stamping/blank").json()["tags"] == []


def test_one_key_comes_back_with_what_goes_on_it(client):
    body = client.get("/stamping/keys/41207", params={"on": TODAY}).json()
    assert body["text"] == "AA1"
    assert body["blind"] is False


def test_a_master_key_comes_back_blind(client):
    body = client.get("/stamping/keys/41211", params={"on": TODAY}).json()
    assert body["blind"] is True
    assert body["text"] == "4000"


def test_a_key_nobody_issued_is_a_404(client):
    assert client.get("/stamping/keys/99999").status_code == 404


def test_a_run_comes_back_in_tag_order(client):
    found = client.post("/stamping/runs", json={"tags": ["41211", "41207"], "stamped_on": TODAY})
    assert [stamp["tag"] for stamp in found.json()["stamps"]] == ["41207", "41211"]


def test_a_run_with_no_tags_is_a_422(client):
    assert client.post("/stamping/runs", json={"tags": [], "stamped_on": TODAY}).status_code == 422


def test_the_sheet_covers_every_key_out(client):
    body = client.get("/stamping/sheet", params={"on": TODAY}).json()
    assert body["lines"][0] == "5 keys to stamp"


def test_the_whole_book_warns_before_it_prints(client):
    client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    body = client.get("/stamping/book").json()
    assert body["warning"].startswith("this table turns")
    assert body["codes"] == [{"symbol": "AA", "code": "4000"}]


def test_a_code_still_on_a_key_cannot_be_retired(client):
    client.post("/stamping/codes", json={"symbol": "AA", "assigned_on": TODAY})
    assert client.delete("/stamping/codes/AA").status_code == 409


def test_a_code_nobody_carries_can_be_retired(client):
    client.post("/stamping/codes", json={"symbol": "AB1", "assigned_on": TODAY})
    assert client.delete("/stamping/codes/AB1").json()["retired"] is True


def test_the_identify_command_answers_a_found_key(shop):
    call(shop, "stamping", "assign", "AA", "--on", TODAY)
    assert call(shop, "stamping", "identify", "4000").output.strip() == "AA"


def test_the_identify_command_refuses_a_number_nobody_stamped(shop):
    assert call(shop, "stamping", "identify", "9999").exit_code != 0


def test_the_assign_command_prints_the_pair(shop):
    found = call(shop, "stamping", "assign", "AA", "--on", TODAY)
    assert found.output.strip() == "AA is 4000"


def test_the_code_command_reads_it_back(shop):
    call(shop, "stamping", "assign", "AA", "--on", TODAY)
    assert call(shop, "stamping", "code", "AA").output.strip() == "4000"


def test_the_key_command_describes_one_stamp(shop):
    found = call(shop, "stamping", "key", "41207", "--on", TODAY)
    assert found.output.strip() == "41207 AA1 (the key symbol)"


def test_the_run_command_prints_a_sheet(shop):
    lines = call(shop, "stamping", "run", "41207", "41211", "--on", TODAY).output.splitlines()
    assert lines[0] == "2 keys to stamp"


def test_the_everything_command_covers_the_keys_out(shop):
    lines = call(shop, "stamping", "everything", "--on", TODAY).output.splitlines()
    assert len(lines) == 5


def test_the_blank_command_is_quiet_by_default(shop):
    assert "every key out carries something" in call(shop, "stamping", "blank").output


def test_the_symbols_command_lists_what_needs_a_code(shop):
    assert call(shop, "stamping", "symbols").output.splitlines() == ["A", "AA", "AA1", "GGM"]


def test_the_retire_command_refuses_while_keys_carry_it(shop):
    call(shop, "stamping", "assign", "AA", "--on", TODAY)
    assert call(shop, "stamping", "retire", "AA").exit_code != 0


def test_the_whole_book_command_needs_confirming(shop):
    call(shop, "stamping", "assign", "AA", "--on", TODAY)
    found = CliRunner().invoke(cli, ["--database", shop, "stamping", "the-whole-book"], input="n\n")
    assert "4000" not in found.output


def test_the_whole_book_command_warns_first(shop):
    call(shop, "stamping", "assign", "AA", "--on", TODAY)
    found = CliRunner().invoke(cli, ["--database", shop, "stamping", "the-whole-book"], input="y\n")
    assert "turns every stamped number into a symbol" in found.output
    assert "4000" in found.output

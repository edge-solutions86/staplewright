import pytest
from click.testing import CliRunner

import dbcampus
from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema

ISSUED = "2025-01-06"
LATER = "2025-12-01"
CUT = "3572461"


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


def a_key(shop, code: str = "CK-1", section: str = "A"):
    return call(shop, "control", "cut", code, section, CUT, "--on", ISSUED)


def a_core(shop, serial: str = "K-1001", code: str = "CK-1", housing: str = "mortise"):
    return call(shop, "control", "core", serial, code, "--housing", housing)


def test_cutting_a_control_key_says_so(shop):
    assert a_key(shop).output.strip() == "CK-1 cut on A"


def test_a_code_is_not_reused(shop):
    a_key(shop)
    assert a_key(shop).exit_code != 0


def test_a_bitting_nobody_can_read_is_refused(shop):
    found = call(shop, "control", "cut", "CK-1", "A", "not a cut", "--on", ISSUED)
    assert found.exit_code != 0


def test_a_format_nobody_makes_is_rejected_by_click(shop):
    found = call(shop, "control", "cut", "CK-1", "A", CUT, "--format", "medium", "--on", ISSUED)
    assert found.exit_code != 0


def test_handing_a_key_over_says_who(shop):
    a_key(shop)
    assert call(shop, "control", "hand-to", "CK-1", "phamm").output.strip() == "CK-1 is with phamm"


def test_withdrawing_a_key_says_so(shop):
    a_key(shop)
    assert call(shop, "control", "withdraw", "CK-1", "--on", LATER).output.strip() == (
        "CK-1 withdrawn"
    )


def test_a_key_with_cores_cannot_be_withdrawn(shop):
    a_key(shop)
    a_core(shop)
    assert call(shop, "control", "withdraw", "CK-1", "--on", LATER).exit_code != 0


def test_a_core_goes_on_the_register(shop):
    a_key(shop)
    assert a_core(shop).output.strip() == "K-1001 on the register, pulled by CK-1"


def test_a_core_needs_a_control_key_that_exists(shop):
    assert a_core(shop).exit_code != 0


def test_a_core_goes_into_a_door(shop):
    a_key(shop)
    a_core(shop)
    found = call(shop, "control", "install", "K-1001", "MOO-101")
    assert found.output.strip() == "K-1001 is in MOO-101"


def test_a_housing_only_takes_one_core(shop):
    a_key(shop)
    a_core(shop, "K-1001")
    a_core(shop, "K-1002")
    call(shop, "control", "install", "K-1001", "MOO-101")
    assert call(shop, "control", "install", "K-1002", "MOO-101").exit_code != 0


def test_pulling_a_core_takes_it_off_the_wall(shop):
    a_key(shop)
    a_core(shop)
    call(shop, "control", "install", "K-1001", "MOO-101")
    assert call(shop, "control", "pull", "K-1001").output.strip() == "K-1001 is off the wall"


def test_a_core_already_off_the_wall_cannot_be_pulled(shop):
    a_key(shop)
    a_core(shop)
    assert call(shop, "control", "pull", "K-1001").exit_code != 0


def test_an_empty_register_says_so(shop):
    assert "no control keys on the register" in call(shop, "control", "keys").output


def test_the_keys_are_listed_with_who_has_them(shop):
    a_key(shop)
    call(shop, "control", "hand-to", "CK-1", "phamm")
    assert "held by phamm" in call(shop, "control", "keys").output


def test_no_control_key_out_says_so(shop):
    assert "no control key is out" in call(shop, "control", "holders").output


def test_the_holders_are_counted(shop):
    a_key(shop)
    call(shop, "control", "hand-to", "CK-1", "phamm")
    lines = call(shop, "control", "holders").output.splitlines()
    assert lines[0] == "1 control keys are out"


def test_an_empty_core_register_says_so(shop):
    assert "no cores on the register" in call(shop, "control", "cores").output


def test_the_cores_list_says_where_each_one_is(shop):
    a_key(shop)
    a_core(shop)
    call(shop, "control", "install", "K-1001", "MOO-101")
    assert "MOO-101" in call(shop, "control", "cores").output


def test_a_shelved_core_says_so(shop):
    a_key(shop)
    a_core(shop)
    assert "on the shelf" in call(shop, "control", "cores").output


def test_the_cores_can_be_narrowed_to_one_key(shop):
    a_key(shop)
    a_key(shop, "CK-2", "B")
    a_core(shop, "K-1001", "CK-1")
    a_core(shop, "K-1002", "CK-2")
    output = call(shop, "control", "cores", "CK-2").output
    assert "K-1002" in output
    assert "K-1001" not in output


def test_one_control_section_crosses_nothing(shop):
    a_key(shop)
    a_core(shop, "K-1001")
    a_core(shop, "K-1002")
    output = call(shop, "control", "crossings").output
    assert "no core can be moved across a control key" in output


def test_two_control_keys_on_one_housing_kind_cross(shop):
    a_key(shop)
    a_key(shop, "CK-2", "B")
    a_core(shop, "K-1001", "CK-1")
    a_core(shop, "K-1002", "CK-2")
    output = call(shop, "control", "crossings").output
    assert "more than one control section" in output
    assert "K-1001 and K-1002" in output


def test_different_housings_do_not_cross(shop):
    a_key(shop)
    a_key(shop, "CK-2", "B")
    a_core(shop, "K-1001", "CK-1", "mortise")
    a_core(shop, "K-1002", "CK-2", "rim")
    assert "no core can be moved" in call(shop, "control", "crossings").output

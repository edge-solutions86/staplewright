import pytest

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.hardware import Position
from hasplib.errors import KeywayError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.loaders import (
    load_coverage,
    load_deposits,
    load_directory,
    load_family,
    load_group_names,
    load_groups,
    load_ledger,
    load_openings,
    load_quarantine,
    load_register,
    load_schedule,
)
from hasplib.persistence.models import RetiredBittingRow
from people import TODAY


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def test_the_openings_come_back_whole(session):
    dbcampus.build(session)
    schedule = load_openings(session)
    assert len(schedule) == 11
    assert schedule.opening("MOO-101").exterior


def test_the_register_puts_every_cylinder_back(session):
    dbcampus.build(session)
    register = load_register(session)
    assert register.at("MOO-214", Position.LOCKSET).serial == "C-1001"
    assert [c.serial for c in register.on_the_shelf()] == ["C-1099"]


def test_the_group_names_reach_the_cylinders(session):
    dbcampus.build(session)
    register = load_register(session)
    assert register.cylinder("C-1001").group == "moore offices"


def test_the_coverage_answers_what_a_key_opens(session):
    dbcampus.build(session)
    coverage = load_coverage(session)
    assert coverage.openings_for(sym("AA1")) == ("MOO-214", "MOO-216")
    assert len(coverage.openings_for(sym("GGM"))) == 6


def test_the_groups_come_back_by_name(session):
    dbcampus.build(session)
    groups = load_groups(session)
    assert "moore offices" in groups
    assert groups["moore offices"].size == 4


def test_the_group_names_come_back_by_id(session):
    dbcampus.build(session)
    names = load_group_names(session)
    assert sorted(names.values()) == sorted(load_groups(session))


def test_the_directory_comes_back_whole(session):
    dbcampus.build(session)
    directory = load_directory(session)
    assert len(directory) == 6
    assert directory.may_authorize("rvirta", "geography", "MOO", TODAY)


def test_the_ledger_comes_back_with_its_keys_out(session):
    dbcampus.build(session)
    ledger = load_ledger(session)
    assert [key.tag for key in ledger.out()] == [
        "41207",
        "41208",
        "41209",
        "41210",
        "41211",
    ]


def test_the_deposit_book_comes_back_empty_when_nothing_was_charged(session):
    dbcampus.build(session)
    assert load_deposits(session).total_held() == 0


def test_the_schedule_comes_back_with_its_cuts(session):
    system = dbcampus.build(session)
    schedule = load_schedule(session, system)
    assert schedule.bitting_of(sym("AA1")).render() == "620446"
    assert len(schedule) == len(dbcampus.CUTS)


def test_the_family_comes_back_as_a_tree(session):
    dbcampus.build(session)
    family = load_family(session, "Sargent LA")
    assert family.enters("L", "LC")
    assert family.leaves() == ("LC", "LD", "LE")


def test_a_family_nobody_stored_has_no_root(session):
    dbcampus.build(session)
    with pytest.raises(KeywayError):
        load_family(session, "Best A2")


def test_the_quarantine_comes_back_with_its_reasons(session):
    dbcampus.build(session)
    session.add(RetiredBittingRow(bitting="342156", reason="compromised", retired_on=TODAY))
    session.flush()
    hold = load_quarantine(session)
    assert len(hold) == 1
    assert hold.permanent()[0].bitting.render() == "342156"


def test_an_empty_quarantine_holds_nothing(session):
    assert len(load_quarantine(session)) == 0


def test_an_empty_database_loads_empty_objects(session):
    assert len(load_openings(session)) == 0
    assert len(load_register(session)) == 0
    assert len(load_directory(session)) == 0
    assert len(load_ledger(session)) == 0

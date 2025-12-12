"""The indexes exist and the queries that need them use them.

A test that an index exists is nearly worthless on its own, because the
question is always whether the planner picks it. So these ask sqlite for the
plan and look for the index by name, which is the only thing that would have
caught the two indexes that were on the wrong column order.
"""

from datetime import date

import pytest
from sqlalchemy import inspect, select, text

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.models.campus import CylinderRow, OpeningRow
from hasplib.persistence.models.control import IssuedKeyRow

TODAY = date(2025, 6, 30)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def plan_for(session, statement) -> str:
    compiled = statement.compile(session.bind, compile_kwargs={"literal_binds": True})
    rows = session.execute(text(f"explain query plan {compiled}")).all()
    return " ".join(str(cell) for row in rows for cell in row)


def indexes_on(session, table: str) -> set[str]:
    return {item["name"] for item in inspect(session.bind).get_indexes(table)}


def test_the_key_indexes_are_on_the_table(session):
    found = indexes_on(session, "issued_keys")
    assert "ix_issued_keys_holder_state" in found
    assert "ix_issued_keys_state_due" in found
    assert "ix_issued_keys_symbol_state" in found


def test_the_cylinder_indexes_are_on_the_table(session):
    found = indexes_on(session, "cylinders")
    assert "ix_cylinders_installed_at" in found
    assert "ix_cylinders_group_id" in found


def test_the_opening_indexes_are_on_the_table(session):
    found = indexes_on(session, "openings")
    assert "ix_openings_building" in found
    assert "ix_openings_function" in found


def test_what_a_person_holds_uses_the_holder_index(session):
    statement = select(IssuedKeyRow).where(
        IssuedKeyRow.keyholder == "kmaki", IssuedKeyRow.state == "out"
    )
    assert "ix_issued_keys_holder_state" in plan_for(session, statement)


def test_the_overdue_list_uses_the_state_index(session):
    statement = select(IssuedKeyRow).where(IssuedKeyRow.state == "out", IssuedKeyRow.due_on < TODAY)
    assert "ix_issued_keys_state_due" in plan_for(session, statement)


def test_the_keys_cut_to_a_symbol_use_the_symbol_index(session):
    statement = select(IssuedKeyRow).where(
        IssuedKeyRow.symbol == "AA1", IssuedKeyRow.state == "out"
    )
    assert "ix_issued_keys_symbol_state" in plan_for(session, statement)


def test_the_cylinders_on_a_door_use_the_installed_index(session):
    statement = select(CylinderRow).where(CylinderRow.installed_at == "MOO-214")
    assert "ix_cylinders_installed_at" in plan_for(session, statement)


def test_the_cylinders_in_a_group_use_the_group_index(session):
    statement = select(CylinderRow).where(CylinderRow.group_id == 1)
    assert "ix_cylinders_group_id" in plan_for(session, statement)


def test_the_doors_in_a_building_use_the_building_index(session):
    statement = select(OpeningRow).where(OpeningRow.building_code == "MOO")
    assert "ix_openings_building" in plan_for(session, statement)


def test_the_indexes_do_not_change_any_answer(session):
    from hasplib.persistence.repositories import CylinderRepository, KeyRepository

    keys = KeyRepository(session)
    assert [row.tag for row in keys.held_by("phamm")] == ["41210", "41211"]
    cylinders = CylinderRepository(session)
    assert [row.serial for row in cylinders.on_opening("MOO-214")] == ["C-1001"]


def test_every_index_this_schema_declares_is_named(session):
    from hasplib.persistence.base import Base

    for table in Base.metadata.tables.values():
        for index in table.indexes:
            assert index.name, table.name
            assert index.name.startswith(("ix_", "ux_")), index.name

import pytest
from sqlalchemy import Column, Integer, MetaData, String, Table, insert, select, text
from sqlalchemy.exc import StatementError

from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.base import (
    LONG,
    MEDIUM,
    NAMING_CONVENTION,
    SHORT,
    table_names,
)
from hasplib.persistence.engine import (
    MEMORY,
    build_engine,
    build_sessions,
    create_schema,
    drop_schema,
    session_scope,
)
from hasplib.persistence.types import BittingColumn, DepthListColumn, SymbolColumn

SCRATCH = MetaData(naming_convention=NAMING_CONVENTION)
"""A metadata of its own, so this table stays out of the migrations."""

SAMPLE = Table(
    "sample_cuts",
    SCRATCH,
    Column("id", Integer, primary_key=True),
    Column("symbol", SymbolColumn()),
    Column("bitting", BittingColumn()),
    Column("chambers", DepthListColumn()),
    Column("note", String(LONG)),
)


@pytest.fixture()
def engine():
    made = build_engine(MEMORY)
    create_schema(made)
    SCRATCH.create_all(made)
    yield made
    SCRATCH.drop_all(made)
    drop_schema(made)
    made.dispose()


def test_the_column_widths_are_ordered():
    assert SHORT < MEDIUM < LONG


def test_every_table_is_named_and_reachable():
    names = table_names()
    assert "charts" in names
    assert "sample_cuts" not in names
    assert len(names) == len(set(names))


def test_the_naming_convention_reaches_the_primary_key():
    assert SAMPLE.primary_key.name == "pk_sample_cuts"


def test_sqlite_is_told_to_enforce_foreign_keys(engine):
    with engine.connect() as connection:
        assert connection.execute(text("PRAGMA foreign_keys")).scalar() == 1


def test_a_bitting_goes_in_and_comes_back_as_a_bitting(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, bitting=Bitting.parse("342156")))
    with session_scope(sessions) as session:
        found = session.execute(select(SAMPLE.c.bitting)).scalar_one()
    assert found == Bitting.parse("342156")


def test_a_bitting_is_stored_the_way_the_shop_writes_it(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, bitting=Bitting((10, 4, 2))))
    with engine.connect() as connection:
        raw = connection.execute(text("select bitting from sample_cuts")).scalar_one()
    assert raw == "10.4.2"


def test_a_bitting_can_be_written_as_text(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, bitting="342156"))
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE.c.bitting)).scalar_one() == Bitting.parse("342156")


def test_a_symbol_round_trips(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, symbol=KeySymbol.parse("AB3")))
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE.c.symbol)).scalar_one() == KeySymbol.parse("AB3")


def test_the_top_key_round_trips_too(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, symbol=KeySymbol.parse("GGM")))
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE.c.symbol)).scalar_one().is_top


def test_a_chamber_list_round_trips(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, chambers=(2, 4, 6)))
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE.c.chambers)).scalar_one() == (2, 4, 6)


def test_an_empty_chamber_list_comes_back_empty(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, chambers=()))
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE.c.chambers)).scalar_one() == ()


def test_nulls_stay_null(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1))
    with session_scope(sessions) as session:
        row = session.execute(select(SAMPLE)).one()
    assert row.bitting is None
    assert row.symbol is None
    assert row.chambers is None


def test_something_that_is_not_a_bitting_is_refused(engine):
    sessions = build_sessions(engine)
    with pytest.raises(StatementError) as caught, session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, bitting=42))
    assert isinstance(caught.value.orig, TypeError)


def test_something_that_is_not_a_symbol_is_refused(engine):
    sessions = build_sessions(engine)
    with pytest.raises(StatementError) as caught, session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, symbol=7))
    assert "key symbol" in str(caught.value)


def test_a_failed_scope_rolls_back(engine):
    sessions = build_sessions(engine)
    with pytest.raises(RuntimeError), session_scope(sessions) as session:
        session.execute(insert(SAMPLE).values(id=1, bitting="342156"))
        raise RuntimeError("something went wrong halfway")
    with session_scope(sessions) as session:
        assert session.execute(select(SAMPLE)).all() == []

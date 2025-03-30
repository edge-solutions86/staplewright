"""The migrations and the models have to describe the same schema.

This is the test that catches the commit where somebody adds a column to a
model and forgets the migration. It builds the database from the migrations
alone, then asks alembic what it would autogenerate against the models, and
anything at all coming back is the failure.
"""

from pathlib import Path

import pytest
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import create_engine, inspect

from hasplib.persistence import models  # noqa: F401
from hasplib.persistence.base import Base, table_names

ROOT = Path(__file__).resolve().parents[1]


def a_config(connection) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.attributes["connection"] = connection
    return config


@pytest.fixture()
def connection(tmp_path):
    engine = create_engine(f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}")
    with engine.connect() as made:
        yield made
    engine.dispose()


def test_the_migrations_build_every_table(connection):
    command.upgrade(a_config(connection), "head")
    built = set(inspect(connection).get_table_names())
    assert set(table_names()) <= built
    assert "alembic_version" in built


def test_the_migrations_and_the_models_agree(connection):
    command.upgrade(a_config(connection), "head")
    context = MigrationContext.configure(connection)
    assert compare_metadata(context, Base.metadata) == []


def test_the_schema_comes_apart_again(connection):
    config = a_config(connection)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    left = set(inspect(connection).get_table_names())
    assert left <= {"alembic_version"}


def test_the_schema_can_be_built_twice(connection):
    config = a_config(connection)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    command.upgrade(config, "head")
    assert set(table_names()) <= set(inspect(connection).get_table_names())


def test_the_head_is_a_single_revision(connection):
    from alembic.script import ScriptDirectory

    script = ScriptDirectory.from_config(a_config(connection))
    assert len(script.get_heads()) == 1


def test_every_revision_has_a_downgrade(connection):
    from alembic.script import ScriptDirectory

    script = ScriptDirectory.from_config(a_config(connection))
    for revision in script.walk_revisions():
        source = Path(revision.path).read_text(encoding="utf-8")
        body = source.split("def downgrade()")[1]
        assert "op." in body or "pass" in body


def test_the_foreign_keys_arrive_with_the_tables(connection):
    command.upgrade(a_config(connection), "head")
    keys = inspect(connection).get_foreign_keys("cylinders")
    referred = {key["referred_table"] for key in keys}
    assert "openings" in referred


def test_the_named_constraints_arrive_with_the_tables(connection):
    command.upgrade(a_config(connection), "head")
    found = inspect(connection).get_unique_constraints("schedule_entries")
    names = {item["name"] for item in found}
    assert "uq_schedule_entries_system_id_symbol" in names

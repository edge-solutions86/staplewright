"""Alembic's entry point, pointed at the models rather than at a snapshot.

The star import is the whole job. A table nobody imported is not on the
metadata, so autogenerate cheerfully writes a migration that drops it, and
the first person to run the downgrade finds out.

Batch mode is on for sqlite because sqlite cannot alter a column in place and
alembic has to rebuild the table instead. The shop runs postgres and every
developer runs sqlite, so both paths have to work.

The url comes off HASPLIB_DATABASE when it is set, which is the same variable
the command line reads. Two ways of naming the database was one too many: a
migration run against the ini default while everything else ran against the
environment is a migration that appears to have worked and has not.
"""

import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from hasplib.persistence.base import Base
from hasplib.persistence.models import *  # noqa: F401, F403

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

DATABASE_VARIABLE = "HASPLIB_DATABASE"


def database_url() -> str:
    """The environment first, then whatever the ini file says."""
    chosen = os.environ.get(DATABASE_VARIABLE, "").strip()
    if chosen:
        config.set_main_option("sqlalchemy.url", chosen)
        return chosen
    return config.get_main_option("sqlalchemy.url") or ""


target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """Emit the sql without a connection, for a dba to read first."""
    context.configure(
        url=database_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        render_as_batch=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run against a live connection."""
    connectable = config.attributes.get("connection", None)
    if connectable is None:
        database_url()
        engine = engine_from_config(
            config.get_section(config.config_ini_section, {}),
            prefix="sqlalchemy.",
            poolclass=pool.NullPool,
        )
        with engine.connect() as connection:
            _run(connection)
        engine.dispose()
    else:
        _run(connectable)


def _run(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        render_as_batch=connection.dialect.name == "sqlite",
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()

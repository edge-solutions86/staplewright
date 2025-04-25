"""Getting a connection, and the two settings sqlite gets wrong by default.

The shop runs on postgres and every developer runs on sqlite, so both have to
work. Sqlite needs foreign keys switched on explicitly, which is a pragma per
connection and not a one time setting, and without it half the constraints in
the schema are decoration.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import Engine, StaticPool, create_engine, event
from sqlalchemy.orm import Session, sessionmaker

from hasplib.persistence.base import Base

MEMORY = "sqlite+pysqlite:///:memory:"


def _enforce_foreign_keys(connection: Any, record: Any) -> None:
    cursor = connection.cursor()
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.close()


def build_engine(url: str = MEMORY, echo: bool = False) -> Engine:
    """An engine with sqlite told to behave like a database.

    An in memory database lives in its connection, so the pool has to hand
    the same one to everybody and stop objecting about threads. Without that
    a web request served on a worker thread opens a second, empty database
    and reports that none of the tables exist.
    """
    options: dict[str, Any] = {"echo": echo, "future": True}
    if url == MEMORY:
        options["poolclass"] = StaticPool
        options["connect_args"] = {"check_same_thread": False}
    engine = create_engine(url, **options)
    if engine.dialect.name == "sqlite":
        event.listen(engine, "connect", _enforce_foreign_keys)
    return engine


def build_sessions(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False, future=True)


def create_schema(engine: Engine) -> None:
    """Build every table from the models. Migrations do this in production."""
    Base.metadata.create_all(engine)


def drop_schema(engine: Engine) -> None:
    Base.metadata.drop_all(engine)


@contextmanager
def session_scope(sessions: sessionmaker[Session]) -> Iterator[Session]:
    """A session that commits on the way out and rolls back on a raise."""
    session = sessions()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()

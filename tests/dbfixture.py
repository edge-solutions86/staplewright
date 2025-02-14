"""One place that stands a database up, so tests do not each build one."""

from collections.abc import Iterator

from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from hasplib.persistence import models  # noqa: F401
from hasplib.persistence.engine import (
    MEMORY,
    build_engine,
    build_sessions,
    create_schema,
    drop_schema,
)


def fresh_engine() -> Engine:
    engine = build_engine(MEMORY)
    create_schema(engine)
    return engine


def teardown(engine: Engine) -> None:
    drop_schema(engine)
    engine.dispose()


def sessions_for(engine: Engine) -> sessionmaker[Session]:
    return build_sessions(engine)


def open_session(engine: Engine) -> Iterator[Session]:
    session = build_sessions(engine)()
    try:
        yield session
    finally:
        session.close()

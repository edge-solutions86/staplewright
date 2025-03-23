"""One transaction, with every repository hanging off it.

The rule this exists to enforce is that a service does one thing and either
all of it happens or none of it does. Issuing a key writes the key row, takes
the deposit, and marks the request filled, and a crash between the second and
the third leaves a key out that nobody asked for.

So a service takes a unit of work rather than a session, uses the
repositories on it, and never commits: leaving the block commits, and raising
out of it rolls back. Nothing inside the domain has to know a transaction
exists.
"""

from __future__ import annotations

from types import TracebackType

from sqlalchemy.orm import Session, sessionmaker

from hasplib.errors import ConflictError
from hasplib.persistence.repositories import (
    ChartRepository,
    CylinderRepository,
    IncidentRepository,
    KeyholderRepository,
    KeyRepository,
    OpeningRepository,
    QuarantineRepository,
    RequestRepository,
    SystemRepository,
    WorkOrderRepository,
)


class UnitOfWork:
    """A session, its repositories, and one commit at the end."""

    def __init__(self, sessions: sessionmaker[Session]) -> None:
        self._sessions = sessions
        self._session: Session | None = None
        self.committed = False
        self.rolled_back = False

    @property
    def session(self) -> Session:
        if self._session is None:
            raise ConflictError("this unit of work is not open")
        return self._session

    def __enter__(self) -> UnitOfWork:
        self._session = self._sessions()
        self.committed = False
        self.rolled_back = False
        self.charts = ChartRepository(self._session)
        self.systems = SystemRepository(self._session)
        self.openings = OpeningRepository(self._session)
        self.cylinders = CylinderRepository(self._session)
        self.keyholders = KeyholderRepository(self._session)
        self.keys = KeyRepository(self._session)
        self.requests = RequestRepository(self._session)
        self.incidents = IncidentRepository(self._session)
        self.orders = WorkOrderRepository(self._session)
        self.quarantine = QuarantineRepository(self._session)
        return self

    def __exit__(
        self,
        kind: type[BaseException] | None,
        value: BaseException | None,
        trace: TracebackType | None,
    ) -> None:
        try:
            if kind is None and not self.rolled_back:
                self.commit()
            else:
                self.rollback()
        finally:
            self.session.close()
            self._session = None

    def commit(self) -> None:
        self.session.commit()
        self.committed = True

    def rollback(self) -> None:
        self.session.rollback()
        self.rolled_back = True

    def flush(self) -> None:
        """Push writes down so ids come back, without ending the transaction."""
        self.session.flush()


def unit_of_work(sessions: sessionmaker[Session]) -> UnitOfWork:
    """The way services ask for one, so the constructor can change."""
    return UnitOfWork(sessions)

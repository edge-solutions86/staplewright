"""Loading each aggregate once per request instead of once per question.

The loaders are cheap to call and expensive to call twice. A campus summary
asks for the openings, the coverage, the ledger and the deposits, and the
coverage asks for the openings and the groups on its own account, so the
openings table gets read three times to answer one question. On a campus with
four hundred doors that is not a performance problem yet, and on the one this
was written against it already was.

So a workspace holds one session and remembers what it has built on it. The
lifetime is the request, deliberately: a longer lived cache would be a cache
somebody has to remember to clear, and the whole class of bug that comes with
that is worse than reading a table twice.

Nothing here queries. Every method calls the same loader a caller would have
called, which is what makes it safe to drop in: the answer cannot differ,
only the number of times it was worked out.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date
from typing import Any, TypeVar

from sqlalchemy.orm import Session

from hasplib.campus.coverage import CoverageMap
from hasplib.campus.hardware import HardwareRegister
from hasplib.campus.openings import OpeningSchedule
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger
from hasplib.control.people import Directory
from hasplib.errors import NotFoundError
from hasplib.keys.schedule import KeyingSchedule
from hasplib.persistence.loaders import (
    load_coverage,
    load_deposits,
    load_directory,
    load_family,
    load_ledger,
    load_openings,
    load_quarantine,
    load_register,
    load_schedule,
)
from hasplib.persistence.models.keying import SystemRow
from hasplib.persistence.repositories import SystemRepository
from hasplib.rekey.quarantine import Quarantine
from hasplib.spec.keyway import KeywayFamily
from hasplib.spec.rules import CuttingRules

Loaded = TypeVar("Loaded")


class Workspace:
    """One session, and everything already built on it."""

    def __init__(
        self, session: Session, rules: CuttingRules | None = None, quarantine_years: int = 7
    ) -> None:
        self.session = session
        self.rules = rules or CuttingRules()
        self.quarantine_years = quarantine_years
        self._held: dict[str, Any] = {}
        self.systems = SystemRepository(session)

    def _once(self, name: str, build: Callable[[], Loaded]) -> Loaded:
        if name not in self._held:
            self._held[name] = build()
        return self._held[name]

    def openings(self) -> OpeningSchedule:
        return self._once("openings", lambda: load_openings(self.session))

    def register(self) -> HardwareRegister:
        return self._once("register", lambda: load_register(self.session))

    def coverage(self) -> CoverageMap:
        return self._once("coverage", lambda: load_coverage(self.session))

    def directory(self) -> Directory:
        return self._once("directory", lambda: load_directory(self.session))

    def ledger(self) -> KeyLedger:
        return self._once("ledger", lambda: load_ledger(self.session))

    def deposits(self) -> DepositBook:
        return self._once("deposits", lambda: load_deposits(self.session))

    def quarantine(self) -> Quarantine:
        return self._once(
            "quarantine", lambda: load_quarantine(self.session, self.quarantine_years)
        )

    def family(self, name: str) -> KeywayFamily:
        return self._once(f"family:{name}", lambda: load_family(self.session, name))

    def system(self, name: str) -> SystemRow:
        row = self.systems.by_name(name)
        if row is None:
            raise NotFoundError("no such keying system", system=name)
        return row

    def schedule(self, name: str) -> KeyingSchedule:
        return self._once(
            f"schedule:{name}",
            lambda: load_schedule(self.session, self.system(name), self.rules),
        )

    def forget(self) -> None:
        """Drop everything held, for a caller that has just written something."""
        self._held.clear()

    @property
    def loaded(self) -> tuple[str, ...]:
        """What has been built so far, which is what the tests assert on."""
        return tuple(sorted(self._held))

    def person_holdings(self, identifier: str) -> tuple[str, ...]:
        """Tags out to one person, which needs the ledger and nothing else."""
        return tuple(key.tag for key in self.ledger().held_by(identifier))

    def overdue(self, on: date) -> tuple[str, ...]:
        return tuple(key.tag for key in self.ledger().overdue(on))


def workspace_for(shop: Any, session: Session) -> Workspace:
    """A workspace carrying this shop's policy, for a router or a command."""
    return Workspace(session, shop.settings.cutting, shop.settings.quarantine_years)

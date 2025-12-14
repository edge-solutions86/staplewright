"""The self check over http, for whatever is watching this service.

Two endpoints and they answer two different questions. ``/health`` is the one
a load balancer asks fifty times a minute: is the process up and can it reach
the database. It runs no checks, because a readiness probe that walks eight
tables is a readiness probe that falls over under the load it creates.

``/health/report`` is the one somebody looks at on a monday. It runs
everything and gives back the findings, and it is expected to be slow.

The status code on the report is the interesting decision. It is always two
hundred, because the request succeeded: the findings are the answer, not an
error. A monitor that wants to alarm reads ``worst`` and decides for itself.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from hasplib import __version__
from hasplib.api.wiring import PLAIN, page, session_dependency
from hasplib.errors import NotFoundError
from hasplib.health import (
    CHECKS,
    Finding,
    counts_by_severity,
    exit_code,
    health_report,
    run_checks,
    worst,
)


def finding_shape(finding: Finding) -> dict[str, Any]:
    return {
        "check": finding.check,
        "severity": finding.severity.label,
        "subject": finding.subject,
        "detail": finding.detail,
    }


def check_names() -> tuple[str, ...]:
    return tuple(sorted(check.__name__ for check in CHECKS))


def check_by_name(name: str) -> Any:
    for check in CHECKS:
        if check.__name__ == name:
            return check
    raise NotFoundError("no such check", check=name)


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/health", tags=["service"])
    session = session_dependency(shop)

    @router.get("")
    def live(db: Session = Depends(session)) -> dict[str, Any]:
        """Up, and able to reach the database. Nothing else."""
        db.execute(text("select 1"))
        return {"status": "up", "version": __version__}

    @router.get("/checks")
    def checks() -> dict[str, Any]:
        return {"checks": list(check_names())}

    @router.get("/report")
    def report(
        on: date | None = None,
        plain: bool = PLAIN,
        db: Session = Depends(session),
    ) -> Any:
        return page(health_report(db, on or date.today()), plain)

    @router.get("/findings")
    def findings(
        on: date | None = None,
        severity: str = Query(default="", pattern="^(|note|warning|fault)$"),
        db: Session = Depends(session),
    ) -> dict[str, Any]:
        today = on or date.today()
        found = run_checks(db, today)
        if severity:
            found = [item for item in found if item.severity.label == severity]
        highest = worst(found)
        return {
            "on": today.isoformat(),
            "worst": highest.label if highest is not None else None,
            "counts": counts_by_severity(found),
            "exit_code": exit_code(found),
            "findings": [finding_shape(item) for item in found],
        }

    @router.get("/findings/{name}")
    def one_check(
        name: str, on: date | None = None, db: Session = Depends(session)
    ) -> dict[str, Any]:
        today = on or date.today()
        found = run_checks(db, today, checks=[check_by_name(name)])
        return {
            "check": name,
            "on": today.isoformat(),
            "findings": [finding_shape(item) for item in found],
        }

    return router

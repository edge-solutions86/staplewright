"""Reports over http, all of them reads and all of them plain text.

Every one of these already exists as a list of lines, because the shop prints
them. Handing the same lines back over http means the web page and the piece
of paper cannot disagree, which they did for two years when the web version
was built separately and rounded the deposits differently.

So the response is the lines, in order, and a content type of text when
somebody asks for it that way. There is no html here and there is not going
to be any.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from hasplib.api.wiring import PLAIN, page, session_dependency
from hasplib.control.notices import NoticeLog, notices_due
from hasplib.persistence.loaders import (
    load_coverage,
    load_deposits,
    load_directory,
    load_ledger,
    load_openings,
    load_schedule,
)
from hasplib.persistence.repositories import SystemRepository
from hasplib.reports.control import (
    department_summary,
    deposit_statement,
    keyholder_statement,
    notice_run,
    overdue_report,
)
from hasplib.reports.schedule import bitting_list, schedule_report, tree_report
from hasplib.reports.statistics import campus_numbers, exposure, keys_by_building, summary


def build_router(shop: Any) -> APIRouter:
    router = APIRouter(prefix="/reports", tags=["reports"])

    session = session_dependency(shop)

    @router.get("/systems/{name}/schedule")
    def schedule(
        name: str,
        plain: bool = PLAIN,
        shape: str = Query(default="schedule", pattern="^(schedule|bittings|tree)$"),
        db: Session = Depends(session),
    ) -> Any:
        systems = SystemRepository(db)
        row = systems.by_name(name)
        if row is None:
            from hasplib.errors import NotFoundError

            raise NotFoundError("no such keying system", system=name)
        drawn = load_schedule(db, row, shop.settings.cutting)
        if shape == "bittings":
            lines = bitting_list(drawn)
        elif shape == "tree":
            lines = tree_report(drawn)
        else:
            lines = schedule_report(drawn, name)
        return page(lines, plain)

    @router.get("/keyholders/{identifier}/statement")
    def statement(
        identifier: str,
        on: date | None = None,
        plain: bool = PLAIN,
        db: Session = Depends(session),
    ) -> Any:
        directory = load_directory(db)
        lines = keyholder_statement(
            directory.person(identifier), load_ledger(db), load_deposits(db), on
        )
        return page(lines, plain)

    @router.get("/keyholders/{identifier}/deposits")
    def deposits(identifier: str, plain: bool = PLAIN, db: Session = Depends(session)) -> Any:
        return page(deposit_statement(load_deposits(db), identifier), plain)

    @router.get("/departments/{department}")
    def department(
        department: str,
        on: date,
        plain: bool = PLAIN,
        db: Session = Depends(session),
    ) -> Any:
        lines = department_summary(load_directory(db), load_ledger(db), department, on)
        return page(lines, plain)

    @router.get("/overdue")
    def overdue(on: date, plain: bool = PLAIN, db: Session = Depends(session)) -> Any:
        return page(overdue_report(load_ledger(db), on), plain)

    @router.get("/letters")
    def letters(on: date, plain: bool = PLAIN, db: Session = Depends(session)) -> Any:
        due = notices_due(load_ledger(db), on, NoticeLog(), shop.settings.notices)
        return page(notice_run(due), plain)

    @router.get("/campus")
    def campus(plain: bool = PLAIN, db: Session = Depends(session)) -> Any:
        numbers = campus_numbers(
            load_openings(db), load_coverage(db), load_ledger(db), load_deposits(db)
        )
        return page(summary(numbers), plain)

    @router.get("/exposure")
    def widest(limit: int = Query(default=5, ge=1, le=50), db: Session = Depends(session)) -> Any:
        found = exposure(load_coverage(db), load_ledger(db), limit)
        return {"keys": [{"symbol": symbol, "openings": count} for symbol, count in found]}

    @router.get("/buildings")
    def buildings(db: Session = Depends(session)) -> Any:
        return {"keys_by_building": keys_by_building(load_coverage(db), load_ledger(db))}

    return router

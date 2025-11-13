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

from hasplib.api.wiring import PLAIN, page, service_dependency
from hasplib.control.notices import NoticeLog, notices_due
from hasplib.persistence.workspace import Workspace
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

    space = service_dependency(
        Workspace, shop, shop.settings.cutting, shop.settings.quarantine_years
    )

    @router.get("/systems/{name}/schedule")
    def schedule(
        name: str,
        plain: bool = PLAIN,
        shape: str = Query(default="schedule", pattern="^(schedule|bittings|tree)$"),
        work: Workspace = Depends(space),
    ) -> Any:
        drawn = work.schedule(name)
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
        work: Workspace = Depends(space),
    ) -> Any:
        directory = work.directory()
        lines = keyholder_statement(
            directory.person(identifier), work.ledger(), work.deposits(), on
        )
        return page(lines, plain)

    @router.get("/keyholders/{identifier}/deposits")
    def deposits(identifier: str, plain: bool = PLAIN, work: Workspace = Depends(space)) -> Any:
        return page(deposit_statement(work.deposits(), identifier), plain)

    @router.get("/departments/{department}")
    def department(
        department: str,
        on: date,
        plain: bool = PLAIN,
        work: Workspace = Depends(space),
    ) -> Any:
        lines = department_summary(work.directory(), work.ledger(), department, on)
        return page(lines, plain)

    @router.get("/overdue")
    def overdue(on: date, plain: bool = PLAIN, work: Workspace = Depends(space)) -> Any:
        return page(overdue_report(work.ledger(), on), plain)

    @router.get("/letters")
    def letters(on: date, plain: bool = PLAIN, work: Workspace = Depends(space)) -> Any:
        due = notices_due(work.ledger(), on, NoticeLog(), shop.settings.notices)
        return page(notice_run(due), plain)

    @router.get("/campus")
    def campus(plain: bool = PLAIN, work: Workspace = Depends(space)) -> Any:
        numbers = campus_numbers(work.openings(), work.coverage(), work.ledger(), work.deposits())
        return page(summary(numbers), plain)

    @router.get("/exposure")
    def widest(limit: int = Query(default=5, ge=1, le=50), work: Workspace = Depends(space)) -> Any:
        found = exposure(work.coverage(), work.ledger(), limit)
        return {"keys": [{"symbol": symbol, "openings": count} for symbol, count in found]}

    @router.get("/buildings")
    def buildings(work: Workspace = Depends(space)) -> Any:
        return {"keys_by_building": keys_by_building(work.coverage(), work.ledger())}

    return router

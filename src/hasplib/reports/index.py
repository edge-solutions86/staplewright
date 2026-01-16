"""Every page this library prints, listed once and checked against itself.

There are about forty reports across nine modules and nobody could name half
of them. The shop asks for a page by describing it, somebody writes a new one
because they cannot find the old one, and a year later there are two answers
to the same question that round differently.

So this is the index, and it is not documentation. Every row names a real
function, there is a test that says so, and there is a second test that says
every report function is on a row. A page that is not on this list either
gets added to it or gets deleted, and both of those are better than a page
nobody can find.

The command that prints it is ``hasplib reports``, which is the first thing
to type when somebody asks for something that sounds like it might exist.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from importlib import import_module
from types import ModuleType

from hasplib.errors import NotFoundError

MODULES = (
    "annual",
    "control",
    "escrow",
    "pinning",
    "rekey",
    "schedule",
    "statistics",
    "stock",
    "survey",
)
"""The report modules, which is where a page is allowed to live."""

NOT_A_REPORT = frozenset(
    {
        # Measurements and shapes that other reports print, rather than pages.
        "reconcile",
        "take",
        "is_master",
        "masters_out",
        "unaccounted_keys",
        "concentration",
        "worst_buildings",
        "blocking_deficiencies",
        "state_counts",
        "campus_numbers",
        "exposure",
        "keys_by_building",
        "expiry_of",
        # Shapes a report hands back, rather than pages themselves.
        "Return",
        "Reconciliation",
        "CampusNumbers",
        # Single lines that a page above them assembles.
        "key_line",
        "chart_body",
        "key_lines",
        "stack_row",
        "schedule_line",
    }
)
"""Names in a report module that are not pages. Kept short on purpose."""


@dataclass(frozen=True)
class Page:
    """One report: where it lives, what it answers, and who asks for it."""

    module: str
    name: str
    asked_by: str
    answers: str

    @property
    def path(self) -> str:
        return f"hasplib.reports.{self.module}.{self.name}"

    def describe(self) -> str:
        return f"{self.name:<24}{self.asked_by:<12}{self.answers}"

    def resolve(self) -> Callable[..., object]:
        """The function itself, which is what makes this list a test."""
        return getattr(_module(self.module), self.name)


def _module(name: str) -> ModuleType:
    return import_module(f"hasplib.reports.{name}")


PAGES: tuple[Page, ...] = (
    Page("annual", "annual_return", "risk", "the whole key control return"),
    Page("annual", "master_table", "risk", "every master out and who has it"),
    Page("annual", "unaccounted_table", "risk", "keys nobody can produce"),
    Page("annual", "returns_table", "risk", "several years side by side"),
    Page("annual", "movement", "risk", "what changed against last year"),
    Page("control", "keyholder_statement", "counter", "the sheet a keyholder signs"),
    Page("control", "deposit_statement", "counter", "deposits against one person"),
    Page("control", "department_summary", "counter", "who in a department holds what"),
    Page("control", "overdue_report", "counter", "the chase list"),
    Page("control", "notice_run", "counter", "what the notices run would send"),
    Page("escrow", "coverage_sheet", "fire service", "where the boxes are"),
    Page("escrow", "exposure_sheet", "shop", "what is open and for how long"),
    Page("escrow", "walk_sheet", "shop", "the list to carry round the campus"),
    Page("escrow", "seal_sheet", "shop", "every seal one box has carried"),
    Page("escrow", "overdue_sheet", "shop", "boxes past their reseal window"),
    Page("escrow", "shop_report", "shop", "the whole box position"),
    Page("escrow", "key_found", "shop", "which boxes hold a symbol"),
    Page("pinning", "cylinder_chart", "bench", "the sheet propped against the vice"),
    Page("pinning", "parts_summary", "bench", "what to draw out of the cabinet"),
    Page("pinning", "core_chart", "bench", "a core, with its control shear line"),
    Page("rekey", "incident_sheet", "shop", "one lost key and what it opens"),
    Page("rekey", "scope_sheet", "shop", "how far a rekey has to reach"),
    Page("rekey", "cost_sheet", "budget", "what a rekey costs"),
    Page("rekey", "order_packet", "technician", "the paper that goes out with a crew"),
    Page("rekey", "line_sheet", "technician", "one work order line by line"),
    Page("rekey", "packet", "technician", "an order and its lines together"),
    Page("schedule", "schedule_report", "shop", "the whole keying schedule"),
    Page("schedule", "bitting_list", "bench", "every symbol and its cut"),
    Page("schedule", "tree_report", "shop", "the schedule as a tree"),
    Page("schedule", "cross_reference", "shop", "which doors a symbol turns"),
    Page("schedule", "open_symbols_report", "shop", "symbols drawn and not cut"),
    Page("statistics", "summary", "management", "the campus in eight numbers"),
    Page("statistics", "keys_by_department", "management", "who holds what, by department"),
    Page("statistics", "incidents_by_year", "management", "lost keys year by year"),
    Page("statistics", "lockout_pressure", "management", "which doors keep locking people out"),
    Page("stock", "reconciliation_sheet", "factory", "one part, opening to closing"),
    Page("stock", "movement_log", "factory", "every movement of one part"),
    Page("stock", "shrinkage", "factory", "parts that do not account for themselves"),
    Page("stock", "reorder_sheet", "shop", "what to put on the next order"),
    Page("stock", "outstanding_orders", "shop", "what is still coming"),
    Page("stock", "restricted_position", "shop", "restricted brass in the building"),
    Page("stock", "stock_report", "factory", "the whole blank stock pack"),
    Page("survey", "condition_table", "management", "how many doors are in each state"),
    Page("survey", "deficiency_table", "management", "what a walk round found"),
    Page("survey", "blocking_work", "shop", "doors that need work before a rekey"),
    Page("survey", "unwalked", "shop", "doors nobody looked at"),
    Page("survey", "inventory", "shop", "every door on a survey"),
    Page("survey", "survey_report", "management", "the whole survey"),
)
"""Every page, in module order then in the order somebody reads them."""

BY_NAME = {page.name: page for page in PAGES}


def page(name: str) -> Page:
    found = BY_NAME.get(name)
    if found is None:
        raise NotFoundError("nothing is printed under that name", report=name)
    return found


def names() -> tuple[str, ...]:
    return tuple(sorted(BY_NAME))


def for_module(module: str) -> tuple[Page, ...]:
    return tuple(item for item in PAGES if item.module == module)


def for_reader(asked_by: str) -> tuple[Page, ...]:
    """Every page one kind of person asks for, which is how they arrive."""
    return tuple(item for item in PAGES if item.asked_by == asked_by)


def readers() -> tuple[str, ...]:
    return tuple(sorted({item.asked_by for item in PAGES}))


def undocumented(module: str) -> tuple[str, ...]:
    """Public functions in a report module that are not on the list."""
    found = _module(module)
    listed = {item.name for item in for_module(module)}
    return tuple(
        sorted(
            name
            for name in dir(found)
            if not name.startswith("_")
            and name not in listed
            and name not in NOT_A_REPORT
            and callable(getattr(found, name))
            and getattr(getattr(found, name), "__module__", "") == found.__name__
        )
    )


def index_sheet(module: str = "") -> tuple[str, ...]:
    """The list itself, which is the page this module exists to print."""
    wanted = for_module(module) if module else PAGES
    if not wanted:
        return (f"nothing is printed out of {module}",)
    lines = [f"{len(wanted)} reports"]
    seen = ""
    for item in wanted:
        if item.module != seen:
            lines.append(item.module)
            seen = item.module
        lines.append(f"  {item.describe()}")
    return tuple(lines)


def reader_sheet(asked_by: str) -> tuple[str, ...]:
    wanted = for_reader(asked_by)
    if not wanted:
        return (f"nobody described as {asked_by} asks for anything",)
    lines = [f"{len(wanted)} reports for {asked_by}"]
    lines.extend(f"  {item.name:<24}{item.answers}" for item in wanted)
    return tuple(lines)


def every_module() -> Sequence[str]:
    return MODULES

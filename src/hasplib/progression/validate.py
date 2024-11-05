"""Reading a whole schedule back and saying what is wrong with it.

Nothing in here refuses anything. A schedule that has been in the ground for
nine years is full of decisions somebody made on a Tuesday, and the shop
needs a list of them rather than an exception. Findings carry a severity so
that the difference between two keys cut the same, which is a real problem,
and a change key that was progressed off pattern, which is usually just old,
comes out of the report in the right order.

The checks that matter, in the order they get argued about:

- two symbols cut the same key, which means two rooms share a lock nobody
  meant to share
- a change key cut identically to its master, which is not a change key
- a key that moved in a chamber the system said was constant
- a split of one step, which is a wafer that will jam
- more wafers in one chamber than the shop will build
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum

from hasplib.keys.bitting import Bitting
from hasplib.keys.schedule import KeyingSchedule
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.plan import ProgressionPlan


class Severity(StrEnum):
    """How much the shop cares."""

    BLOCKING = "blocking"
    SERIOUS = "serious"
    NOTE = "note"


_ORDER = {Severity.BLOCKING: 0, Severity.SERIOUS: 1, Severity.NOTE: 2}


@dataclass(frozen=True)
class Finding:
    """One thing wrong with a schedule."""

    code: str
    severity: Severity
    message: str
    symbol: str | None = None

    def as_dict(self) -> dict[str, object]:
        out: dict[str, object] = {
            "code": self.code,
            "severity": str(self.severity),
            "message": self.message,
        }
        if self.symbol is not None:
            out["symbol"] = self.symbol
        return out


def duplicate_cuts(schedule: KeyingSchedule) -> list[Finding]:
    """Two symbols cut to the same bitting."""
    seen: dict[tuple[int, ...], KeySymbol] = {}
    found: list[Finding] = []
    for entry in schedule.entries():
        if entry.bitting is None:
            continue
        first = seen.get(entry.bitting.depths)
        if first is None:
            seen[entry.bitting.depths] = entry.symbol
            continue
        found.append(
            Finding(
                code="duplicate_cut",
                severity=Severity.BLOCKING,
                message=(
                    f"{entry.symbol.render()} is cut the same as {first.render()}, "
                    f"{entry.bitting.render()}"
                ),
                symbol=entry.symbol.render(),
            )
        )
    return found


def keys_matching_their_master(schedule: KeyingSchedule) -> list[Finding]:
    """A key cut identically to the one above it."""
    found: list[Finding] = []
    for entry in schedule.entries():
        parent = entry.symbol.parent()
        if entry.bitting is None or parent is None or parent not in schedule:
            continue
        above = schedule.bitting_of(parent)
        if above is not None and above == entry.bitting:
            found.append(
                Finding(
                    code="same_as_master",
                    severity=Severity.BLOCKING,
                    message=f"{entry.symbol.render()} is cut the same as {parent.render()}",
                    symbol=entry.symbol.render(),
                )
            )
    return found


def thin_splits(schedule: KeyingSchedule, step: int = 2) -> list[Finding]:
    """Splits between a key and its master that are tighter than the step."""
    found: list[Finding] = []
    for entry in schedule.entries():
        parent = entry.symbol.parent()
        if entry.bitting is None or parent is None or parent not in schedule:
            continue
        above = schedule.bitting_of(parent)
        if above is None:
            continue
        for chamber in range(1, schedule.chambers + 1):
            gap = abs(above.at(chamber) - entry.bitting.at(chamber))
            if gap and gap % step:
                found.append(
                    Finding(
                        code="off_step",
                        severity=Severity.SERIOUS,
                        message=(
                            f"{entry.symbol.render()} splits {gap} in chamber {chamber} "
                            f"and the system moves {step} at a time"
                        ),
                        symbol=entry.symbol.render(),
                    )
                )
    return found


def keys_off_plan(schedule: KeyingSchedule, plan: ProgressionPlan) -> list[Finding]:
    """Change keys that could not have come out of the plan."""
    found: list[Finding] = []
    for symbol in schedule.change_keys():
        bitting = schedule.bitting_of(symbol)
        if bitting is None or plan.holds(bitting):
            continue
        moved = [
            chamber for chamber in plan.constants if bitting.at(chamber) != plan.master.at(chamber)
        ]
        message = (
            f"{symbol.render()} moved in constant chamber {moved[0]}"
            if moved
            else f"{symbol.render()} is not one of the keys the plan holds"
        )
        found.append(
            Finding(
                code="off_plan",
                severity=Severity.SERIOUS,
                message=message,
                symbol=symbol.render(),
            )
        )
    return found


def crowded_chambers(
    schedule: KeyingSchedule, groups: Sequence[Sequence[Bitting]], most_wafers: int = 3
) -> list[Finding]:
    """Chambers asked to break at more depths than the shop will build."""
    found: list[Finding] = []
    for index, keys in enumerate(groups, start=1):
        for chamber in range(1, schedule.chambers + 1):
            breaks = len({key.at(chamber) for key in keys})
            if breaks - 1 > most_wafers:
                found.append(
                    Finding(
                        code="crowded_chamber",
                        severity=Severity.SERIOUS,
                        message=(
                            f"group {index} wants {breaks - 1} wafers in chamber {chamber} "
                            f"and the shop builds {most_wafers}"
                        ),
                    )
                )
    return found


def uncut_symbols(schedule: KeyingSchedule) -> list[Finding]:
    """Symbols drawn on the schedule that nobody has cut."""
    return [
        Finding(
            code="uncut",
            severity=Severity.NOTE,
            message=f"{symbol.render()} is on the schedule and has never been cut",
            symbol=symbol.render(),
        )
        for symbol in schedule.open_symbols()
    ]


def review(
    schedule: KeyingSchedule,
    plan: ProgressionPlan | None = None,
    step: int = 2,
) -> tuple[Finding, ...]:
    """Every finding, worst first, then by symbol so the report is stable."""
    found: list[Finding] = []
    found.extend(duplicate_cuts(schedule))
    found.extend(keys_matching_their_master(schedule))
    found.extend(thin_splits(schedule, step))
    if plan is not None:
        found.extend(keys_off_plan(schedule, plan))
    found.extend(uncut_symbols(schedule))
    found.sort(key=lambda finding: (_ORDER[finding.severity], finding.symbol or "", finding.code))
    return tuple(found)


def blocking(findings: Sequence[Finding]) -> tuple[Finding, ...]:
    return tuple(f for f in findings if f.severity is Severity.BLOCKING)


def is_buildable(findings: Sequence[Finding]) -> bool:
    return not blocking(findings)

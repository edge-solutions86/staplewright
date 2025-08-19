"""The patent clock, which is the part of key control nobody budgets for.

A restricted keyway is only restricted because a patent stops anybody else
milling the blank. The factory contract on top of it is worth something, but
the patent is the part with teeth, and it runs out on a date printed on the
blank. After that date the section is a normal keyway that anybody can buy on
the open market, and every system riding on it quietly stops being a
restricted system.

Nothing breaks on that day. That is the problem. A shop that has not looked
at the dates finds out when a department turns up with a key it cut at a
hardware shop for four dollars, and by then the answer is a keyway migration
across every building on that section, which is a two year project.

So this reads the dates off the sections, sorts out what expires when, and
answers the two questions worth asking: what is exposed today, and what will
be exposed by the time a system planned this year is finished.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.errors import InputError, KeywayError
from hasplib.spec.keyway import KeywayFamily, KeywaySection

PLANNING_YEARS = 5


def expiry_of(section: KeywaySection) -> date | None:
    """The date off the section, or nothing if it never carried one.

    The catalogue prints these as plain iso days and the section keeps them
    as text, because a chart is a document before it is a data structure.
    Parsing lives here so a malformed date is one error in one place rather
    than a surprise in the middle of a report.
    """
    if section.patent_expires is None:
        return None
    try:
        return date.fromisoformat(section.patent_expires)
    except ValueError as bad:
        raise KeywayError(
            "that section has a patent date nobody can read",
            section=section.code,
            written=section.patent_expires,
        ) from bad


@dataclass(frozen=True)
class Exposure:
    """One section, and where it stands against a date."""

    section: str
    restricted: bool
    expires_on: date | None

    def days_left(self, today: date) -> int | None:
        if self.expires_on is None:
            return None
        return (self.expires_on - today).days

    def is_open(self, today: date) -> bool:
        """The patent has run, so anybody can buy the blank."""
        if not self.restricted:
            return True
        return self.expires_on is not None and self.expires_on <= today

    def is_closing(self, today: date, within_years: int = PLANNING_YEARS) -> bool:
        """It will be open before a project started today finishes."""
        if self.is_open(today) or self.expires_on is None:
            return False
        return self.expires_on <= _add_years(today, within_years)

    def describe(self, today: date) -> str:
        if not self.restricted:
            return f"{self.section} open keyway"
        if self.expires_on is None:
            return f"{self.section} restricted by contract only"
        if self.is_open(today):
            return f"{self.section} patent ran out {self.expires_on.isoformat()}"
        return f"{self.section} restricted until {self.expires_on.isoformat()}"


def _add_years(day: date, years: int) -> date:
    """The same day some years on, without dragging in a calendar library."""
    if years < 0:
        raise InputError("a planning horizon does not run backwards", years=years)
    try:
        return day.replace(year=day.year + years)
    except ValueError:
        return day.replace(year=day.year + years, day=day.day - 1)


def exposure_of(section: KeywaySection) -> Exposure:
    return Exposure(section.code, section.restricted, expiry_of(section))


def exposures(family: KeywayFamily) -> tuple[Exposure, ...]:
    """Every section in the family, soonest expiry first."""
    found = [exposure_of(section) for section in family]
    found.sort(key=lambda item: (item.expires_on or date.max, item.section))
    return tuple(found)


def open_sections(family: KeywayFamily, today: date) -> tuple[str, ...]:
    """Sections anybody can buy a blank for, restricted or never restricted."""
    return tuple(item.section for item in exposures(family) if item.is_open(today))


def closing_sections(
    family: KeywayFamily, today: date, within_years: int = PLANNING_YEARS
) -> tuple[str, ...]:
    """Sections that will be open before a project started today lands."""
    return tuple(item.section for item in exposures(family) if item.is_closing(today, within_years))


def next_expiry(family: KeywayFamily, today: date) -> Exposure | None:
    """The next section to lose its patent, which is the one to plan against."""
    for item in exposures(family):
        if item.expires_on is not None and item.expires_on > today:
            return item
    return None


def still_restricted(family: KeywayFamily, today: date) -> tuple[str, ...]:
    """Sections that are genuinely restricted on the day, in tree order."""
    open_today = set(open_sections(family, today))
    return tuple(code for code in family.codes() if code not in open_today)


def section_is_safe(
    family: KeywayFamily, code: str, today: date, within_years: int = PLANNING_YEARS
) -> bool:
    """Whether a new system may be laid down on this section.

    A section is safe when it is restricted today and stays restricted long
    enough to be worth the work. A section whose patent runs out inside the
    horizon is not a security decision, it is a deadline.
    """
    item = exposure_of(family.section(code))
    return not item.is_open(today) and not item.is_closing(today, within_years)


def refuse_unsafe_section(
    family: KeywayFamily, code: str, today: date, within_years: int = PLANNING_YEARS
) -> None:
    """Raise if a system is about to be laid down on a section that is going."""
    if section_is_safe(family, code, today, within_years):
        return
    item = exposure_of(family.section(code))
    raise KeywayError(
        "that section will not stay restricted long enough to key on",
        section=code,
        expires=item.expires_on.isoformat() if item.expires_on else "never restricted",
        horizon=within_years,
    )


def exposed_below(family: KeywayFamily, code: str, today: date) -> tuple[str, ...]:
    """Open sections a key cut on this one enters.

    This is the question that matters after an expiry, because the damage is
    not to the section that expired. It is to every section underneath it,
    which is where the change keys live.
    """
    open_today = set(open_sections(family, today))
    return tuple(entered for entered in family.sections_entered(code) if entered in open_today)


def systems_at_risk(
    family: KeywayFamily, sections_in_use: Iterable[str], today: date
) -> tuple[str, ...]:
    """Which of the sections a shop is actually keying on have gone open."""
    open_today = set(open_sections(family, today))
    return tuple(sorted({code for code in sections_in_use if code in open_today}))


def migration_order(family: KeywayFamily, today: date) -> tuple[str, ...]:
    """Which section to move off first, worst exposure first.

    Worst means widest. A root section going open exposes everything under
    it, so it gets moved before a leaf that only exposes itself, whatever the
    dates say about which expired first.
    """
    exposed = [code for code in open_sections(family, today) if family.section(code).restricted]
    exposed.sort(key=lambda code: (-len(family.sections_entered(code)), code))
    return tuple(exposed)


def patent_report(family: KeywayFamily, today: date) -> tuple[str, ...]:
    """Where the family stands, in the order somebody reads it."""
    lines = [f"{family.name} patent position at {today.isoformat()}"]
    gone = migration_order(family, today)
    if gone:
        lines.append(f"{len(gone)} sections are open and were not meant to be")
        lines.extend(f"  {code}" for code in gone)
    else:
        lines.append("every restricted section is still restricted")
    closing = closing_sections(family, today)
    if closing:
        lines.append(f"{len(closing)} sections go open inside {PLANNING_YEARS} years")
        lines.extend(f"  {exposure_of(family.section(code)).describe(today)}" for code in closing)
    following = next_expiry(family, today)
    if following is not None:
        lines.append(f"next is {following.describe(today)}")
    return tuple(lines)


def dates_in_order(sections: Sequence[KeywaySection]) -> tuple[tuple[str, date], ...]:
    """Sections that carry a date, soonest first, for a wall planner."""
    dated = [(item.code, expiry_of(item)) for item in sections]
    found = [(code, when) for code, when in dated if when is not None]
    found.sort(key=lambda pair: (pair[1], pair[0]))
    return tuple(found)

"""What changed between releases, in the file rather than in a text document.

A release note that lives in a markdown file gets written the day of the
release, from memory, by whoever is doing the release. A release note that
lives here gets written by the person making the change, because the version
they are adding to is the one they are working on.

The interesting field is ``migrations``. A shop upgrading two versions needs
to know which alembic revisions it is crossing, and working that out from the
migration folder means reading twelve files and hoping the numbering is what
it looks like.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date

from hasplib.errors import NotFoundError


@dataclass(frozen=True)
class Release:
    """One version, what it added, and what it needs on the way in."""

    version: str
    released_on: date
    headline: str
    migrations: tuple[str, ...] = ()
    notes: tuple[str, ...] = field(default_factory=tuple)

    @property
    def parts(self) -> tuple[int, int, int]:
        major, minor, patch = self.version.split(".")
        return int(major), int(minor), int(patch)

    def describe(self) -> str:
        return f"{self.version:<8}{self.released_on.isoformat()}  {self.headline}"


RELEASES: tuple[Release, ...] = (
    Release(
        "0.1.0",
        date(2024, 11, 18),
        "charts, symbols, bittings and the progression",
        notes=("no database and no service, only the arithmetic",),
    ),
    Release(
        "0.2.0",
        date(2025, 1, 20),
        "the campus: buildings, openings, cylinders and coverage",
    ),
    Release(
        "0.3.0",
        date(2025, 3, 25),
        "the schema, the mappers and the first migration",
        migrations=("0001",),
    ),
    Release(
        "0.4.0",
        date(2025, 5, 6),
        "the counter: requests, keys, deposits and notices",
    ),
    Release(
        "0.5.0",
        date(2025, 6, 18),
        "lockouts, surveys and the audit trail",
        migrations=("0002", "0003"),
    ),
    Release(
        "0.6.0",
        date(2025, 8, 8),
        "the http service and the bench half of the work",
        migrations=("0004",),
    ),
    Release(
        "0.7.0",
        date(2025, 9, 24),
        "blank stock as a ledger, and the patent clock on restricted sections",
        migrations=("0005", "0006"),
        notes=(
            "key_blanks.stock is now written by the stock service only",
            "a stocktake records the gap it found, which older counts cannot",
        ),
    ),
    Release(
        "0.8.0",
        date(2025, 11, 14),
        "sealed boxes, key handovers and the annual return",
        migrations=("0007", "0008", "0009"),
        notes=(
            "who holds a key is the ledger plus the confirmed handovers on it",
            "the ledger view is still correct about who signed, and is not the answer",
        ),
    ),
    Release(
        "0.9.0",
        date(2026, 1, 15),
        "stamping, control keys, phasing and the self check",
        migrations=("0010", "0011", "0012"),
        notes=(
            "the blind code table turns a stamped number back into a symbol",
            "control keys are their own two tables and are not issued keys",
        ),
    ),
    Release(
        "1.0.0",
        date(2026, 1, 20),
        "the interface is settled",
        notes=(
            "the names exported from hasplib are the ones that will not move",
            "the refusal codes in hasplib.catalogue are part of that promise",
            "everything under hasplib.persistence moves whenever the schema does",
        ),
    ),
)
"""Every release, oldest first."""

BY_VERSION = {item.version: item for item in RELEASES}
CURRENT = RELEASES[-1]


def release(version: str) -> Release:
    found = BY_VERSION.get(version)
    if found is None:
        raise NotFoundError("no such release", version=version)
    return found


def versions() -> tuple[str, ...]:
    return tuple(item.version for item in RELEASES)


def migrations_between(older: str, newer: str) -> tuple[str, ...]:
    """The alembic revisions an upgrade crosses, oldest first.

    Inclusive of the newer version and exclusive of the older one, which is
    what an upgrade actually runs.
    """
    first = release(older).parts
    last = release(newer).parts
    if first > last:
        raise NotFoundError("that is a downgrade", older=older, newer=newer)
    found: list[str] = []
    for item in RELEASES:
        if first < item.parts <= last:
            found.extend(item.migrations)
    return tuple(found)


def notes_between(older: str, newer: str) -> tuple[str, ...]:
    """What somebody upgrading needs to have read."""
    first = release(older).parts
    last = release(newer).parts
    found: list[str] = []
    for item in RELEASES:
        if first < item.parts <= last:
            found.extend(f"{item.version}: {note}" for note in item.notes)
    return tuple(found)


def history_sheet() -> tuple[str, ...]:
    """The whole history, which is what the release command prints."""
    lines = [f"{len(RELEASES)} releases, {CURRENT.version} is current"]
    lines.extend(f"  {item.describe()}" for item in RELEASES)
    return tuple(lines)


def upgrade_sheet(older: str, newer: str = "") -> tuple[str, ...]:
    """What an upgrade crosses and what to read before starting it."""
    target = newer or CURRENT.version
    crossings = migrations_between(older, target)
    lines = [f"{older} to {target}"]
    if crossings:
        lines.append(f"{len(crossings)} migrations: {', '.join(crossings)}")
    else:
        lines.append("no migrations to run")
    notes = notes_between(older, target)
    if notes:
        lines.append("read first")
        lines.extend(f"  {note}" for note in notes)
    return tuple(lines)


def every_migration() -> tuple[str, ...]:
    """Every revision any release has shipped, which the tests check."""
    found: list[str] = []
    for item in RELEASES:
        found.extend(item.migrations)
    return tuple(found)


def current_release(version: str) -> Release:
    """The release a version string belongs to, for the version command."""
    return release(version)


def describe_current() -> tuple[str, ...]:
    lines = [CURRENT.describe()]
    lines.extend(f"  {note}" for note in CURRENT.notes)
    return tuple(lines)


def matches_package(version: str) -> bool:
    """Whether the package version and the latest release agree."""
    return version == CURRENT.version


def unreleased(version: str) -> Sequence[str]:
    """Releases newer than a version, which is what somebody is behind by."""
    held = release(version).parts
    return tuple(item.version for item in RELEASES if item.parts > held)

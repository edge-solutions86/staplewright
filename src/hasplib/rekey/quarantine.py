"""Bittings that are out of service, and the ones that are never coming back.

A rekey retires a cut. The old key still exists somewhere, in a drawer or on
a ring or in a pocket in another state, so putting that cut back into service
on a different door hands somebody a key they did not know they had.

Two kinds of retirement, and the difference is the whole file. A cut retired
because the system was reorganised is only superseded, and after long enough
the shop will use it again rather than shrink the array forever. A cut
retired because a key went missing is compromised and never comes back, no
matter how many years pass or who asks.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting


class RetirementReason(StrEnum):
    """Why a cut left service."""

    COMPROMISED = "compromised"
    SUPERSEDED = "superseded"
    DAMAGED = "damaged"
    RESERVED = "reserved"


PERMANENT = frozenset({RetirementReason.COMPROMISED})
"""Reasons that never expire, whatever the release window says."""


@dataclass(frozen=True)
class RetiredBitting:
    """One cut that is out of service, and the story behind it."""

    bitting: Bitting
    reason: RetirementReason
    retired_on: date
    symbol: str = ""
    note: str = ""

    @property
    def is_permanent(self) -> bool:
        return self.reason in PERMANENT

    def released_by(self, today: date, after_years: int) -> bool:
        """Whether a superseded cut has sat out long enough to come back."""
        if self.is_permanent:
            return False
        years = today.year - self.retired_on.year
        if (today.month, today.day) < (self.retired_on.month, self.retired_on.day):
            years -= 1
        return years >= after_years


class Quarantine:
    """Every cut the shop will not issue, with the reason it will not."""

    def __init__(self, release_after_years: int = 7) -> None:
        if release_after_years < 1:
            raise ValueError("a release window is at least a year")
        self.release_after_years = release_after_years
        self._retired: dict[tuple[int, ...], RetiredBitting] = {}

    def retire(
        self,
        bitting: Bitting,
        reason: RetirementReason,
        when: date,
        symbol: str = "",
        note: str = "",
    ) -> RetiredBitting:
        """Take a cut out of service. A compromised cut wins over anything."""
        held = self._retired.get(bitting.depths)
        upgrading = reason in PERMANENT
        if held is not None and (held.is_permanent or not upgrading):
            raise ConflictError(
                "that cut is already retired",
                bitting=bitting.render(),
                reason=str(held.reason),
            )
        entry = RetiredBitting(bitting, reason, when, symbol, note)
        self._retired[bitting.depths] = entry
        return entry

    def entry(self, bitting: Bitting) -> RetiredBitting:
        try:
            return self._retired[bitting.depths]
        except KeyError:
            raise NotFoundError("that cut is not retired", bitting=bitting.render()) from None

    def release(self, bitting: Bitting) -> RetiredBitting:
        """Put a cut back deliberately. Refused for anything compromised."""
        entry = self.entry(bitting)
        if entry.is_permanent:
            raise ConflictError(
                "a compromised cut does not come back",
                bitting=bitting.render(),
                retired_on=entry.retired_on.isoformat(),
            )
        del self._retired[bitting.depths]
        return entry

    def is_retired(self, bitting: Bitting, today: date | None = None) -> bool:
        """Whether the shop would refuse this cut today."""
        entry = self._retired.get(bitting.depths)
        if entry is None:
            return False
        if today is None:
            return True
        return not entry.released_by(today, self.release_after_years)

    def available(
        self, bittings: Iterable[Bitting], today: date | None = None
    ) -> tuple[Bitting, ...]:
        """Filter a run of candidate cuts down to the ones still usable."""
        return tuple(b for b in bittings if not self.is_retired(b, today))

    def permanent(self) -> tuple[RetiredBitting, ...]:
        found = [entry for entry in self._retired.values() if entry.is_permanent]
        found.sort(key=lambda entry: (entry.retired_on, entry.bitting.render()))
        return tuple(found)

    def releasable(self, today: date) -> tuple[RetiredBitting, ...]:
        """Cuts that have sat out their window and could be used again."""
        found = [
            entry
            for entry in self._retired.values()
            if entry.released_by(today, self.release_after_years)
        ]
        found.sort(key=lambda entry: (entry.retired_on, entry.bitting.render()))
        return tuple(found)

    def by_reason(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for entry in self._retired.values():
            counts[str(entry.reason)] = counts.get(str(entry.reason), 0) + 1
        return dict(sorted(counts.items()))

    def __contains__(self, bitting: object) -> bool:
        if not isinstance(bitting, Bitting):
            return False
        return bitting.depths in self._retired

    def __iter__(self) -> Iterator[RetiredBitting]:
        entries = sorted(self._retired.values(), key=lambda entry: entry.bitting.render())
        return iter(entries)

    def __len__(self) -> int:
        return len(self._retired)

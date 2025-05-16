"""The hardware survey, which is what a building looks like before anybody
touches it.

A survey is a walk with a clipboard. Every opening gets a condition, a
manufacturer if the lock is old enough to have lost its label, and a note
about anything that will make the rekey take longer than twenty minutes. The
deficiencies fall out of it: a door with a cylinder nobody can identify, a
door with the wrong function for the room, a door whose lock is so worn it
takes two hands.

The value is entirely in doing it before the work is quoted. A survey after
the crew arrives is called a surprise, and the shop bills for those.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date
from enum import IntEnum, StrEnum
from typing import Any

from hasplib.errors import ConflictError, InputError
from hasplib.store import Registry


class Condition(IntEnum):
    """How the hardware is doing. Ordered so a sort means something."""

    GOOD = 1
    WORN = 2
    FAILING = 3
    BROKEN = 4

    @property
    def label(self) -> str:
        return self.name.lower()


class Deficiency(StrEnum):
    """What a surveyor found that somebody has to deal with."""

    UNIDENTIFIED = "cylinder cannot be identified"
    WRONG_FUNCTION = "function does not suit the room"
    NO_CYLINDER = "keyed opening with nothing in it"
    WORN_OUT = "hardware needs replacing before it is rekeyed"
    NO_STRIKE = "frame or strike needs work first"
    UNKNOWN_KEY = "opens to a key nobody has a record of"


@dataclass(frozen=True)
class SurveyLine:
    """One opening as it was found."""

    opening: str
    surveyed_on: date
    surveyor: str
    condition: Condition
    manufacturer: str = ""
    cylinder_serial: str = ""
    deficiencies: tuple[Deficiency, ...] = ()
    note: str = ""

    def __post_init__(self) -> None:
        if not self.opening.strip():
            raise InputError("a survey line names an opening")
        if not self.surveyor.strip():
            raise InputError("a survey line names who walked it", opening=self.opening)
        if len(set(self.deficiencies)) != len(self.deficiencies):
            raise ConflictError("a deficiency is listed twice", opening=self.opening)

    @property
    def needs_work_first(self) -> bool:
        """Whether something has to happen before a rekey is worth booking."""
        blocking = {Deficiency.WORN_OUT, Deficiency.NO_STRIKE, Deficiency.NO_CYLINDER}
        return bool(blocking & set(self.deficiencies))

    @property
    def is_sound(self) -> bool:
        return self.condition <= Condition.WORN and not self.deficiencies

    def describe(self) -> str:
        found = ", ".join(item.value for item in self.deficiencies) or "nothing to report"
        return f"{self.opening} {self.condition.label}: {found}"


class Survey:
    """One walk round a building, line by line."""

    def __init__(self, building: str, walked_on: date) -> None:
        if not building.strip():
            raise InputError("a survey covers a building")
        self.building = building
        self.walked_on = walked_on
        self._lines: Registry[SurveyLine] = Registry("survey line", "opening")

    def record(self, line: SurveyLine) -> SurveyLine:
        return self._lines.add(line.opening, line)

    def line(self, opening: str) -> SurveyLine:
        return self._lines.get(opening)

    def revise(self, opening: str, **changes: Any) -> SurveyLine:
        """A second look at one door, which happens on every survey."""
        line = self._lines.get(opening)
        return self._lines.put(opening, replace(line, **changes))

    def lines(self) -> tuple[SurveyLine, ...]:
        return self._lines.by_key()

    def with_deficiency(self, kind: Deficiency) -> tuple[SurveyLine, ...]:
        return tuple(line for line in self.lines() if kind in line.deficiencies)

    def blocking(self) -> tuple[SurveyLine, ...]:
        """Doors that need other work before a rekey is worth booking."""
        return tuple(line for line in self.lines() if line.needs_work_first)

    def sound(self) -> tuple[SurveyLine, ...]:
        return tuple(line for line in self.lines() if line.is_sound)

    def worst_first(self) -> tuple[SurveyLine, ...]:
        found = sorted(
            self.lines(),
            key=lambda line: (-int(line.condition), -len(line.deficiencies), line.opening),
        )
        return tuple(found)

    def counts_by_condition(self) -> dict[str, int]:
        counts = {condition.label: 0 for condition in Condition}
        for line in self.lines():
            counts[line.condition.label] += 1
        return counts

    def counts_by_deficiency(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for line in self.lines():
            for kind in line.deficiencies:
                counts[kind.value] = counts.get(kind.value, 0) + 1
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def coverage(self, openings: tuple[str, ...]) -> tuple[str, ...]:
        """Doors on the list that nobody walked."""
        walked = {line.opening for line in self.lines()}
        return tuple(sorted(number for number in openings if number not in walked))

    def surveyors(self) -> tuple[str, ...]:
        return tuple(sorted({line.surveyor for line in self.lines()}))

    def __contains__(self, opening: object) -> bool:
        return opening in self._lines

    def __iter__(self) -> Iterator[SurveyLine]:
        return iter(self.lines())

    def __len__(self) -> int:
        return len(self._lines)

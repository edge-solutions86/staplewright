"""Openings, which is the word for a door once a locksmith is looking at it.

An opening is a position, not a lock. It has a building, a floor, a number
painted on it and a function, and it keeps that identity through four
different locks over thirty years. That is why the whole system is keyed on
openings rather than on cylinders, and why an opening number is the thing
written on every work order.

The function matters more than it looks. A classroom lockset locks from
outside with a key and always opens from inside, so it is life safety
neutral. A storeroom function is always locked outside and needs a key every
time. An office function can be left unlocked, which is the one that produces
the phone call about a stolen laptop. The shop refuses some combinations of
function and space outright, and the refusals are in this file because they
are about the door rather than about the key.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from enum import StrEnum

from hasplib.errors import ConflictError, InputError, NotFoundError


class Function(StrEnum):
    """What the lockset does when nobody is holding a key."""

    CLASSROOM = "classroom"
    STOREROOM = "storeroom"
    OFFICE = "office"
    ENTRANCE = "entrance"
    PRIVACY = "privacy"
    EXIT_ONLY = "exit_only"
    PADLOCK = "padlock"
    CABINET = "cabinet"


KEYED_FUNCTIONS = frozenset(
    {
        Function.CLASSROOM,
        Function.STOREROOM,
        Function.OFFICE,
        Function.ENTRANCE,
        Function.PADLOCK,
        Function.CABINET,
    }
)
"""Functions that take a cylinder. Privacy and exit only do not."""


@dataclass(frozen=True)
class Building:
    """One building, as facilities numbers it."""

    code: str
    name: str
    year_built: int | None = None

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise InputError("a building needs a code")
        if self.code != self.code.strip().upper():
            raise InputError("building codes are upper case", code=self.code)
        if self.year_built is not None and not 1850 <= self.year_built <= 2100:
            raise InputError("that is not a year a building went up", year=self.year_built)


@dataclass(frozen=True)
class Opening:
    """One door, hatch, gate or cabinet the shop is responsible for."""

    number: str
    building: str
    floor: str
    room: str
    function: Function
    description: str = ""
    exterior: bool = False

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("an opening needs a number")
        if not self.building.strip():
            raise InputError("an opening belongs to a building", opening=self.number)
        if self.exterior and self.function in {Function.PRIVACY, Function.CABINET}:
            raise ConflictError(
                "that function does not go on an exterior door",
                opening=self.number,
                function=str(self.function),
            )

    @property
    def is_keyed(self) -> bool:
        return self.function in KEYED_FUNCTIONS

    @property
    def label(self) -> str:
        return f"{self.building} {self.room}"

    def describe(self) -> str:
        where = f"{self.building} floor {self.floor} room {self.room}"
        return f"{self.number}: {where}, {self.function.value}"


class OpeningSchedule:
    """Every opening on campus, indexed the two ways anybody asks for them.

    By number, because that is what a work order carries, and by building,
    because that is how a survey gets walked.
    """

    def __init__(self) -> None:
        self._buildings: dict[str, Building] = {}
        self._openings: dict[str, Opening] = {}

    def add_building(self, building: Building) -> Building:
        if building.code in self._buildings:
            raise ConflictError("that building is already on the list", code=building.code)
        self._buildings[building.code] = building
        return building

    def building(self, code: str) -> Building:
        try:
            return self._buildings[code]
        except KeyError:
            raise NotFoundError("no such building", code=code) from None

    def add(self, opening: Opening) -> Opening:
        if opening.number in self._openings:
            raise ConflictError("that opening number is taken", number=opening.number)
        self.building(opening.building)
        self._openings[opening.number] = opening
        return opening

    def opening(self, number: str) -> Opening:
        try:
            return self._openings[number]
        except KeyError:
            raise NotFoundError("no such opening", number=number) from None

    def remove(self, number: str) -> Opening:
        """A door that got bricked up. It happens twice a decade."""
        opening = self.opening(number)
        del self._openings[number]
        return opening

    def in_building(self, code: str) -> tuple[Opening, ...]:
        self.building(code)
        found = [o for o in self._openings.values() if o.building == code]
        found.sort(key=lambda o: (o.floor, o.room, o.number))
        return tuple(found)

    def on_floor(self, code: str, floor: str) -> tuple[Opening, ...]:
        return tuple(o for o in self.in_building(code) if o.floor == floor)

    def with_function(self, function: Function) -> tuple[Opening, ...]:
        found = [o for o in self._openings.values() if o.function is function]
        found.sort(key=lambda o: o.number)
        return tuple(found)

    def keyed_openings(self) -> tuple[Opening, ...]:
        found = [o for o in self._openings.values() if o.is_keyed]
        found.sort(key=lambda o: o.number)
        return tuple(found)

    def exterior_openings(self) -> tuple[Opening, ...]:
        found = [o for o in self._openings.values() if o.exterior]
        found.sort(key=lambda o: o.number)
        return tuple(found)

    def buildings(self) -> tuple[Building, ...]:
        return tuple(self._buildings[code] for code in sorted(self._buildings))

    def floors(self, code: str) -> tuple[str, ...]:
        return tuple(sorted({o.floor for o in self.in_building(code)}))

    def count_by_building(self) -> dict[str, int]:
        counts = {code: 0 for code in self._buildings}
        for opening in self._openings.values():
            counts[opening.building] += 1
        return dict(sorted(counts.items()))

    def __contains__(self, number: object) -> bool:
        return number in self._openings

    def __iter__(self) -> Iterator[Opening]:
        return iter(self._openings[number] for number in sorted(self._openings))

    def __len__(self) -> int:
        return len(self._openings)

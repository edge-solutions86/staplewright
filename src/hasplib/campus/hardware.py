"""Cylinders, and where each one is at the moment.

A cylinder is a serial numbered object. It gets pinned, installed, pulled,
rekeyed, put in a drawer for two years and installed somewhere else, and the
shop is expected to know which of those happened. Openings are permanent and
cylinders move, so the register is keyed on the cylinder and points at the
opening rather than the other way round.

One opening can carry more than one cylinder. A storeroom door with a deadbolt
above the lockset is two, and an exit device with an outside trim cylinder is
two more, so the position is part of what makes an installation unique. What
is not allowed is two cylinders in one position, and a cylinder in two places
at once, which is the pair of mistakes a paper register makes every year.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from enum import StrEnum

from hasplib.campus.openings import Opening, OpeningSchedule
from hasplib.errors import ConflictError, InputError, NotFoundError


class CylinderFormat(StrEnum):
    """The shape of the thing, which decides what housing takes it."""

    MORTISE = "mortise"
    RIM = "rim"
    KIK = "kik"
    SFIC = "sfic"
    PADLOCK = "padlock"
    CAM = "cam"


class Position(StrEnum):
    """Where on an opening a cylinder sits."""

    LOCKSET = "lockset"
    DEADBOLT = "deadbolt"
    TRIM = "trim"
    SHACKLE = "shackle"


@dataclass(frozen=True)
class Cylinder:
    """One serial numbered cylinder or core."""

    serial: str
    format: CylinderFormat
    section: str
    chambers: int
    manufacturer: str = ""
    spooled: bool = False
    installed_at: str | None = None
    position: Position | None = None
    group: str = ""

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise InputError("a cylinder needs a serial")
        if self.chambers < 1:
            raise InputError("a cylinder has chambers", serial=self.serial)
        if (self.installed_at is None) != (self.position is None):
            raise ConflictError(
                "an installed cylinder has a position and a shelved one has neither",
                serial=self.serial,
            )

    @property
    def is_installed(self) -> bool:
        return self.installed_at is not None

    @property
    def is_core(self) -> bool:
        return self.format is CylinderFormat.SFIC

    def describe(self) -> str:
        where = f"{self.installed_at} {self.position}" if self.is_installed else "on the shelf"
        return f"{self.serial} {self.format.value} {self.section}, {where}"


class HardwareRegister:
    """Every cylinder the shop owns, and where it is."""

    def __init__(self, openings: OpeningSchedule) -> None:
        self.openings = openings
        self._cylinders: dict[str, Cylinder] = {}

    def add(self, cylinder: Cylinder) -> Cylinder:
        if cylinder.serial in self._cylinders:
            raise ConflictError("that serial is already on the register", serial=cylinder.serial)
        if cylinder.is_installed:
            raise ConflictError(
                "a cylinder joins the register on the shelf and gets installed after",
                serial=cylinder.serial,
            )
        self._cylinders[cylinder.serial] = cylinder
        return cylinder

    def cylinder(self, serial: str) -> Cylinder:
        try:
            return self._cylinders[serial]
        except KeyError:
            raise NotFoundError("no such cylinder", serial=serial) from None

    def install(self, serial: str, opening_number: str, position: Position) -> Cylinder:
        """Put a cylinder in a door, refusing anything already occupied."""
        cylinder = self.cylinder(serial)
        opening = self.openings.opening(opening_number)
        if cylinder.is_installed:
            raise ConflictError(
                "that cylinder is already in a door",
                serial=serial,
                installed_at=cylinder.installed_at,
            )
        if not opening.is_keyed:
            raise ConflictError(
                "that opening does not take a cylinder",
                opening=opening_number,
                function=str(opening.function),
            )
        sitting = self.at(opening_number, position)
        if sitting is not None:
            raise ConflictError(
                "something is already in that position",
                opening=opening_number,
                position=str(position),
                serial=sitting.serial,
            )
        updated = replace(cylinder, installed_at=opening_number, position=position)
        self._cylinders[serial] = updated
        return updated

    def pull(self, serial: str) -> Cylinder:
        """Take a cylinder out and put it back on the shelf."""
        cylinder = self.cylinder(serial)
        if not cylinder.is_installed:
            raise ConflictError("that cylinder is already on the shelf", serial=serial)
        updated = replace(cylinder, installed_at=None, position=None)
        self._cylinders[serial] = updated
        return updated

    def move(self, serial: str, opening_number: str, position: Position) -> Cylinder:
        """Pull and install in one step, which is what a swap really is."""
        self.pull(serial)
        return self.install(serial, opening_number, position)

    def assign_group(self, serial: str, group: str) -> Cylinder:
        cylinder = self.cylinder(serial)
        updated = replace(cylinder, group=group)
        self._cylinders[serial] = updated
        return updated

    def at(self, opening_number: str, position: Position) -> Cylinder | None:
        for cylinder in self._cylinders.values():
            if cylinder.installed_at == opening_number and cylinder.position is position:
                return cylinder
        return None

    def on_opening(self, opening_number: str) -> tuple[Cylinder, ...]:
        self.openings.opening(opening_number)
        found = [c for c in self._cylinders.values() if c.installed_at == opening_number]
        found.sort(key=lambda c: (str(c.position), c.serial))
        return tuple(found)

    def in_building(self, code: str) -> tuple[Cylinder, ...]:
        numbers = {opening.number for opening in self.openings.in_building(code)}
        found = [c for c in self._cylinders.values() if c.installed_at in numbers]
        found.sort(key=lambda c: (c.installed_at or "", str(c.position)))
        return tuple(found)

    def on_the_shelf(self) -> tuple[Cylinder, ...]:
        found = [c for c in self._cylinders.values() if not c.is_installed]
        found.sort(key=lambda c: c.serial)
        return tuple(found)

    def in_group(self, group: str) -> tuple[Cylinder, ...]:
        found = [c for c in self._cylinders.values() if c.group == group]
        found.sort(key=lambda c: c.serial)
        return tuple(found)

    def unkeyed(self) -> tuple[Cylinder, ...]:
        """Installed cylinders nobody has said what they open."""
        found = [c for c in self._cylinders.values() if c.is_installed and not c.group]
        found.sort(key=lambda c: c.serial)
        return tuple(found)

    def uncovered_openings(self) -> tuple[Opening, ...]:
        """Keyed openings with no cylinder in the lockset position."""
        found = [
            opening
            for opening in self.openings.keyed_openings()
            if self.at(opening.number, Position.LOCKSET) is None
        ]
        return tuple(found)

    def __contains__(self, serial: object) -> bool:
        return serial in self._cylinders

    def __iter__(self) -> Iterator[Cylinder]:
        return iter(self._cylinders[serial] for serial in sorted(self._cylinders))

    def __len__(self) -> int:
        return len(self._cylinders)

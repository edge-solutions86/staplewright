"""Zones, which is how a campus with forty buildings stops being forty things.

The shop does not think in buildings. It thinks in the science precinct, the
lower campus, the two houses out on the point, because that is how the crews
are assigned and how the after hours call rota is drawn. A zone is that
grouping, and it earns its place the moment somebody asks for a key to
everything in the science precinct: without zones that request is a list of
eleven buildings that will be wrong within a year.

A building sits in one zone. That is a decision rather than a fact about the
world, and the register enforces it, because a building in two zones means
two zone masters open it and neither of them knows about the other.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass

from hasplib.campus.openings import OpeningSchedule
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.store import Registry


@dataclass(frozen=True)
class Zone:
    """A group of buildings the shop treats as one thing."""

    code: str
    name: str
    after_hours_rota: str = ""
    note: str = ""

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise InputError("a zone needs a code")
        if self.code != self.code.strip().upper():
            raise InputError("zone codes are upper case", code=self.code)
        if not self.name.strip():
            raise InputError("a zone needs a name", code=self.code)


class ZoneMap:
    """Every zone, and which building sits in which."""

    def __init__(self, openings: OpeningSchedule) -> None:
        self.openings = openings
        self._zones: Registry[Zone] = Registry("zone", "code")
        self._where: dict[str, str] = {}

    def add(self, zone: Zone) -> Zone:
        return self._zones.add(zone.code, zone)

    def zone(self, code: str) -> Zone:
        return self._zones.get(code)

    def place(self, building: str, zone_code: str) -> str:
        """Put a building in a zone, refusing one that is already placed."""
        self.openings.building(building)
        self._zones.get(zone_code)
        held = self._where.get(building)
        if held is not None and held != zone_code:
            raise ConflictError("that building is already in a zone", building=building, zone=held)
        self._where[building] = zone_code
        return zone_code

    def move(self, building: str, zone_code: str) -> str:
        """Move a building deliberately, which happens after a reorganisation."""
        self.openings.building(building)
        self._zones.get(zone_code)
        if building not in self._where:
            raise NotFoundError("that building is not in a zone", building=building)
        self._where[building] = zone_code
        return zone_code

    def zone_of(self, building: str) -> Zone | None:
        code = self._where.get(building)
        return None if code is None else self._zones.get(code)

    def buildings_in(self, zone_code: str) -> tuple[str, ...]:
        self._zones.get(zone_code)
        return tuple(
            sorted(building for building, code in self._where.items() if code == zone_code)
        )

    def openings_in(self, zone_code: str) -> tuple[str, ...]:
        """Every keyed door in a zone, which is what a zone master would open."""
        found: list[str] = []
        for building in self.buildings_in(zone_code):
            found.extend(
                opening.number
                for opening in self.openings.in_building(building)
                if opening.is_keyed
            )
        return tuple(sorted(found))

    def unplaced(self) -> tuple[str, ...]:
        """Buildings nobody has put in a zone."""
        return tuple(
            sorted(
                building.code
                for building in self.openings.buildings()
                if building.code not in self._where
            )
        )

    def empty_zones(self) -> tuple[str, ...]:
        return tuple(code for code in self._zones.sorted_keys() if not self.buildings_in(code))

    def counts(self) -> dict[str, int]:
        """Doors per zone, biggest first, for the crew assignment."""
        counts = {code: len(self.openings_in(code)) for code in self._zones.sorted_keys()}
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def rota_for(self, building: str) -> str:
        """Who is on call for this building tonight."""
        zone = self.zone_of(building)
        if zone is None:
            raise NotFoundError("that building is not in a zone", building=building)
        return zone.after_hours_rota

    def zones(self) -> tuple[Zone, ...]:
        return self._zones.by_key()

    def __contains__(self, code: object) -> bool:
        return code in self._zones

    def __iter__(self) -> Iterator[Zone]:
        return iter(self.zones())

    def __len__(self) -> int:
        return len(self._zones)


def spans_zones(zones: ZoneMap, buildings: Sequence[str]) -> bool:
    """Whether a set of buildings reaches more than one zone."""
    found = {zones.zone_of(building) for building in buildings}
    return len({zone.code for zone in found if zone is not None}) > 1

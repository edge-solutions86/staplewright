"""Pin segments and the kit they come out of.

A chamber is a column of little cylinders. From the bottom of the plug up:
one bottom pin sitting against the key, then a master wafer for every extra
depth the chamber has to break at, then a driver on top of all of it, then a
spring. Every one of those is a part with a length and a bin in the cabinet.

Lengths are in mils. A kit is finite, and the shop runs out of the useful
middle lengths long before it runs out of the extremes, which is why drawing
from a kit is a transaction that can fail rather than a lookup.

Drivers come in three profiles. A standard driver is a plain cylinder. A
spool or a serrated driver has a waist cut into it so that a pick sets it
false, and the dorms get those on anything facing a corridor.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from enum import StrEnum

from hasplib.errors import CapacityError, PinningError


class SegmentKind(StrEnum):
    """Where in the column a segment belongs."""

    BOTTOM = "bottom"
    WAFER = "wafer"
    DRIVER = "driver"
    SPRING = "spring"


class DriverProfile(StrEnum):
    """What the side of a driver looks like."""

    STANDARD = "standard"
    SPOOL = "spool"
    SERRATED = "serrated"


@dataclass(frozen=True)
class PinSegment:
    """One piece of brass, as it sits in a bin."""

    kind: SegmentKind
    length_mils: int
    profile: DriverProfile = DriverProfile.STANDARD

    def __post_init__(self) -> None:
        if self.length_mils <= 0:
            raise PinningError("a segment has a length", kind=str(self.kind))
        if self.profile is not DriverProfile.STANDARD and self.kind is not SegmentKind.DRIVER:
            raise PinningError(
                "only drivers come in profiles",
                kind=str(self.kind),
                profile=str(self.profile),
            )

    @property
    def bin_code(self) -> str:
        """The label on the drawer. Profile only shows up on drivers."""
        if self.kind is SegmentKind.DRIVER and self.profile is not DriverProfile.STANDARD:
            return f"{self.kind.value[0].upper()}{self.length_mils}{self.profile.value[0].upper()}"
        return f"{self.kind.value[0].upper()}{self.length_mils}"

    def __str__(self) -> str:
        return self.bin_code


class PinKit:
    """The cabinet at the bench, counted.

    Nothing here is clever. It is a count per bin, and the only interesting
    thing it does is refuse a draw it cannot fill in full, because a half
    pinned cylinder on the bench is worse than one that was never started.
    """

    def __init__(self, name: str, stock: dict[PinSegment, int] | None = None) -> None:
        self.name = name
        self._stock: dict[PinSegment, int] = {}
        for segment, count in (stock or {}).items():
            if count < 0:
                raise PinningError("a bin cannot hold less than nothing", segment=str(segment))
            if count:
                self._stock[segment] = count

    def stock_of(self, segment: PinSegment) -> int:
        return self._stock.get(segment, 0)

    def add(self, segment: PinSegment, count: int) -> None:
        """Put parts in. Restocking is the only way a kit grows."""
        if count < 1:
            raise PinningError("restocking adds parts", count=count)
        self._stock[segment] = self._stock.get(segment, 0) + count

    def lengths(self, kind: SegmentKind, profile: DriverProfile | None = None) -> tuple[int, ...]:
        """Lengths the cabinet has any of, shallow to deep."""
        found = {
            segment.length_mils
            for segment, count in self._stock.items()
            if segment.kind is kind
            and count > 0
            and (profile is None or segment.profile is profile)
        }
        return tuple(sorted(found))

    def holds(self, segment: PinSegment, count: int = 1) -> bool:
        return self.stock_of(segment) >= count

    def shortfall(self, wanted: Iterable[PinSegment]) -> dict[PinSegment, int]:
        """What a draw would be short by, without taking anything."""
        needed: dict[PinSegment, int] = {}
        for segment in wanted:
            needed[segment] = needed.get(segment, 0) + 1
        short: dict[PinSegment, int] = {}
        for segment, count in needed.items():
            missing = count - self.stock_of(segment)
            if missing > 0:
                short[segment] = missing
        return short

    def draw(self, wanted: Iterable[PinSegment]) -> None:
        """Take a whole list of parts, or take nothing and say why."""
        wanted = list(wanted)
        short = self.shortfall(wanted)
        if short:
            raise CapacityError(
                "the kit cannot fill that",
                kit=self.name,
                short={str(segment): count for segment, count in short.items()},
            )
        for segment in wanted:
            remaining = self._stock[segment] - 1
            if remaining:
                self._stock[segment] = remaining
            else:
                del self._stock[segment]

    def put_back(self, segments: Iterable[PinSegment]) -> None:
        """Parts off a cylinder that came apart go back in the drawer."""
        for segment in segments:
            self._stock[segment] = self._stock.get(segment, 0) + 1

    def total(self) -> int:
        return sum(self._stock.values())

    def bins(self) -> tuple[tuple[PinSegment, int], ...]:
        """Every bin with something in it, in bin code order."""
        return tuple(sorted(self._stock.items(), key=lambda item: item[0].bin_code))

    def copy(self) -> PinKit:
        clone = PinKit(self.name)
        clone._stock = dict(self._stock)
        return clone


def standard_kit(
    name: str,
    bottom_lengths: Iterable[int],
    wafer_lengths: Iterable[int],
    driver_lengths: Iterable[int],
    count: int = 100,
) -> PinKit:
    """A kit stocked the way a new cabinet arrives, even across every bin."""
    kit = PinKit(name)
    for length in bottom_lengths:
        kit.add(PinSegment(SegmentKind.BOTTOM, length), count)
    for length in wafer_lengths:
        kit.add(PinSegment(SegmentKind.WAFER, length), count)
    for length in driver_lengths:
        kit.add(PinSegment(SegmentKind.DRIVER, length), count)
    return kit

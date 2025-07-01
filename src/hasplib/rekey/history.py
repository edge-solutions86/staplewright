"""What has been done to a cylinder, which is the only case for replacing it.

A cylinder does not wear out on a schedule. It wears out from being taken
apart: the plug gets marked, the cap screws round out, the chambers pick up
brass dust, and the eleventh repin is the one where a pin sticks and somebody
spends an afternoon on a door that used to work.

Nobody notices that happening because each repin is twenty minutes and looks
like nothing. The history is what turns it into a number, and the number is
what gets a cylinder replaced during a rekey the shop is doing anyway rather
than in an emergency six months later.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator, Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.errors import ConflictError, InputError
from hasplib.keys.symbol import KeySymbol


@dataclass(frozen=True)
class RekeyEvent:
    """One time a cylinder came apart, and what it came back as."""

    serial: str
    happened_on: date
    to_symbols: tuple[KeySymbol, ...]
    order_number: str = ""
    technician: str = ""
    reason: str = ""

    def __post_init__(self) -> None:
        if not self.serial.strip():
            unnamed = "a rekey event names a cylinder"
            raise InputError(unnamed)
        if not self.to_symbols:
            raise ConflictError("a cylinder comes back keyed to something", serial=self.serial)
        if len(set(self.to_symbols)) != len(self.to_symbols):
            raise ConflictError("a symbol is on the cylinder twice", serial=self.serial)

    @property
    def wafers(self) -> int:
        """One less than the symbols is the floor on the wafers it carries."""
        return max(len(self.to_symbols) - 1, 0)

    def describe(self) -> str:
        symbols = ", ".join(symbol.render() for symbol in self.to_symbols)
        return f"{self.happened_on.isoformat()}  {self.serial}  {symbols}"


class RekeyHistory:
    """Every repin the shop has recorded, by cylinder."""

    def __init__(self, replace_after: int = 10) -> None:
        if replace_after < 1:
            raise InputError("a replacement threshold is a count of repins")
        self.replace_after = replace_after
        self._events: list[RekeyEvent] = []

    def record(self, event: RekeyEvent) -> RekeyEvent:
        """Write one repin down. Events can arrive out of order and do."""
        self._events.append(event)
        return event

    def record_all(self, events: Iterable[RekeyEvent]) -> None:
        for event in events:
            self.record(event)

    def events_for(self, serial: str) -> tuple[RekeyEvent, ...]:
        found = [event for event in self._events if event.serial == serial]
        found.sort(key=lambda event: (event.happened_on, event.order_number))
        return tuple(found)

    def times_rekeyed(self, serial: str) -> int:
        return len(self.events_for(serial))

    def last_rekeyed(self, serial: str) -> date | None:
        found = self.events_for(serial)
        return found[-1].happened_on if found else None

    def current_symbols(self, serial: str) -> tuple[KeySymbol, ...]:
        """What the cylinder is keyed to now, which is its last event."""
        found = self.events_for(serial)
        return found[-1].to_symbols if found else ()

    def intervals(self, serial: str) -> tuple[int, ...]:
        """Days between one repin and the next, oldest first."""
        found = self.events_for(serial)
        return tuple(
            (later.happened_on - earlier.happened_on).days
            for earlier, later in zip(found, found[1:], strict=False)
        )

    def average_interval(self, serial: str) -> int | None:
        gaps = self.intervals(serial)
        if not gaps:
            return None
        return int(sum(gaps) / len(gaps))

    def worn_out(self) -> tuple[tuple[str, int], ...]:
        """Cylinders past the threshold, most repinned first."""
        counts: dict[str, int] = {}
        for event in self._events:
            counts[event.serial] = counts.get(event.serial, 0) + 1
        found = [(serial, count) for serial, count in counts.items() if count >= self.replace_after]
        found.sort(key=lambda item: (-item[1], item[0]))
        return tuple(found)

    def never_rekeyed(self, serials: Sequence[str]) -> tuple[str, ...]:
        """Cylinders with no history at all, which is either new or unrecorded."""
        seen = {event.serial for event in self._events}
        return tuple(sorted(serial for serial in serials if serial not in seen))

    def busiest(self, limit: int = 5) -> tuple[tuple[str, int], ...]:
        counts: dict[str, int] = {}
        for event in self._events:
            counts[event.serial] = counts.get(event.serial, 0) + 1
        found = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
        return tuple(found[:limit])

    def by_technician(self, technician: str) -> tuple[RekeyEvent, ...]:
        found = [event for event in self._events if event.technician == technician]
        found.sort(key=lambda event: (event.happened_on, event.serial))
        return tuple(found)

    def since(self, when: date) -> tuple[RekeyEvent, ...]:
        found = [event for event in self._events if event.happened_on >= when]
        found.sort(key=lambda event: (event.happened_on, event.serial))
        return tuple(found)

    def lines(self, serial: str) -> tuple[str, ...]:
        """The history of one cylinder, for the back of a work order."""
        found = self.events_for(serial)
        if not found:
            return (f"{serial}: nothing on record",)
        header = f"{serial}: {len(found)} repins"
        return (header, *(event.describe() for event in found))

    def __iter__(self) -> Iterator[RekeyEvent]:
        return iter(self._events)

    def __len__(self) -> int:
        return len(self._events)

"""Sealed key boxes, which are the one place the shop hands over control.

Every building on a campus has a box by the main entrance with a key in it
that the fire service can reach without waiting for anybody. The box is
sealed with a numbered tag, and the seal is the whole of the security model:
nobody watches the box, so the only evidence that it has been opened is a
seal number that no longer matches the one on file.

A broken seal is not an incident on its own. Firefighters break them, and
that is the point of the box. What matters is that the key inside is out of
anybody's control from that moment, so the box goes into a state that is not
"fine" and not "compromised" either, and stays there until somebody puts a
new key in and a new seal on.

The rule that catches people out is the one about what is in the box. A box
holding a building master is a building master hanging on a wall outdoors.
The policy here says which symbol levels may sit in a box, and refuses the
rest, because the alternative is finding out from a fire report.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.lifecycle import require_state
from hasplib.store import Registry

CHECK_DAYS = 180


class BoxState(StrEnum):
    """Where a box stands."""

    SEALED = "sealed"
    OPENED = "opened"
    EMPTY = "empty"
    WITHDRAWN = "withdrawn"


class OpenedBy(StrEnum):
    """Who broke the seal, which decides what happens next."""

    FIRE = "fire service"
    SHOP = "the lock shop"
    UNKNOWN = "nobody signed for it"


LIVE_STATES = frozenset({BoxState.SEALED, BoxState.OPENED, BoxState.EMPTY})


@dataclass(frozen=True)
class BoxPolicy:
    """What the shop will put in a box on a wall.

    ``lowest_level`` is counted the way a symbol reads: the top key is zero
    and every master below it is one more. A box may hold a symbol no higher
    up the tree than this, so a policy of three refuses a building master and
    allows the change key for the entrance the box is bolted beside.
    """

    lowest_level: int = 3
    check_days: int = CHECK_DAYS
    reseal_days: int = 2

    def __post_init__(self) -> None:
        if self.lowest_level < 1:
            raise InputError("a box holds a key at some level")
        if self.check_days < 1:
            raise InputError("a box gets checked some day")
        if self.reseal_days < 0:
            raise InputError("a reseal window does not run backwards")

    def allows(self, symbol: KeySymbol) -> bool:
        return symbol.level >= self.lowest_level


@dataclass(frozen=True)
class KeyBox:
    """One sealed box, on one building, with one key in it."""

    number: str
    building: str
    opening: str
    symbol: KeySymbol
    seal: str
    sealed_on: date
    state: BoxState = BoxState.SEALED
    checked_on: date | None = None
    opened_on: date | None = None
    opened_by: OpenedBy | None = None
    note: str = ""

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise InputError("a box needs a number")
        if not self.seal.strip() and self.state is BoxState.SEALED:
            raise InputError("a sealed box has a seal number", box=self.number)

    @property
    def is_live(self) -> bool:
        return self.state in LIVE_STATES

    @property
    def holds_a_key(self) -> bool:
        return self.state in {BoxState.SEALED, BoxState.OPENED}

    def last_seen(self) -> date:
        """The most recent day anybody looked at it."""
        return self.checked_on or self.sealed_on

    def due_for_check(self, today: date, policy: BoxPolicy) -> bool:
        if self.state is BoxState.WITHDRAWN:
            return False
        return (today - self.last_seen()).days >= policy.check_days

    def overdue_reseal(self, today: date, policy: BoxPolicy) -> bool:
        """Open longer than the shop said it would take to get back out."""
        if self.state is not BoxState.OPENED or self.opened_on is None:
            return False
        return (today - self.opened_on).days > policy.reseal_days

    def describe(self) -> str:
        return f"{self.number} {self.building} {self.opening} {self.symbol.render()} {self.state}"


class BoxRegister:
    """Every sealed box on the campus."""

    def __init__(self, policy: BoxPolicy | None = None) -> None:
        self.policy = policy or BoxPolicy()
        self._boxes: Registry[KeyBox] = Registry("key box", "number")

    def install(self, box: KeyBox) -> KeyBox:
        """Put a box up, refusing a key nobody should leave on a wall."""
        if not self.policy.allows(box.symbol):
            raise ConflictError(
                "that symbol is too wide to leave in a box",
                box=box.number,
                symbol=box.symbol.render(),
                level=box.symbol.level,
            )
        if box.state is not BoxState.SEALED:
            raise ConflictError("a box goes up sealed", box=box.number)
        return self._boxes.add(box.number, box)

    def load(self, box: KeyBox) -> KeyBox:
        """Put a box back as it was stored, in whatever state it reached."""
        return self._boxes.put(box.number, box)

    def box(self, number: str) -> KeyBox:
        return self._boxes.get(number)

    def check(self, number: str, seal: str, when: date) -> KeyBox:
        """Walk past and read the seal. A seal that does not match is an opening.

        This is the method that does the work. Nobody reports opening a box,
        so the only way one is ever found open is somebody reading the number
        off the tag and it not being the number on file.
        """
        box = self.box(number)
        require_state(
            box.state,
            {BoxState.SEALED},
            "only a sealed box gets its seal read",
            box=number,
        )
        if seal.strip() == box.seal:
            return self._store(replace(box, checked_on=when))
        return self._store(
            replace(
                box,
                state=BoxState.OPENED,
                checked_on=when,
                opened_on=when,
                opened_by=OpenedBy.UNKNOWN,
                note=f"seal read {seal.strip()}, expected {box.seal}",
            )
        )

    def report_opened(self, number: str, by: OpenedBy, when: date, note: str = "") -> KeyBox:
        """Somebody said they opened it, which is better than finding out."""
        box = self.box(number)
        require_state(
            box.state,
            {BoxState.SEALED},
            "that box was already open",
            box=number,
        )
        return self._store(
            replace(
                box,
                state=BoxState.OPENED,
                opened_on=when,
                opened_by=by,
                note=note,
            )
        )

    def empty(self, number: str, when: date, note: str = "") -> KeyBox:
        """The key is gone. The box is still on the wall and still a hole."""
        box = self.box(number)
        require_state(
            box.state,
            {BoxState.OPENED},
            "a key only goes missing out of an open box",
            box=number,
        )
        return self._store(replace(box, state=BoxState.EMPTY, checked_on=when, note=note))

    def reseal(self, number: str, seal: str, symbol: KeySymbol, when: date) -> KeyBox:
        """A new key and a new seal, which is the only way back to sealed."""
        box = self.box(number)
        require_state(
            box.state,
            {BoxState.OPENED, BoxState.EMPTY},
            "that box is already sealed",
            box=number,
        )
        if not seal.strip():
            raise InputError("a reseal needs a seal number", box=number)
        if seal.strip() == box.seal:
            raise ConflictError("a reseal uses a new seal number", box=number, seal=seal.strip())
        if not self.policy.allows(symbol):
            raise ConflictError(
                "that symbol is too wide to leave in a box",
                box=number,
                symbol=symbol.render(),
            )
        return self._store(
            replace(
                box,
                state=BoxState.SEALED,
                seal=seal.strip(),
                symbol=symbol,
                sealed_on=when,
                checked_on=when,
                opened_on=None,
                opened_by=None,
                note="",
            )
        )

    def withdraw(self, number: str, when: date, note: str) -> KeyBox:
        """Take the box off the wall for good."""
        box = self.box(number)
        if box.state is BoxState.WITHDRAWN:
            raise ConflictError("that box is already off the wall", box=number)
        if not note.strip():
            raise InputError("taking a box down says why", box=number)
        return self._store(replace(box, state=BoxState.WITHDRAWN, checked_on=when, note=note))

    def _store(self, box: KeyBox) -> KeyBox:
        return self._boxes.put(box.number, box)

    def in_state(self, state: BoxState) -> tuple[KeyBox, ...]:
        return self._boxes.where(lambda box: box.state is state)

    def in_building(self, building: str) -> tuple[KeyBox, ...]:
        return self._boxes.where(lambda box: box.building == building)

    def holding(self, symbol: KeySymbol) -> tuple[KeyBox, ...]:
        return self._boxes.where(lambda box: box.symbol == symbol and box.holds_a_key)

    def open_boxes(self) -> tuple[KeyBox, ...]:
        """Everything not sealed and still on a wall, worst first."""
        found = [box for box in self._boxes.values() if box.is_live]
        found = [box for box in found if box.state is not BoxState.SEALED]
        found.sort(key=lambda box: (box.state is not BoxState.EMPTY, box.number))
        return tuple(found)

    def due_for_check(self, today: date) -> tuple[KeyBox, ...]:
        found = [box for box in self._boxes.values() if box.due_for_check(today, self.policy)]
        found.sort(key=lambda box: (box.last_seen(), box.number))
        return tuple(found)

    def overdue_reseal(self, today: date) -> tuple[KeyBox, ...]:
        found = [box for box in self._boxes.values() if box.overdue_reseal(today, self.policy)]
        found.sort(key=lambda box: (box.opened_on or today, box.number))
        return tuple(found)

    def exposure(self) -> dict[str, int]:
        """How many boxes are standing open per building, most first."""
        counts: dict[str, int] = {}
        for box in self.open_boxes():
            counts[box.building] = counts.get(box.building, 0) + 1
        return dict(sorted(counts.items(), key=lambda pair: (-pair[1], pair[0])))

    def symbols_at_risk(self) -> tuple[KeySymbol, ...]:
        """Symbols sitting in a box that is not sealed, which is loose brass."""
        found = {box.symbol for box in self.open_boxes() if box.holds_a_key}
        return tuple(sorted(found, key=lambda symbol: symbol.render()))

    def __contains__(self, number: object) -> bool:
        return number in self._boxes

    def __iter__(self) -> Iterator[KeyBox]:
        return iter(self._boxes.values())

    def __len__(self) -> int:
        return len(self._boxes)


def boxes_needing_work(register: BoxRegister, today: date) -> tuple[KeyBox, ...]:
    """Everything a walk round should deal with, in the order to deal with it."""
    wanted: list[KeyBox] = []
    for box in register.open_boxes():
        wanted.append(box)
    for box in register.due_for_check(today):
        if box not in wanted:
            wanted.append(box)
    return tuple(wanted)


def coverage(register: BoxRegister, buildings: Sequence[str]) -> tuple[str, ...]:
    """Buildings with no live box on them, which is the fire brigade's problem."""
    held = {box.building for box in register if box.is_live}
    return tuple(sorted(code for code in buildings if code not in held))

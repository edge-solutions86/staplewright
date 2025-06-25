"""Keyed alike sets: doors that take one key because somebody asked nicely.

A department with four store cupboards does not want four keys, so the four
cylinders get the same cuts and the set becomes one thing. That is cheap and
convenient and it has one property people forget: the set is now atomic. Lose
the key to one cupboard and all four are compromised, and rekeying one means
rekeying all four on the same afternoon or the department ends up carrying
two keys anyway, which was the thing they were trying to avoid.

So a set is a first class object rather than a coincidence of bittings, and
the checks are about whether the hardware can actually do it: one keyway
section, one chamber count, and formats that take the same cuts. A mortise
cylinder and a seven pin core cannot be keyed alike however much anybody
wants them to be.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass

from hasplib.campus.hardware import Cylinder, CylinderFormat, HardwareRegister, Position
from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.store import Registry

INTERCHANGEABLE = frozenset({CylinderFormat.MORTISE, CylinderFormat.RIM, CylinderFormat.KIK})
"""Formats that take the same cuts on the same section as each other."""


@dataclass(frozen=True)
class KeyedAlikeSet:
    """A run of openings that carry one key between them."""

    name: str
    openings: tuple[str, ...]
    note: str = ""

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise InputError("a keyed alike set needs a name")
        if len(self.openings) < 2:
            raise ConflictError("a set of one door is just a door", name=self.name)
        if len(set(self.openings)) != len(self.openings):
            raise ConflictError("a door is in the set twice", name=self.name)

    @property
    def size(self) -> int:
        return len(self.openings)

    def holds(self, opening: str) -> bool:
        return opening in self.openings

    def describe(self) -> str:
        return f"{self.name}: {', '.join(sorted(self.openings))}"


def cylinders_in(register: HardwareRegister, alike: KeyedAlikeSet) -> tuple[Cylinder, ...]:
    """The lockset cylinder at every door in the set, in door order."""
    found: list[Cylinder] = []
    for number in sorted(alike.openings):
        cylinder = register.at(number, Position.LOCKSET)
        if cylinder is not None:
            found.append(cylinder)
    return tuple(found)


def problems(register: HardwareRegister, alike: KeyedAlikeSet) -> tuple[str, ...]:
    """Every reason the hardware cannot take one key across this set."""
    found: list[str] = []
    cylinders = cylinders_in(register, alike)
    empty = [
        number for number in sorted(alike.openings) if register.at(number, Position.LOCKSET) is None
    ]
    for number in empty:
        found.append(f"{number} has nothing in the lockset")
    if not cylinders:
        return tuple(found)

    sections = {cylinder.section for cylinder in cylinders}
    if len(sections) > 1:
        found.append(f"the set spans keyway sections {', '.join(sorted(sections))}")
    chambers = {cylinder.chambers for cylinder in cylinders}
    if len(chambers) > 1:
        listed = ", ".join(str(count) for count in sorted(chambers))
        found.append(f"the set spans chamber counts {listed}")
    formats = {cylinder.format for cylinder in cylinders}
    if len(formats) > 1 and not formats <= INTERCHANGEABLE:
        listed = ", ".join(sorted(item.value for item in formats))
        found.append(f"the set mixes formats that do not take one cut: {listed}")
    return tuple(found)


def can_be_keyed_alike(register: HardwareRegister, alike: KeyedAlikeSet) -> bool:
    return not problems(register, alike)


class KeyedAlikeRegister:
    """Every set the shop maintains, and which door is in which."""

    def __init__(self) -> None:
        self._sets: Registry[KeyedAlikeSet] = Registry("keyed alike set", "name")

    def add(self, alike: KeyedAlikeSet) -> KeyedAlikeSet:
        for number in alike.openings:
            held = self.set_holding(number)
            if held is not None:
                raise ConflictError(
                    "that door is already in a keyed alike set",
                    opening=number,
                    held_by=held.name,
                )
        return self._sets.add(alike.name, alike)

    def get(self, name: str) -> KeyedAlikeSet:
        return self._sets.get(name)

    def remove(self, name: str) -> KeyedAlikeSet:
        return self._sets.remove(name)

    def set_holding(self, opening: str) -> KeyedAlikeSet | None:
        return self._sets.first(lambda alike: alike.holds(opening))

    def merge(self, first: str, second: str, name: str) -> KeyedAlikeSet:
        """Two sets become one, which is what happens when a wall comes out."""
        left = self._sets.get(first)
        right = self._sets.get(second)
        joined = KeyedAlikeSet(name, (*left.openings, *right.openings))
        self._sets.remove(first)
        self._sets.remove(second)
        return self._sets.add(name, joined)

    def split(self, name: str, taken: Sequence[str], into: str) -> KeyedAlikeSet:
        """Pull some doors out of a set into their own.

        Both halves have to survive as sets, so a split that would leave one
        door on its own is refused rather than quietly deleting a set.
        """
        alike = self._sets.get(name)
        wanted = set(taken)
        missing = wanted - set(alike.openings)
        if missing:
            raise NotFoundError(
                "those doors are not in that set", name=name, missing=tuple(sorted(missing))
            )
        left = tuple(number for number in alike.openings if number not in wanted)
        if len(left) < 2 or len(wanted) < 2:
            raise ConflictError("a split leaves two sets or it is not a split", name=name)
        self._sets.put(name, KeyedAlikeSet(name, left, alike.note))
        return self._sets.add(into, KeyedAlikeSet(into, tuple(sorted(wanted))))

    def sets(self) -> tuple[KeyedAlikeSet, ...]:
        return self._sets.by_key()

    def openings_alike_with(self, opening: str) -> tuple[str, ...]:
        """The other doors that go up with this one when it is rekeyed."""
        held = self.set_holding(opening)
        if held is None:
            return ()
        return tuple(sorted(number for number in held.openings if number != opening))

    def __contains__(self, name: object) -> bool:
        return name in self._sets

    def __iter__(self) -> Iterator[KeyedAlikeSet]:
        return iter(self.sets())

    def __len__(self) -> int:
        return len(self._sets)

"""Who has to get in where, written down before any key is designed.

This is the document the shop should be given and almost never is. A
department says which of its people have to open which doors, and the answer
is a matrix: access groups down one side, openings along the other.

Two things fall straight out of it. Openings wanted by exactly the same set of
groups can be keyed alike, because no key would ever tell them apart, and that
collapse is where most of the savings in a system come from. And an access
group that reaches into more than one building needs a key higher up the
hierarchy than a building master, which is the conversation nobody wants to
have late in a project.

The matrix knows nothing about keys. It is the requirement, and the schedule
is one answer to it.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from hasplib.campus.openings import OpeningSchedule
from hasplib.errors import ConflictError, InputError, NotFoundError


@dataclass(frozen=True)
class AccessGroup:
    """A set of people who need the same doors."""

    name: str
    description: str = ""
    department: str = ""

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise InputError("an access group needs a name")


class AccessMatrix:
    """Access groups against openings, and nothing else."""

    def __init__(self, openings: OpeningSchedule) -> None:
        self.openings = openings
        self._groups: dict[str, AccessGroup] = {}
        self._wants: dict[str, set[str]] = {}

    def add_group(self, group: AccessGroup) -> AccessGroup:
        if group.name in self._groups:
            raise ConflictError("that access group is already here", group=group.name)
        self._groups[group.name] = group
        self._wants[group.name] = set()
        return group

    def group(self, name: str) -> AccessGroup:
        try:
            return self._groups[name]
        except KeyError:
            raise NotFoundError("no such access group", group=name) from None

    def require(self, group_name: str, opening_number: str) -> None:
        """This group has to open this door."""
        self.group(group_name)
        opening = self.openings.opening(opening_number)
        if not opening.is_keyed:
            raise ConflictError(
                "that opening takes no key, so nobody can be given one",
                opening=opening_number,
                function=str(opening.function),
            )
        self._wants[group_name].add(opening_number)

    def require_all(self, group_name: str, opening_numbers: Iterable[str]) -> None:
        for number in opening_numbers:
            self.require(group_name, number)

    def require_building(self, group_name: str, building: str) -> None:
        """Everything keyed in one building, which is how a master is asked for."""
        for opening in self.openings.in_building(building):
            if opening.is_keyed:
                self.require(group_name, opening.number)

    def drop(self, group_name: str, opening_number: str) -> None:
        self.group(group_name)
        self._wants[group_name].discard(opening_number)

    def openings_for(self, group_name: str) -> tuple[str, ...]:
        self.group(group_name)
        return tuple(sorted(self._wants[group_name]))

    def groups_for(self, opening_number: str) -> tuple[str, ...]:
        self.openings.opening(opening_number)
        return tuple(
            sorted(name for name, wanted in self._wants.items() if opening_number in wanted)
        )

    def wants(self, group_name: str, opening_number: str) -> bool:
        self.group(group_name)
        return opening_number in self._wants[group_name]

    def signature(self, opening_number: str) -> tuple[str, ...]:
        """The set of groups that must open a door, which is its keying."""
        return self.groups_for(opening_number)

    def keyed_alike_sets(self) -> tuple[tuple[str, ...], ...]:
        """Openings no key could tell apart, grouped.

        Doors wanted by nobody are left out. They are a separate problem and
        putting them all in one giant keyed alike set would hide it.
        """
        buckets: dict[tuple[str, ...], list[str]] = {}
        for opening in self.openings.keyed_openings():
            groups = self.signature(opening.number)
            if not groups:
                continue
            buckets.setdefault(groups, []).append(opening.number)
        collapsed = [tuple(sorted(numbers)) for numbers in buckets.values()]
        collapsed.sort(key=lambda numbers: (-len(numbers), numbers))
        return tuple(collapsed)

    def unreachable_openings(self) -> tuple[str, ...]:
        """Keyed doors no access group asked for. Somebody still has to get in."""
        return tuple(
            opening.number
            for opening in self.openings.keyed_openings()
            if not self.signature(opening.number)
        )

    def buildings_reached(self, group_name: str) -> tuple[str, ...]:
        numbers = self.openings_for(group_name)
        return tuple(sorted({self.openings.opening(number).building for number in numbers}))

    def cross_building_groups(self) -> tuple[str, ...]:
        """Groups whose doors span buildings, which need a key above a master."""
        return tuple(sorted(name for name in self._groups if len(self.buildings_reached(name)) > 1))

    def coverage(self) -> dict[str, int]:
        """How many doors each group is asking for, most first when printed."""
        counts = {name: len(wanted) for name, wanted in self._wants.items()}
        return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

    def groups(self) -> tuple[AccessGroup, ...]:
        return tuple(self._groups[name] for name in sorted(self._groups))

    def __contains__(self, name: object) -> bool:
        return name in self._groups

    def __iter__(self) -> Iterator[AccessGroup]:
        return iter(self.groups())

    def __len__(self) -> int:
        return len(self._groups)

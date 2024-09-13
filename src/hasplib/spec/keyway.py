"""Keyway sections, and which of them a key will physically enter.

The warding down the side of a keyway decides what goes in before any cut
depth matters. A key cut on a section high in a multiplex family enters every
section below it, which is how one grand master opens buildings that were
keyed years apart on different sections.

The family is a tree. A section has one parent and any number of children,
the root section enters everything, and a leaf section enters only itself.
That relation is the whole point of this file, and getting it backwards is
the classic way to hand a department a key that opens more than the schedule
says it does.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from hasplib.errors import KeywayError


@dataclass(frozen=True)
class KeywaySection:
    """One warding section, named the way the catalogue names it."""

    code: str
    manufacturer: str
    restricted: bool = False
    patent_expires: str | None = None

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise KeywayError("a section needs a code")
        if self.code != self.code.strip().upper():
            raise KeywayError("section codes are upper case and untrimmed", code=self.code)


class KeywayFamily:
    """A multiplex family, held as the tree the catalogue draws.

    Built by naming the root and then hanging sections off it. A section may
    be added only once and only under a parent that is already there, so the
    tree cannot pick up a cycle or a second root by accident.
    """

    def __init__(self, name: str, root: KeywaySection) -> None:
        if not name.strip():
            raise KeywayError("a family needs a name")
        self.name = name
        self._sections: dict[str, KeywaySection] = {root.code: root}
        self._parents: dict[str, str | None] = {root.code: None}
        self._children: dict[str, list[str]] = {root.code: []}
        self._root = root.code

    @property
    def root(self) -> KeywaySection:
        return self._sections[self._root]

    def add(self, section: KeywaySection, under: str) -> KeywaySection:
        """Hang a section under one that is already in the family."""
        if section.code in self._sections:
            raise KeywayError("that section is already in the family", code=section.code)
        if under not in self._sections:
            raise KeywayError("no such parent section", parent=under, family=self.name)
        if section.manufacturer != self.root.manufacturer:
            raise KeywayError(
                "a family holds one manufacturer",
                family=self.name,
                expected=self.root.manufacturer,
                got=section.manufacturer,
            )
        self._sections[section.code] = section
        self._parents[section.code] = under
        self._children[section.code] = []
        self._children[under].append(section.code)
        return section

    def section(self, code: str) -> KeywaySection:
        try:
            return self._sections[code]
        except KeyError:
            raise KeywayError("no such section", code=code, family=self.name) from None

    def parent_of(self, code: str) -> KeywaySection | None:
        self.section(code)
        parent = self._parents[code]
        return None if parent is None else self._sections[parent]

    def children_of(self, code: str) -> tuple[KeywaySection, ...]:
        self.section(code)
        return tuple(self._sections[child] for child in self._children[code])

    def ancestry(self, code: str) -> tuple[str, ...]:
        """The section itself, then its parent, up to the root."""
        self.section(code)
        line: list[str] = []
        walk: str | None = code
        while walk is not None:
            line.append(walk)
            walk = self._parents[walk]
        return tuple(line)

    def enters(self, cut_on: str, keyway: str) -> bool:
        """Whether a key cut on one section goes into a cylinder of another."""
        self.section(cut_on)
        return cut_on in self.ancestry(keyway)

    def sections_entered(self, cut_on: str) -> tuple[str, ...]:
        """Every section a key cut on this one enters, itself included."""
        self.section(cut_on)
        found: list[str] = []
        pending = [cut_on]
        while pending:
            code = pending.pop(0)
            found.append(code)
            pending.extend(self._children[code])
        return tuple(found)

    def common_section(self, first: str, second: str) -> str:
        """The lowest section a single key could be cut on to enter both."""
        first_line = self.ancestry(first)
        second_line = set(self.ancestry(second))
        for code in first_line:
            if code in second_line:
                return code
        raise KeywayError(
            "those sections are not in one family", first=first, second=second, family=self.name
        )

    def depth_of(self, code: str) -> int:
        """How far below the root a section sits. The root is zero."""
        return len(self.ancestry(code)) - 1

    def leaves(self) -> tuple[str, ...]:
        return tuple(sorted(code for code, kids in self._children.items() if not kids))

    def codes(self) -> tuple[str, ...]:
        return tuple(sorted(self._sections))

    def __contains__(self, code: object) -> bool:
        return code in self._sections

    def __iter__(self) -> Iterator[KeywaySection]:
        return iter(self._sections[code] for code in self.codes())

    def __len__(self) -> int:
        return len(self._sections)


@dataclass(frozen=True)
class Blank:
    """A key blank as it sits in the drawer, with its part number."""

    part_number: str
    section: str
    manufacturer: str
    chambers: int
    stock: int = 0

    def __post_init__(self) -> None:
        if self.chambers < 1:
            raise KeywayError("a blank has at least one chamber", part=self.part_number)
        if self.stock < 0:
            raise KeywayError("the drawer cannot hold less than nothing", part=self.part_number)


def blanks_for(blanks: Iterable[Blank], section: str) -> tuple[Blank, ...]:
    """Every blank cut on one section, deepest stock first."""
    matching = [blank for blank in blanks if blank.section == section]
    matching.sort(key=lambda blank: (-blank.stock, blank.part_number))
    return tuple(matching)


def restricted_sections(family: KeywayFamily) -> tuple[str, ...]:
    return tuple(section.code for section in family if section.restricted)

"""Key symbols, which are how the shop names a key without describing it.

The notation is old and it is compact. GGM is the one key at the top. A single
letter is a grand master, two letters a master, three a sub master, and a
letter group followed by a number is a change key, the key an actual person
carries. So AB3 is the third change key under master AB, which itself sits
under grand master A, which sits under GGM.

Depth in the notation is depth in the hierarchy, and that is the only reason
the notation survives. Reading a symbol tells you what a key ought to open
before anybody looks up a single cylinder.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from hasplib.errors import HierarchyError

TOP_SYMBOL = "GGM"
"""The one key at the top of a system. Written this way on every chart."""

_PATTERN = re.compile(r"^([A-Z]{1,4})([0-9]{1,4})?$")

_LEVEL_NAMES = {
    0: "great grand master",
    1: "grand master",
    2: "master",
    3: "sub master",
    4: "sub sub master",
}


@dataclass(frozen=True, order=True)
class KeySymbol:
    """One name out of the keying schedule, held so it can be walked.

    ``letters`` is the empty string for the top key and gets one character
    longer at each master level below it. ``number`` is set only on a change
    key, which is always a leaf.
    """

    letters: str
    number: int | None = None

    def __post_init__(self) -> None:
        if self.letters and not self.letters.isalpha():
            raise HierarchyError("a symbol group is letters", letters=self.letters)
        if self.letters != self.letters.upper():
            raise HierarchyError("symbols are written in capitals", letters=self.letters)
        if self.number is not None:
            if self.number < 1:
                raise HierarchyError("change keys are numbered from one", number=self.number)
            if not self.letters:
                raise HierarchyError("a numbered key hangs under a master", number=self.number)

    @classmethod
    def parse(cls, text: str) -> KeySymbol:
        cleaned = text.strip().upper()
        if not cleaned:
            raise HierarchyError("a symbol cannot be blank")
        if cleaned == TOP_SYMBOL:
            return cls("", None)
        match = _PATTERN.match(cleaned)
        if match is None:
            raise HierarchyError("that is not a key symbol", symbol=text)
        letters, digits = match.groups()
        return cls(letters, int(digits) if digits else None)

    @property
    def is_top(self) -> bool:
        return not self.letters and self.number is None

    @property
    def is_change_key(self) -> bool:
        return self.number is not None

    @property
    def level(self) -> int:
        """Distance below the top key. The top key is zero."""
        return len(self.letters) + (1 if self.number is not None else 0)

    @property
    def level_name(self) -> str:
        if self.is_change_key:
            return "change key"
        return _LEVEL_NAMES.get(self.level, f"level {self.level} master")

    def parent(self) -> KeySymbol | None:
        """The key directly above this one, or None at the top."""
        if self.is_top:
            return None
        if self.number is not None:
            return KeySymbol(self.letters, None)
        return KeySymbol(self.letters[:-1], None)

    def ancestry(self) -> tuple[KeySymbol, ...]:
        """This symbol, then everything above it, ending at the top key."""
        line: list[KeySymbol] = []
        walk: KeySymbol | None = self
        while walk is not None:
            line.append(walk)
            walk = walk.parent()
        return tuple(line)

    def descends_from(self, other: KeySymbol) -> bool:
        """Whether this key sits somewhere under the other one."""
        return other in self.ancestry()

    def child_master(self, letter: str) -> KeySymbol:
        """The master one level down, named by adding a letter."""
        if self.is_change_key:
            raise HierarchyError("a change key has nothing under it", symbol=self.render())
        if len(letter) != 1 or not letter.isalpha():
            raise HierarchyError("a master is named by one letter", letter=letter)
        return KeySymbol(self.letters + letter.upper(), None)

    def change_key(self, number: int) -> KeySymbol:
        """A numbered key directly under this master."""
        if self.is_change_key:
            raise HierarchyError("a change key has nothing under it", symbol=self.render())
        if self.is_top:
            raise HierarchyError("change keys do not hang off the top key")
        return KeySymbol(self.letters, number)

    def render(self) -> str:
        if self.is_top:
            return TOP_SYMBOL
        if self.number is None:
            return self.letters
        return f"{self.letters}{self.number}"

    def __str__(self) -> str:
        return self.render()


def sort_symbols(symbols: list[KeySymbol]) -> list[KeySymbol]:
    """Schedule order: shallow before deep, then letters, then number."""
    return sorted(symbols, key=lambda s: (s.level, s.letters, s.number or 0))


def common_ancestor(first: KeySymbol, second: KeySymbol) -> KeySymbol:
    """The lowest key that sits above both. Always exists, at worst the top."""
    above = set(second.ancestry())
    for symbol in first.ancestry():
        if symbol in above:
            return symbol
    raise HierarchyError(
        "two symbols with no key above them", first=first.render(), second=second.render()
    )

"""A bitting is the depth of each cut on a key, in order from the bow.

The shop writes one down as a run of digits, so 342156 is a six chamber key
whose first cut is a three. That works until a chart goes past nine depths,
and two of the three manufacturers on campus do, so the parser also reads a
dotted form: 10.4.12.3 is four cuts, the first ten deep.

Nothing here knows whether a bitting is legal. Legality belongs to the chart
it is cut against, and the chart arrives two files later. A Bitting is only
the number, held so it cannot be edited by accident and so two of them can be
compared without arguing about whitespace.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator, Sequence

from hasplib.errors import InputError

WILDCARD = "x"
"""Stands for any depth in a pattern. The shop writes it lower case."""


class Bitting:
    """An ordered run of cut depths, counted from the bow toward the tip.

    Chambers are numbered from one because that is how the pinning chart on
    the bench is numbered, and an off by one between the chart and the screen
    costs a cylinder.
    """

    __slots__ = ("_depths",)

    def __init__(self, depths: Iterable[int]) -> None:
        cuts = tuple(depths)
        if not cuts:
            raise InputError("a bitting needs at least one cut")
        for position, depth in enumerate(cuts, start=1):
            if isinstance(depth, bool) or not isinstance(depth, int):
                raise InputError("a cut depth is a whole number", chamber=position, got=depth)
            if depth < 0:
                raise InputError("a cut depth cannot be negative", chamber=position, depth=depth)
        self._depths = cuts

    @classmethod
    def parse(cls, text: str) -> Bitting:
        """Read either the packed digit form or the dotted form."""
        if not isinstance(text, str):
            raise InputError("a bitting is written as text", got=type(text).__name__)
        cleaned = text.strip().replace(" ", "").replace("-", ".")
        if not cleaned:
            raise InputError("a bitting needs at least one cut")
        if "." in cleaned:
            parts = cleaned.split(".")
            if any(not part for part in parts):
                raise InputError("the dotted form has an empty cut in it", text=text)
            if any(not part.isdigit() for part in parts):
                raise InputError("a cut depth is a whole number", text=text)
            return cls(int(part) for part in parts)
        if not cleaned.isdigit():
            raise InputError("the packed form takes digits only", text=text)
        return cls(int(char) for char in cleaned)

    @property
    def depths(self) -> tuple[int, ...]:
        return self._depths

    @property
    def chambers(self) -> int:
        return len(self._depths)

    def at(self, chamber: int) -> int:
        """The depth in one chamber, counted from one."""
        if chamber < 1 or chamber > len(self._depths):
            raise InputError(
                "no such chamber on this key", chamber=chamber, chambers=len(self._depths)
            )
        return self._depths[chamber - 1]

    def with_depth(self, chamber: int, depth: int) -> Bitting:
        """The same key with one cut changed. Returns a new bitting."""
        self.at(chamber)
        cuts = list(self._depths)
        cuts[chamber - 1] = depth
        return Bitting(cuts)

    def differs_from(self, other: Bitting) -> tuple[int, ...]:
        """Which chambers the two keys disagree in, counted from one."""
        if self.chambers != other.chambers:
            raise InputError(
                "two keys of different lengths cannot be compared cut for cut",
                left=self.chambers,
                right=other.chambers,
            )
        return tuple(
            position
            for position, (mine, theirs) in enumerate(
                zip(self._depths, other.depths, strict=True), start=1
            )
            if mine != theirs
        )

    def adjacent_differences(self) -> tuple[int, ...]:
        """The absolute step between each neighbouring pair of cuts."""
        pairs = zip(self._depths, self._depths[1:], strict=False)
        return tuple(abs(second - first) for first, second in pairs)

    def deepest(self) -> int:
        return max(self._depths)

    def shallowest(self) -> int:
        return min(self._depths)

    def render(self, dotted: bool = False) -> str:
        """Write it back out the way the shop writes it."""
        if dotted or any(depth > 9 for depth in self._depths):
            return ".".join(str(depth) for depth in self._depths)
        return "".join(str(depth) for depth in self._depths)

    def __iter__(self) -> Iterator[int]:
        return iter(self._depths)

    def __len__(self) -> int:
        return len(self._depths)

    def __getitem__(self, index: int) -> int:
        return self._depths[index]

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Bitting):
            return NotImplemented
        return self._depths == other.depths

    def __lt__(self, other: Bitting) -> bool:
        if not isinstance(other, Bitting):
            return NotImplemented
        return self._depths < other.depths

    def __hash__(self) -> int:
        return hash(self._depths)

    def __repr__(self) -> str:
        return f"Bitting({self.render()!r})"


def parse_pattern(text: str, chambers: int) -> tuple[int | None, ...]:
    """Read a bitting with holes in it, the way a keying schedule writes one.

    A master cut that has not been chosen yet is written as an x. The result
    has one entry per chamber and None where the x was.
    """
    cleaned = text.strip().replace(" ", "").replace("-", ".")
    parts: Sequence[str]
    parts = cleaned.split(".") if "." in cleaned else list(cleaned)
    if len(parts) != chambers:
        raise InputError(
            "the pattern is not as long as the system is",
            pattern=text,
            chambers=chambers,
            got=len(parts),
        )
    out: list[int | None] = []
    for position, part in enumerate(parts, start=1):
        if part.lower() == WILDCARD:
            out.append(None)
        elif part.isdigit():
            out.append(int(part))
        else:
            raise InputError("a pattern takes depths or an x", chamber=position, got=part)
    return tuple(out)


def matches_pattern(bitting: Bitting, pattern: Sequence[int | None]) -> bool:
    """Whether a key fits a pattern, ignoring the chambers left open."""
    if bitting.chambers != len(pattern):
        return False
    paired = zip(bitting.depths, pattern, strict=True)
    return all(wanted is None or wanted == depth for depth, wanted in paired)

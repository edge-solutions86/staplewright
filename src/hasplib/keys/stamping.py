"""What gets stamped on a key, which is a security decision disguised as a chore.

A key bow has room for about eight characters and every one of them is a
choice. Stamp the symbol and anybody who finds the key knows it is a master
and roughly where it fits. Stamp nothing and the shop cannot identify a key
somebody hands in, which happens more often than losing one.

The answer the trade settled on is a blind code: a number that means nothing
to anybody without the shop's book, and everything with it. So a key carries
its tag and a blind code, the symbol stays on paper, and the one place they
are joined up is a table in this building.

The blind code is not a hash and is not meant to be. It is a short number
from a series, and its security is entirely that the lookup table is not
public. Anything cleverer would be a cipher somebody eventually recovers the
key for, and would still be a number on a piece of brass.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from enum import StrEnum

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.store import Registry

BOW_CHARACTERS = 8
DO_NOT_DUPLICATE = "DND"


class Marking(StrEnum):
    """What a shop puts on the brass."""

    SYMBOL = "the key symbol"
    BLIND = "a blind code"
    TAG = "the tag number"
    NOTHING = "nothing at all"


@dataclass(frozen=True)
class StampPolicy:
    """What this shop stamps, and on what.

    ``masters_go_blind`` is the rule worth having. A change key with its
    symbol on it tells a finder which office it opens, which is bad enough. A
    grand master with its symbol on it tells them what they are holding.
    """

    change_keys: Marking = Marking.SYMBOL
    masters: Marking = Marking.BLIND
    add_do_not_duplicate: bool = True
    bow_characters: int = BOW_CHARACTERS

    def __post_init__(self) -> None:
        if self.bow_characters < 4:
            raise InputError("nothing useful fits on a bow that small")

    def marking_for(self, symbol: KeySymbol) -> Marking:
        return self.change_keys if symbol.is_change_key else self.masters


@dataclass(frozen=True)
class Stamp:
    """What actually gets cut into one key."""

    tag: str
    symbol: str
    text: str
    marking: Marking

    def __post_init__(self) -> None:
        if not self.tag.strip():
            raise InputError("a stamp goes on a tagged key")

    @property
    def is_blind(self) -> bool:
        return self.marking is Marking.BLIND

    def describe(self) -> str:
        return f"{self.tag} {self.text or 'blank'} ({self.marking.value})"


def _fits(text: str, policy: StampPolicy) -> str:
    if len(text) > policy.bow_characters:
        raise ConflictError(
            "that does not fit on the bow",
            text=text,
            room=policy.bow_characters,
        )
    return text


class BlindCodeBook:
    """The table that joins a number on a key to a symbol on paper.

    This is the whole security model, so it is one object with one job and no
    convenience methods that hand back the mapping in bulk. Anything that
    wants the symbol behind a code asks for exactly one, which is the sort of
    thing a log can be read against later.
    """

    def __init__(self, start: int = 4000, step: int = 7) -> None:
        if start < 1:
            raise InputError("a blind series starts at one or later")
        if step < 1:
            raise InputError("a blind series steps forward")
        self.start = start
        self.step = step
        self._next = start
        self._by_symbol: Registry[str] = Registry("symbol", "symbol")
        self._by_code: dict[str, str] = {}

    def assign(self, symbol: KeySymbol) -> str:
        """Give a symbol a code, or hand back the one it already has."""
        rendered = symbol.render()
        held = self._by_symbol.find(rendered)
        if held is not None:
            return held
        code = str(self._next)
        self._next += self.step
        self._by_symbol.add(rendered, code)
        self._by_code[code] = rendered
        return code

    def code_for(self, symbol: KeySymbol) -> str:
        found = self._by_symbol.find(symbol.render())
        if found is None:
            raise NotFoundError("that symbol has no blind code", symbol=symbol.render())
        return found

    def symbol_for(self, code: str) -> str:
        """What a number on a key means, which is the reason this exists."""
        found = self._by_code.get(code.strip())
        if found is None:
            raise NotFoundError("no key was ever stamped with that", code=code)
        return found

    def load(self, symbol: str, code: str) -> None:
        """Put a pair back as it was stored, refusing to reuse a code."""
        if code in self._by_code and self._by_code[code] != symbol:
            raise ConflictError("that code is already against another symbol", code=code)
        self._by_symbol.put(symbol, code)
        self._by_code[code] = symbol
        following = int(code) + self.step
        self._next = max(self._next, following)

    def codes(self) -> tuple[tuple[str, str], ...]:
        """Every pair, symbol first. The one bulk method, for the safe."""
        return tuple(sorted(self._by_code.items(), key=lambda pair: pair[1]))

    def __contains__(self, code: object) -> bool:
        return code in self._by_code

    def __iter__(self) -> Iterator[str]:
        return iter(sorted(self._by_code))

    def __len__(self) -> int:
        return len(self._by_code)


def stamp_for(
    tag: str, symbol: KeySymbol, policy: StampPolicy, book: BlindCodeBook | None = None
) -> Stamp:
    """Work out what goes on one key, refusing anything that will not fit."""
    marking = policy.marking_for(symbol)
    if marking is Marking.NOTHING:
        text = ""
    elif marking is Marking.TAG:
        text = _fits(tag, policy)
    elif marking is Marking.BLIND:
        if book is None:
            raise ConflictError(
                "a blind stamp needs the code book", tag=tag, symbol=symbol.render()
            )
        text = _fits(book.assign(symbol), policy)
    else:
        text = _fits(symbol.render(), policy)
    return Stamp(tag, symbol.render(), text, marking)


def stamp_run(
    wanted: Sequence[tuple[str, KeySymbol]],
    policy: StampPolicy,
    book: BlindCodeBook | None = None,
) -> tuple[Stamp, ...]:
    """A batch for the stamping machine, in tag order."""
    found = [stamp_for(tag, symbol, policy, book) for tag, symbol in wanted]
    found.sort(key=lambda stamp: stamp.tag)
    return tuple(found)


def stamp_sheet(found: Sequence[Stamp], policy: StampPolicy) -> tuple[str, ...]:
    """The sheet that goes to the bench with the blanks."""
    if not found:
        return ("nothing to stamp",)
    lines = [f"{len(found)} keys to stamp"]
    for stamp in found:
        trailer = f" and {DO_NOT_DUPLICATE}" if policy.add_do_not_duplicate else ""
        lines.append(f"  {stamp.tag:<10}{stamp.text or '(blank)':<10}{trailer}")
    blind = [stamp for stamp in found if stamp.is_blind]
    if blind:
        lines.append(f"{len(blind)} of them are blind, the symbols stay on paper")
    return tuple(lines)


def identify(code: str, book: BlindCodeBook) -> str:
    """Somebody handed in a key with a number on it. This is the answer."""
    return book.symbol_for(code)


def unstamped(found: Sequence[Stamp]) -> tuple[str, ...]:
    """Keys that carry nothing, which the shop cannot identify if handed one."""
    return tuple(sorted(stamp.tag for stamp in found if not stamp.text))

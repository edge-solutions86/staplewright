"""Duplicate control: how many of a key exist, and who said so.

Stamping a key DO NOT DUPLICATE achieves nothing on its own. Any hardware
store will cut it, the stamp is not a legal instruction, and the shop has a
drawer full of copies to prove it. What works is the restricted keyway, where
the blank cannot be bought, and the count, where the shop knows five keys
were cut to AA1 and finds six.

So this is arithmetic rather than policy. Every symbol has a ceiling, either
the standing one or a specific one somebody signed for, and the check is a
count of what is out against that ceiling. A symbol over its ceiling is not
proof of anything. It is a question worth asking, which is more than a stamp
ever produced.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.control.issue import KeyLedger
from hasplib.errors import ConflictError, InputError
from hasplib.keys.symbol import KeySymbol


@dataclass(frozen=True)
class DuplicationPolicy:
    """How many of each kind of key the shop will have out at once."""

    change_key_limit: int = 4
    master_limit: int = 2
    top_key_limit: int = 1
    restricted_only_for_masters: bool = True

    def __post_init__(self) -> None:
        limits = (self.change_key_limit, self.master_limit, self.top_key_limit)
        if any(limit < 1 for limit in limits):
            raise InputError("a limit of nothing means no keys at all")

    def ceiling_for(self, symbol: KeySymbol) -> int:
        if symbol.is_top:
            return self.top_key_limit
        if symbol.is_change_key:
            return self.change_key_limit
        return self.master_limit


DEFAULT_POLICY = DuplicationPolicy()


@dataclass(frozen=True)
class Authorisation:
    """Somebody signed for more copies of one symbol than policy allows."""

    symbol: str
    ceiling: int
    signed_by: str
    signed_on: date
    reason: str

    def __post_init__(self) -> None:
        if self.ceiling < 1:
            raise InputError("an authorisation raises a ceiling", symbol=self.symbol)
        if not self.reason.strip():
            raise InputError("an authorisation says why", symbol=self.symbol)


@dataclass(frozen=True)
class Overrun:
    """A symbol with more keys out than it is allowed."""

    symbol: str
    out: int
    ceiling: int
    authorised: bool

    @property
    def over_by(self) -> int:
        return self.out - self.ceiling

    def describe(self) -> str:
        signed = "authorised" if self.authorised else "policy"
        return f"{self.symbol}: {self.out} out against {signed} ceiling {self.ceiling}"


class DuplicationRegister:
    """Ceilings, the ones somebody raised, and what is over them."""

    def __init__(self, policy: DuplicationPolicy = DEFAULT_POLICY) -> None:
        self.policy = policy
        self._authorised: dict[str, Authorisation] = {}

    def authorise(self, item: Authorisation) -> Authorisation:
        """Raise the ceiling on one symbol. It cannot be lowered here."""
        held = self._authorised.get(item.symbol)
        if held is not None and item.ceiling <= held.ceiling:
            raise ConflictError(
                "that symbol already has a ceiling at least that high",
                symbol=item.symbol,
                ceiling=held.ceiling,
            )
        self._authorised[item.symbol] = item
        return item

    def authorisation(self, symbol: str) -> Authorisation | None:
        return self._authorised.get(symbol)

    def ceiling_for(self, symbol: KeySymbol) -> int:
        held = self._authorised.get(symbol.render())
        if held is not None:
            return held.ceiling
        return self.policy.ceiling_for(symbol)

    def is_authorised(self, symbol: KeySymbol) -> bool:
        return symbol.render() in self._authorised

    def overruns(self, ledger: KeyLedger) -> tuple[Overrun, ...]:
        """Every symbol with more keys out than it is allowed, worst first."""
        counts: dict[str, int] = {}
        for key in ledger.out():
            counts[key.symbol] = counts.get(key.symbol, 0) + 1
        found: list[Overrun] = []
        for rendered, count in counts.items():
            symbol = KeySymbol.parse(rendered)
            ceiling = self.ceiling_for(symbol)
            if count > ceiling:
                found.append(Overrun(rendered, count, ceiling, self.is_authorised(symbol)))
        found.sort(key=lambda item: (-item.over_by, item.symbol))
        return tuple(found)

    def room_for(self, symbol: KeySymbol, ledger: KeyLedger) -> int:
        """How many more of this key can go out before the ceiling."""
        out = len(ledger.cut_to(symbol.render()))
        return max(self.ceiling_for(symbol) - out, 0)

    def would_exceed(self, symbol: KeySymbol, ledger: KeyLedger) -> bool:
        return self.room_for(symbol, ledger) == 0

    def authorisations(self) -> tuple[Authorisation, ...]:
        return tuple(self._authorised[key] for key in sorted(self._authorised))


def restricted_needed(policy: DuplicationPolicy, symbol: KeySymbol) -> bool:
    """Whether this key has to be cut on a restricted blank."""
    if not policy.restricted_only_for_masters:
        return True
    return not symbol.is_change_key


def unstamped(symbols: Sequence[KeySymbol], policy: DuplicationPolicy) -> tuple[str, ...]:
    """Symbols that ought to be on a restricted blank, for a stock check."""
    return tuple(symbol.render() for symbol in symbols if restricted_needed(policy, symbol))

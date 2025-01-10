"""Deciding how much to rekey, which is where the money goes.

The cheap answer is to repin the one cylinder and cut one new key. The
expensive answer is that the missing key was a master, so every cylinder that
master turns has to come apart, and there are ninety of them in four
buildings and each one is twenty minutes.

There is a third answer that catches people out. Some shops hold that a
repinned cylinder retires every cut in it, not just the compromised one,
because the array position is now known to somebody. Turn that policy on and
the work does not stop at one pass: recutting the other change keys drags in
every other cylinder those keys operate, and that drags in their masters, and
the set grows until it stops growing. The fixpoint is usually small and
occasionally it is the whole system, which is exactly the thing worth knowing
before the crew is dispatched.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from enum import IntEnum

from hasplib.campus.coverage import CoverageMap
from hasplib.keys.symbol import KeySymbol
from hasplib.rekey.compromise import Assessment, Cause


class Scope(IntEnum):
    """How far the work reaches. Ordered from cheapest."""

    NOTHING = 0
    OPENING = 1
    GROUP = 2
    BRANCH = 3
    SYSTEM = 4

    @property
    def label(self) -> str:
        return self.name.lower()


@dataclass(frozen=True)
class ScopePolicy:
    """What the shop has decided to do about missing keys."""

    recut_all_on_repin: bool = False
    always_rekey_exterior: bool = True
    always_rekey_masters: bool = True
    ignore_below_openings: int = 0
    cylinder_cap: int | None = None

    def __post_init__(self) -> None:
        if self.ignore_below_openings < 0:
            raise ValueError("a threshold is a count of openings")
        if self.cylinder_cap is not None and self.cylinder_cap < 1:
            raise ValueError("a cap of nothing rekeys nothing")


DEFAULT_POLICY = ScopePolicy()


def closure(
    coverage: CoverageMap,
    symbols: Iterable[KeySymbol],
    recut_all_on_repin: bool = False,
) -> tuple[tuple[KeySymbol, ...], tuple[str, ...]]:
    """Every symbol that has to be recut and every cylinder that comes apart.

    With the strict policy off this is one pass. With it on it is a fixpoint,
    and the loop is written out rather than made clever because somebody will
    read it while deciding whether to spend a week.
    """
    wanted = set(symbols)
    serials: set[str] = set()
    while True:
        before = (len(wanted), len(serials))
        for symbol in list(wanted):
            for cylinder in coverage.cylinders_for(symbol):
                if cylinder.is_installed:
                    serials.add(cylinder.serial)
        if recut_all_on_repin:
            for serial in list(serials):
                cylinder = coverage.register.cylinder(serial)
                group = coverage.group_of(cylinder)
                if group is not None:
                    wanted.update(group.symbols)
        if (len(wanted), len(serials)) == before:
            break
    ordered = sorted(wanted, key=lambda symbol: (symbol.level, symbol.render()))
    return tuple(ordered), tuple(sorted(serials))


@dataclass(frozen=True)
class ScopeDecision:
    """What the shop is going to do, and why."""

    scope: Scope
    symbols: tuple[KeySymbol, ...]
    cylinders: tuple[str, ...]
    reason: str
    over_cap: bool = False

    @property
    def cylinder_count(self) -> int:
        return len(self.cylinders)

    @property
    def does_nothing(self) -> bool:
        return self.scope is Scope.NOTHING

    def summary(self) -> str:
        return (
            f"{self.scope.label}: {self.cylinder_count} cylinders, "
            f"{len(self.symbols)} symbols recut, {self.reason}"
        )


def _scope_for(coverage: CoverageMap, serials: tuple[str, ...]) -> Scope:
    """Name the reach of a set of cylinders the way a work order names it."""
    if not serials:
        return Scope.NOTHING
    openings = {coverage.register.cylinder(serial).installed_at for serial in serials}
    buildings = {
        coverage.register.openings.opening(number).building for number in openings if number
    }
    if len(buildings) > 1:
        return Scope.SYSTEM
    if len(openings) == 1:
        return Scope.OPENING
    total = len(coverage.register.in_building(sorted(buildings)[0]))
    return Scope.BRANCH if len(serials) == total else Scope.GROUP


def decide(
    assessment: Assessment,
    coverage: CoverageMap,
    policy: ScopePolicy = DEFAULT_POLICY,
) -> ScopeDecision:
    """Read an assessment and say what work it justifies."""
    symbol = assessment.incident.key_symbol
    forced = ""
    if policy.always_rekey_masters and not symbol.is_change_key:
        forced = "a master is missing"
    elif policy.always_rekey_exterior and assessment.touches_outside:
        forced = "an exterior door is exposed"
    elif assessment.incident.cause is Cause.STOLEN:
        forced = "the key was taken rather than lost"

    if not forced and assessment.opening_count <= policy.ignore_below_openings:
        return ScopeDecision(
            scope=Scope.NOTHING,
            symbols=(),
            cylinders=(),
            reason="under the threshold the shop acts on",
        )

    symbols, serials = closure(coverage, [symbol], policy.recut_all_on_repin)
    if not serials:
        return ScopeDecision(Scope.NOTHING, (), (), "that key turns nothing on record")
    over = policy.cylinder_cap is not None and len(serials) > policy.cylinder_cap
    reason = forced or "the key is out and the cylinders are known"
    if over:
        reason = f"{reason}, over the cap of {policy.cylinder_cap}"
    return ScopeDecision(
        scope=_scope_for(coverage, serials),
        symbols=symbols,
        cylinders=serials,
        reason=reason,
        over_cap=over,
    )

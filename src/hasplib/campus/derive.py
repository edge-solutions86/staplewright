"""Turning an access matrix into a hierarchy somebody could actually key.

There is no correct answer here and anybody who says otherwise has not read
a real requirement. Set cover is the underlying problem and the input is a
department's opinion about who should have what, which changes twice before
the doors are hung. So this produces a defensible first draft and says out
loud where it had to guess.

The draft is built in one pass:

- openings no key could tell apart collapse into one change key
- each building takes a grand master
- an access group that stays inside one building and covers two or more of
  those change keys becomes a master under that grand master
- an access group that reaches across buildings gets nothing but the top key,
  and is reported, because that is a decision and not a detail

What comes back is a proposal, not a schedule. Nothing is cut, no bittings
exist, and the useful part is the list of places where the hierarchy grants
more than the matrix asked for.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from string import ascii_uppercase

from hasplib.campus.access import AccessMatrix
from hasplib.errors import CapacityError
from hasplib.keys.symbol import KeySymbol


@dataclass(frozen=True)
class Grant:
    """One symbol and every opening a key cut to it would turn."""

    symbol: KeySymbol
    openings: tuple[str, ...]
    reason: str = ""

    @property
    def size(self) -> int:
        return len(self.openings)


@dataclass(frozen=True)
class Proposal:
    """A first draft hierarchy against a matrix."""

    grants: tuple[Grant, ...]
    notes: tuple[str, ...] = field(default_factory=tuple)

    def symbols(self) -> tuple[KeySymbol, ...]:
        return tuple(grant.symbol for grant in self.grants)

    def grant_for(self, symbol: KeySymbol) -> Grant | None:
        for grant in self.grants:
            if grant.symbol == symbol:
                return grant
        return None

    def opens(self, symbol: KeySymbol) -> tuple[str, ...]:
        grant = self.grant_for(symbol)
        return grant.openings if grant else ()

    def change_keys(self) -> tuple[Grant, ...]:
        return tuple(grant for grant in self.grants if grant.symbol.is_change_key)

    def masters(self) -> tuple[Grant, ...]:
        return tuple(grant for grant in self.grants if not grant.symbol.is_change_key)


def _letters(count: int) -> list[str]:
    if count > len(ascii_uppercase):
        raise CapacityError("more branches than there are letters to name them", wanted=count)
    return list(ascii_uppercase[:count])


def derive(matrix: AccessMatrix) -> Proposal:
    """Draw a hierarchy that satisfies the matrix, and say what it guessed."""
    alike = matrix.keyed_alike_sets()
    notes: list[str] = []
    by_building: dict[str, list[tuple[str, ...]]] = {}
    for openings in alike:
        buildings = {matrix.openings.opening(number).building for number in openings}
        if len(buildings) > 1:
            notes.append(
                f"{'/'.join(sorted(openings))} sit in more than one building and were "
                "left keyed alike anyway"
            )
        by_building.setdefault(sorted(buildings)[0], []).append(openings)

    grants: list[Grant] = []
    building_letters = dict(zip(sorted(by_building), _letters(len(by_building)), strict=True))
    cross = set(matrix.cross_building_groups())

    for building, letter in building_letters.items():
        sets = by_building[building]
        grand = KeySymbol(letter)
        covered = tuple(sorted(number for openings in sets for number in openings))
        grants.append(Grant(grand, covered, f"grand master for {building}"))

        local = [
            name
            for name in matrix.coverage()
            if name not in cross and matrix.buildings_reached(name) == (building,)
        ]
        masters: list[tuple[str, KeySymbol, set[str]]] = []
        for name in local:
            wanted = set(matrix.openings_for(name))
            reach = [openings for openings in sets if set(openings) <= wanted]
            if len(reach) < 2:
                continue
            symbol = grand.child_master(ascii_uppercase[len(masters)])
            masters.append((name, symbol, wanted))
            grants.append(
                Grant(
                    symbol,
                    tuple(sorted(number for openings in reach for number in openings)),
                    f"master for {name}",
                )
            )

        numbered: dict[str, int] = {}
        for openings in sets:
            parent = grand
            for _, symbol, wanted in masters:
                if set(openings) <= wanted:
                    parent = symbol
                    break
            key = parent.render()
            numbered[key] = numbered.get(key, 0) + 1
            grants.append(Grant(parent.change_key(numbered[key]), openings, "change key"))

    for name in sorted(cross):
        notes.append(
            f"{name} reaches {len(matrix.buildings_reached(name))} buildings and needs the top key"
        )
    for number in matrix.unreachable_openings():
        notes.append(f"{number} is keyed and no access group asked for it")

    grants.sort(
        key=lambda grant: (grant.symbol.level, grant.symbol.letters, grant.symbol.number or 0)
    )
    return Proposal(tuple(grants), tuple(notes))


@dataclass(frozen=True)
class OverGrant:
    """A group whose nearest key opens more than it asked for."""

    group: str
    symbol: str
    extra: tuple[str, ...]


def over_grants(matrix: AccessMatrix, proposal: Proposal) -> tuple[OverGrant, ...]:
    """Where a proposal hands somebody doors the matrix never mentioned.

    For each group, the smallest single symbol that covers everything it
    asked for is the key it would be issued. Anything else that key opens is
    an over grant, and the shop would rather argue about it now.

    A freshly derived draft is clean by construction, so the interesting
    input here is an older proposal, or one a person edited. Requirements get
    withdrawn far more often than keys get taken back, and this is the check
    that finds the gap between the two.
    """
    found: list[OverGrant] = []
    for group in matrix.groups():
        wanted = set(matrix.openings_for(group.name))
        if not wanted:
            continue
        candidates = [grant for grant in proposal.grants if wanted <= set(grant.openings)]
        if not candidates:
            continue
        best = min(candidates, key=lambda grant: (grant.size, grant.symbol.render()))
        extra = tuple(sorted(set(best.openings) - wanted))
        if extra:
            found.append(OverGrant(group.name, best.symbol.render(), extra))
    found.sort(key=lambda item: (-len(item.extra), item.group))
    return tuple(found)


def unsatisfied(matrix: AccessMatrix, proposal: Proposal) -> tuple[str, ...]:
    """Groups no single key in the draft covers."""
    found: list[str] = []
    for group in matrix.groups():
        wanted = set(matrix.openings_for(group.name))
        if not wanted:
            continue
        if not any(wanted <= set(grant.openings) for grant in proposal.grants):
            found.append(group.name)
    return tuple(sorted(found))

"""The annual key control return, which is the document the shop is judged on.

Once a year somebody in risk management asks four questions and the answers
go in a file nobody reads until something happens. How many keys are out.
How many of those the shop cannot account for. How many masters exist and who
has them. And what changed since last year.

The last one is the only interesting one. A shop with two hundred keys out is
neither good nor bad on its own, but a shop that had a hundred and eighty out
last year and two hundred and forty this year has stopped saying no to
somebody, and that is a finding.

The masters section is the one that gets read. Every key above change key
level is listed by name with who holds it, because the whole argument for a
master key system is that a small number of people can open a lot of doors,
and the only way that stays true is somebody looking at the list.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from hasplib.campus.coverage import CoverageMap
from hasplib.control.issue import IssuedKey, KeyLedger
from hasplib.control.people import Directory
from hasplib.errors import InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.reports.page import columns

NAME_WIDTH = 18
SYMBOL_WIDTH = 8


@dataclass(frozen=True)
class Return:
    """The four numbers, for one year."""

    year: int
    keys_out: int
    masters_out: int
    unaccounted: int
    keyholders: int

    def __post_init__(self) -> None:
        if self.year < 1900:
            raise InputError("a return is for a year somebody was alive", year=self.year)

    @property
    def keys_per_holder(self) -> float:
        if not self.keyholders:
            return 0.0
        return self.keys_out / self.keyholders

    def describe(self) -> str:
        return (
            f"{self.year}: {self.keys_out} keys out, {self.masters_out} masters, "
            f"{self.unaccounted} unaccounted"
        )


def is_master(symbol: str) -> bool:
    """Whether a symbol is above change key level, which is the whole cut."""
    return not KeySymbol.parse(symbol).is_change_key


def masters_out(ledger: KeyLedger) -> tuple[IssuedKey, ...]:
    """Every master key that is out, deepest symbol first then by tag."""
    found = [key for key in ledger.out() if is_master(key.symbol)]
    found.sort(key=lambda key: (KeySymbol.parse(key.symbol).level, key.tag))
    return tuple(found)


def unaccounted_keys(ledger: KeyLedger, on: date) -> tuple[IssuedKey, ...]:
    """Keys nobody can produce: lost, or out past the day they were due."""
    found = [key for key in ledger if key.state.value == "lost"]
    found.extend(key for key in ledger.overdue(on) if key.state.value != "lost")
    found.sort(key=lambda key: key.tag)
    return tuple(found)


def take(ledger: KeyLedger, directory: Directory, on: date) -> Return:
    """Measure the year, which is four counts off two objects."""
    out = ledger.out()
    holders = {key.keyholder for key in out}
    return Return(
        year=on.year,
        keys_out=len(out),
        masters_out=len(masters_out(ledger)),
        unaccounted=len(unaccounted_keys(ledger, on)),
        keyholders=len(holders),
    )


def master_table(ledger: KeyLedger, coverage: CoverageMap | None = None) -> tuple[str, ...]:
    """Every master that is out, with who has it and how far it reaches."""
    found = masters_out(ledger)
    if not found:
        return ("no master keys are out",)
    header = columns(("symbol", "tag", "keyholder", "doors"), (SYMBOL_WIDTH, 10, NAME_WIDTH))
    lines = [f"{len(found)} master keys are out", header]
    for key in found:
        reach = ""
        if coverage is not None:
            reach = str(coverage.reach_of(KeySymbol.parse(key.symbol)))
        lines.append(
            columns((key.symbol, key.tag, key.keyholder, reach), (SYMBOL_WIDTH, 10, NAME_WIDTH))
        )
    return tuple(lines)


def unaccounted_table(ledger: KeyLedger, on: date) -> tuple[str, ...]:
    """The keys nobody can produce, which is the number that gets quoted."""
    found = unaccounted_keys(ledger, on)
    if not found:
        return ("every key out is accounted for",)
    lines = [f"{len(found)} keys cannot be accounted for"]
    for key in found:
        why = "lost" if key.state.value == "lost" else "overdue"
        lines.append(
            columns((key.tag, key.symbol, key.keyholder, why), (10, SYMBOL_WIDTH, NAME_WIDTH))
        )
    return tuple(lines)


def concentration(ledger: KeyLedger, limit: int = 5) -> tuple[tuple[str, int], ...]:
    """Who holds the most keys, which is where a single loss hurts most."""
    counts: dict[str, int] = {}
    for key in ledger.out():
        counts[key.keyholder] = counts.get(key.keyholder, 0) + 1
    found = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))
    return tuple(found[:limit])


def movement(this_year: Return, last_year: Return | None) -> tuple[str, ...]:
    """What changed, which is the only part anybody argues about."""
    if last_year is None:
        return ("no return was filed last year, so there is nothing to compare",)
    lines = [f"against {last_year.year}"]
    for name, now, before in (
        ("keys out", this_year.keys_out, last_year.keys_out),
        ("masters out", this_year.masters_out, last_year.masters_out),
        ("unaccounted", this_year.unaccounted, last_year.unaccounted),
        ("keyholders", this_year.keyholders, last_year.keyholders),
    ):
        change = now - before
        way = "up" if change > 0 else "down" if change < 0 else "level"
        lines.append(f"  {name:<14}{now:>6}{before:>8}   {way} {abs(change)}")
    return tuple(lines)


def annual_return(
    ledger: KeyLedger,
    directory: Directory,
    on: date,
    coverage: CoverageMap | None = None,
    last_year: Return | None = None,
) -> tuple[str, ...]:
    """The whole return, in the order the form asks for it."""
    found = take(ledger, directory, on)
    lines = [
        f"key control return for {found.year}",
        found.describe(),
        f"{found.keyholders} keyholders, {found.keys_per_holder:.1f} keys each",
        "",
        *master_table(ledger, coverage),
        "",
        *unaccounted_table(ledger, on),
        "",
        "most keys held",
    ]
    lines.extend(f"  {name:<{NAME_WIDTH}}{count}" for name, count in concentration(ledger))
    lines.append("")
    lines.extend(movement(found, last_year))
    return tuple(lines)


def returns_table(found: Sequence[Return]) -> tuple[str, ...]:
    """Several years side by side, oldest first, for the trend."""
    if not found:
        return ("no returns have been filed",)
    ordered = sorted(found, key=lambda item: item.year)
    lines = [columns(("year", "out", "masters", "unaccounted", "holders"), (8, 8, 10, 14))]
    for item in ordered:
        lines.append(
            columns(
                (
                    str(item.year),
                    str(item.keys_out),
                    str(item.masters_out),
                    str(item.unaccounted),
                    str(item.keyholders),
                ),
                (8, 8, 10, 14),
            )
        )
    return tuple(lines)

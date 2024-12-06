"""The physical audit, where somebody walks round and looks at the keys.

Once a year a department is asked to put every key it holds on a table and
read the tag numbers out. What comes back never matches the ledger, and the
four ways it fails to match are all different problems.

A key the ledger says is out and nobody can produce is unaccounted for, and
that is the one that costs money. A tag that turns up and is not in the
ledger at all is a stray, which usually means somebody cut a key at a
hardware store. A tag the ledger closed and somebody is still carrying is a
key that came back on paper only. And a tag in the right department but the
wrong hands is a transfer that never got written down.

None of these are errors in the code sense. They are the output.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from datetime import date

from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger, KeyState


@dataclass(frozen=True)
class Sighting:
    """One tag somebody actually held up."""

    tag: str
    keyholder: str


@dataclass(frozen=True)
class AuditFinding:
    """One line the audit cannot explain."""

    kind: str
    tag: str
    expected: str = ""
    seen: str = ""

    def describe(self) -> str:
        if self.kind == "unaccounted":
            return f"{self.tag} is out to {self.expected} and nobody produced it"
        if self.kind == "stray":
            return f"{self.tag} turned up with {self.seen} and is not in the ledger"
        if self.kind == "closed":
            return f"{self.tag} was closed on paper and {self.seen} still has it"
        return f"{self.tag} is out to {self.expected} and {self.seen} has it"


@dataclass(frozen=True)
class AuditReport:
    """What one walk round a department found."""

    on: date
    department: str
    counted: int
    expected: int
    findings: tuple[AuditFinding, ...]

    @property
    def clean(self) -> bool:
        return not self.findings

    def of_kind(self, kind: str) -> tuple[AuditFinding, ...]:
        return tuple(finding for finding in self.findings if finding.kind == kind)

    @property
    def unaccounted(self) -> tuple[AuditFinding, ...]:
        return self.of_kind("unaccounted")

    def summary(self) -> str:
        return (
            f"{self.department} {self.on.isoformat()}: {self.counted} counted "
            f"against {self.expected} on the ledger, {len(self.findings)} to explain"
        )


def reconcile(
    ledger: KeyLedger,
    department: str,
    sightings: Iterable[Sighting],
    on: date,
) -> AuditReport:
    """Compare what was on the table against what the ledger says."""
    seen = {sighting.tag: sighting.keyholder for sighting in sightings}
    expected: dict[str, str] = {}
    for person in ledger.directory.in_department(department):
        for key in ledger.held_by(person.identifier):
            expected[key.tag] = key.keyholder

    findings: list[AuditFinding] = []
    for tag, holder in sorted(expected.items()):
        if tag not in seen:
            findings.append(AuditFinding("unaccounted", tag, expected=holder))
        elif seen[tag] != holder:
            findings.append(AuditFinding("wrong_holder", tag, expected=holder, seen=seen[tag]))
    for tag, holder in sorted(seen.items()):
        if tag in expected:
            continue
        if tag in ledger:
            key = ledger.key(tag)
            if key.state is not KeyState.OUT:
                findings.append(AuditFinding("closed", tag, seen=holder))
            else:
                findings.append(
                    AuditFinding("wrong_holder", tag, expected=key.keyholder, seen=holder)
                )
        else:
            findings.append(AuditFinding("stray", tag, seen=holder))
    return AuditReport(on, department, len(seen), len(expected), tuple(findings))


@dataclass(frozen=True)
class DepositCheck:
    """Whether the money and the metal agree."""

    ledger_cents: int
    book_cents: int
    tags_without_deposits: tuple[str, ...]
    deposits_without_keys: tuple[str, ...]

    @property
    def difference(self) -> int:
        return self.ledger_cents - self.book_cents

    @property
    def balances(self) -> bool:
        return (
            self.difference == 0
            and not self.tags_without_deposits
            and not self.deposits_without_keys
        )


def check_deposits(ledger: KeyLedger, book: DepositBook) -> DepositCheck:
    """Reconcile the deposit book against the keys that are out.

    Every key out with a deposit on it should have an open charge, and every
    open charge should belong to a key that is out. The two lists are usually
    short and they are always interesting.
    """
    out_tags = {key.tag: key.deposit_cents for key in ledger.out() if key.deposit_cents}
    open_tags = set(book.open_tags())
    missing = tuple(sorted(tag for tag in out_tags if tag not in open_tags))
    orphan = tuple(sorted(tag for tag in open_tags if tag not in out_tags))
    return DepositCheck(ledger.deposits_held(), book.total_held(), missing, orphan)


def holdings_by_department(ledger: KeyLedger, departments: Iterable[str]) -> Mapping[str, int]:
    """How many keys each department is holding, for the cover page."""
    counts: dict[str, int] = {}
    for department in departments:
        total = 0
        for person in ledger.directory.in_department(department):
            total += len(ledger.held_by(person.identifier))
        counts[department] = total
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))

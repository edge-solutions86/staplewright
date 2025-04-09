"""The paper key control produces: statements, chase lists and summaries.

A keyholder statement is the one that gets signed. It lists every key one
person is holding, what each one is cut to, when it is due, and what the shop
is holding in deposits against it, and the person signs at the bottom to say
they agree. Getting the deposit figure wrong on that sheet is how an argument
starts, so it comes off the deposit book rather than off a field somebody
typed.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.control.deposits import DepositBook, dollars
from hasplib.control.issue import IssuedKey, KeyLedger
from hasplib.control.notices import Notice
from hasplib.control.people import Directory, Keyholder

TAG_WIDTH = 8
SYMBOL_WIDTH = 10
DATE_WIDTH = 12


def _date(when: date | None) -> str:
    return when.isoformat() if when else ""


def key_line(key: IssuedKey, book: DepositBook | None = None) -> str:
    held = dollars(book.held_on(key.tag)) if book else ""
    return (
        f"{key.tag:<{TAG_WIDTH}}{key.symbol:<{SYMBOL_WIDTH}}"
        f"{_date(key.issued_on):<{DATE_WIDTH}}{_date(key.due_on):<{DATE_WIDTH}}{held}"
    )


def keyholder_statement(
    person: Keyholder,
    ledger: KeyLedger,
    book: DepositBook | None = None,
    on: date | None = None,
) -> tuple[str, ...]:
    """Every key one person is holding, for them to sign against."""
    keys = ledger.held_by(person.identifier)
    lines = [f"{person.label}  {person.department}"]
    if on is not None:
        lines.append(f"as at {on.isoformat()}")
    lines.append(
        f"{'tag':<{TAG_WIDTH}}{'cut':<{SYMBOL_WIDTH}}"
        f"{'issued':<{DATE_WIDTH}}{'due':<{DATE_WIDTH}}deposit"
    )
    lines.extend(key_line(key, book).rstrip() for key in keys)
    lines.append(f"{len(keys)} keys")
    if book is not None:
        lines.append(f"deposits held {dollars(book.balance_of(person.identifier))}")
    lines.append("signed ............................  date ..............")
    return tuple(lines)


def department_summary(
    directory: Directory, ledger: KeyLedger, department: str, today: date
) -> tuple[str, ...]:
    """Who in a department holds what, and who should not hold anything."""
    people = directory.in_department(department)
    lines = [f"{department}: {len(people)} on the books"]
    for person in people:
        keys = ledger.held_by(person.identifier)
        standing = "" if person.may_hold_keys(today) else f"  [{person.standing.value}]"
        lines.append(f"  {person.label:<34}{len(keys)} keys{standing}")
    total = sum(len(ledger.held_by(person.identifier)) for person in people)
    lines.append(f"{total} keys out to this department")
    return tuple(lines)


def overdue_report(ledger: KeyLedger, today: date) -> tuple[str, ...]:
    """The chase list, worst first."""
    late = ledger.overdue(today)
    if not late:
        return (f"nothing overdue as at {today.isoformat()}",)
    lines = [f"{len(late)} keys overdue as at {today.isoformat()}"]
    for key in late:
        who = f"{key.tag:<{TAG_WIDTH}}{key.keyholder:<{SYMBOL_WIDTH}}"
        lines.append(f"  {who}{key.days_late(today)} days")
    return tuple(lines)


def notice_run(notices: Sequence[Notice]) -> tuple[str, ...]:
    """What the letters run produced, grouped by how hard it is asking."""
    if not notices:
        return ("no letters to send today",)
    lines = [f"{len(notices)} letters"]
    for notice in notices:
        lines.append(f"  {notice.describe()}")
    holds = sorted({notice.keyholder for notice in notices if notice.places_hold})
    if holds:
        lines.append(f"holds to place: {', '.join(holds)}")
    return tuple(lines)


def deposit_statement(book: DepositBook, keyholder: str) -> tuple[str, ...]:
    """Every deposit line against one person, and what is left."""
    entries = book.entries_of(keyholder)
    lines = [f"deposits for {keyholder}"]
    for entry in entries:
        lines.append(
            f"  {entry.when.isoformat()}  {entry.tag:<{TAG_WIDTH}}"
            f"{entry.kind.value:<10}{dollars(entry.signed_cents)}"
        )
    lines.append(f"held {dollars(book.balance_of(keyholder))}")
    return tuple(lines)


def as_text(lines: Sequence[str]) -> str:
    return "\n".join(lines)

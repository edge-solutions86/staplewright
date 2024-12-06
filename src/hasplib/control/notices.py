"""Chasing keys that are late, in the order the manual says to chase them.

A key comes back late or it does not come back, and the difference between
those two is a series of letters. Reminder, then a first notice, then a
second, then a hold on the record, then the deposit is written off and the
opening goes on the rekey list.

The thresholds are policy and they change, which is why they are data. What
does not change is that each level is sent once. The shop has posted the same
first notice four times to one graduate student and does not intend to do it
again, so the log is checked before anything is generated.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from datetime import date
from enum import IntEnum

from hasplib.control.issue import IssuedKey, KeyLedger
from hasplib.errors import ConflictError, InputError


class NoticeLevel(IntEnum):
    """How hard the shop is currently asking. Ordered on purpose."""

    REMINDER = 1
    FIRST = 2
    SECOND = 3
    HOLD = 4
    WRITTEN_OFF = 5

    @property
    def label(self) -> str:
        return self.name.lower().replace("_", " ")


@dataclass(frozen=True)
class NoticePolicy:
    """How many days late each step happens."""

    reminder_days: int = 3
    first_days: int = 14
    second_days: int = 30
    hold_days: int = 60
    write_off_days: int = 120

    def __post_init__(self) -> None:
        steps = [
            self.reminder_days,
            self.first_days,
            self.second_days,
            self.hold_days,
            self.write_off_days,
        ]
        if any(day < 0 for day in steps):
            raise InputError("a notice threshold is a number of days")
        if steps != sorted(steps) or len(set(steps)) != len(steps):
            raise ConflictError("the notice steps have to get later", steps=tuple(steps))

    def level_for(self, days_late: int) -> NoticeLevel | None:
        """The furthest step this many days late has reached."""
        if days_late >= self.write_off_days:
            return NoticeLevel.WRITTEN_OFF
        if days_late >= self.hold_days:
            return NoticeLevel.HOLD
        if days_late >= self.second_days:
            return NoticeLevel.SECOND
        if days_late >= self.first_days:
            return NoticeLevel.FIRST
        if days_late >= self.reminder_days:
            return NoticeLevel.REMINDER
        return None


DEFAULT_POLICY = NoticePolicy()


@dataclass(frozen=True)
class Notice:
    """One letter the shop owes somebody."""

    tag: str
    keyholder: str
    level: NoticeLevel
    days_late: int
    on: date

    @property
    def places_hold(self) -> bool:
        return self.level >= NoticeLevel.HOLD

    def describe(self) -> str:
        return f"{self.tag} {self.days_late} days late, {self.level.label} to {self.keyholder}"


class NoticeLog:
    """What has already been sent, so nothing goes out twice."""

    def __init__(self) -> None:
        self._sent: dict[str, set[NoticeLevel]] = {}
        self._order: list[Notice] = []

    def record(self, notice: Notice) -> Notice:
        levels = self._sent.setdefault(notice.tag, set())
        if notice.level in levels:
            raise ConflictError(
                "that notice has already gone out",
                tag=notice.tag,
                level=notice.level.label,
            )
        levels.add(notice.level)
        self._order.append(notice)
        return notice

    def sent_for(self, tag: str) -> frozenset[NoticeLevel]:
        return frozenset(self._sent.get(tag, set()))

    def highest_sent(self, tag: str) -> NoticeLevel | None:
        levels = self._sent.get(tag)
        return max(levels) if levels else None

    def history(self) -> tuple[Notice, ...]:
        return tuple(self._order)

    def clear(self, tag: str) -> None:
        """The key came back. The next time it goes out, start over."""
        self._sent.pop(tag, None)

    def __len__(self) -> int:
        return len(self._order)


def notice_for(
    key: IssuedKey,
    today: date,
    log: NoticeLog,
    policy: NoticePolicy = DEFAULT_POLICY,
) -> Notice | None:
    """The letter one late key is owed today, if any is."""
    days = key.days_late(today)
    if not days:
        return None
    level = policy.level_for(days)
    if level is None:
        return None
    already = log.highest_sent(key.tag)
    if already is not None and already >= level:
        return None
    return Notice(key.tag, key.keyholder, level, days, today)


def notices_due(
    ledger: KeyLedger,
    today: date,
    log: NoticeLog,
    policy: NoticePolicy = DEFAULT_POLICY,
) -> tuple[Notice, ...]:
    """Every letter owed today, worst first."""
    found: list[Notice] = []
    for key in ledger.overdue(today):
        notice = notice_for(key, today, log, policy)
        if notice is not None:
            found.append(notice)
    found.sort(key=lambda notice: (-notice.level, -notice.days_late, notice.tag))
    return tuple(found)


def holds_to_place(notices: Iterable[Notice]) -> tuple[str, ...]:
    """Keyholders whose records get a hold out of this run."""
    return tuple(sorted({notice.keyholder for notice in notices if notice.places_hold}))


def write_offs(notices: Iterable[Notice]) -> tuple[str, ...]:
    """Tags the shop has given up on, which go to the rekey list."""
    return tuple(
        sorted(notice.tag for notice in notices if notice.level is NoticeLevel.WRITTEN_OFF)
    )

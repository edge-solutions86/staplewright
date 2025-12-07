"""Every number the shop changes, in one place instead of nine.

These were scattered across the modules that used them, which is fine until
the deposit goes up and somebody has to find all four places it is written.
They are gathered here, still typed, still with their own defaults, and the
modules take them as arguments rather than reaching for a global.

Reading them from the environment is deliberately dull: names are upper case
with a HASPLIB prefix, values are whole numbers or true and false, and
anything unreadable raises rather than falling back to a default nobody
intended.
"""

from __future__ import annotations

import os
from collections.abc import Callable, Mapping
from dataclasses import dataclass, field, replace
from typing import Any

from hasplib.control.deposits import DepositRates
from hasplib.control.escrow import BoxPolicy
from hasplib.control.notices import NoticePolicy
from hasplib.control.transfer import TransferPolicy
from hasplib.errors import InputError
from hasplib.keys.stamping import Marking, StampPolicy
from hasplib.rekey.costing import RateCard
from hasplib.rekey.scope import ScopePolicy
from hasplib.spec.rules import CuttingRules

PREFIX = "HASPLIB_"
Reader = Callable[[Mapping[str, str], str, Any], Any]


@dataclass(frozen=True)
class ShopSettings:
    """The whole of shop policy, gathered."""

    cutting: CuttingRules = field(default_factory=CuttingRules)
    deposits: DepositRates = field(default_factory=DepositRates)
    notices: NoticePolicy = field(default_factory=NoticePolicy)
    scope: ScopePolicy = field(default_factory=ScopePolicy)
    rates: RateCard = field(default_factory=RateCard)
    boxes: BoxPolicy = field(default_factory=BoxPolicy)
    transfers: TransferPolicy = field(default_factory=TransferPolicy)
    stamping: StampPolicy = field(default_factory=StampPolicy)
    quarantine_years: int = 7
    progression_step: int = 2

    def __post_init__(self) -> None:
        if self.quarantine_years < 1:
            raise InputError("a quarantine window is at least a year")
        if self.progression_step < 1:
            raise InputError("a progression step is at least one")

    def with_deposits(self, change_key_cents: int, master_cents: int) -> ShopSettings:
        return replace(
            self,
            deposits=DepositRates(change_key_cents=change_key_cents, master_cents=master_cents),
        )

    def describe(self) -> tuple[str, ...]:
        """What the settings are, for the top of a report."""
        return (
            f"macs policy: runs to {self.cutting.max_run}, "
            f"{self.cutting.min_distinct} depths minimum",
            f"deposits: {self.deposits.change_key_cents} and {self.deposits.master_cents} cents",
            f"notices: reminder at {self.notices.reminder_days} days, "
            f"write off at {self.notices.write_off_days}",
            f"rekey: cap {self.scope.cylinder_cap}, strict repin {self.scope.recut_all_on_repin}",
            f"boxes: no wider than level {self.boxes.lowest_level}, "
            f"checked every {self.boxes.check_days} days",
            f"handovers: confirmed inside {self.transfers.confirm_days} days",
            f"stamping: change keys carry {self.stamping.change_keys.value}, "
            f"masters carry {self.stamping.masters.value}",
            f"quarantine: {self.quarantine_years} years",
            f"progression: {self.progression_step} steps",
        )


DEFAULTS = ShopSettings()


def _whole(source: Mapping[str, str], name: str, fallback: int) -> int:
    raw = source.get(PREFIX + name)
    if raw is None:
        return fallback
    try:
        return int(raw)
    except ValueError:
        raise InputError("that setting is a whole number", setting=name, value=raw) from None


def _flag(source: Mapping[str, str], name: str, fallback: bool) -> bool:
    raw = source.get(PREFIX + name)
    if raw is None:
        return fallback
    lowered = raw.strip().lower()
    if lowered in {"1", "true", "yes", "on"}:
        return True
    if lowered in {"0", "false", "no", "off"}:
        return False
    raise InputError("that setting is true or false", setting=name, value=raw)


def _marking(source: Mapping[str, str], name: str, fallback: Marking) -> Marking:
    """A marking by its own name, which is what a person would type."""
    raw = source.get(PREFIX + name)
    if raw is None:
        return fallback
    wanted = raw.strip().lower()
    for item in Marking:
        if wanted in {item.name.lower(), item.value}:
            return item
    raise InputError("nothing gets stamped that way", setting=name, value=raw)


def _reader(kind: type) -> Reader:
    if kind is bool:
        return _flag
    if kind is Marking:
        return _marking
    return _whole


@dataclass(frozen=True)
class Setting:
    """One knob: where it lives, what it is called, and how it reads."""

    group: str
    attribute: str
    variable: str

    def read(self, source: Mapping[str, str], fallback: Any) -> Any:
        return _reader(type(fallback))(source, self.variable, fallback)


SETTINGS = (
    Setting("cutting", "max_run", "MAX_RUN"),
    Setting("cutting", "min_distinct", "MIN_DISTINCT"),
    Setting("deposits", "change_key_cents", "DEPOSIT_CHANGE_CENTS"),
    Setting("deposits", "master_cents", "DEPOSIT_MASTER_CENTS"),
    Setting("notices", "reminder_days", "REMINDER_DAYS"),
    Setting("notices", "first_days", "FIRST_NOTICE_DAYS"),
    Setting("notices", "second_days", "SECOND_NOTICE_DAYS"),
    Setting("notices", "hold_days", "HOLD_DAYS"),
    Setting("notices", "write_off_days", "WRITE_OFF_DAYS"),
    Setting("scope", "recut_all_on_repin", "STRICT_REPIN"),
    Setting("scope", "always_rekey_exterior", "REKEY_EXTERIOR"),
    Setting("scope", "always_rekey_masters", "REKEY_MASTERS"),
    Setting("scope", "ignore_below_openings", "IGNORE_BELOW"),
    Setting("rates", "labour_cents_per_hour", "LABOUR_CENTS_HOUR"),
    Setting("rates", "blank_cents", "BLANK_CENTS"),
    Setting("boxes", "lowest_level", "BOX_LOWEST_LEVEL"),
    Setting("boxes", "check_days", "BOX_CHECK_DAYS"),
    Setting("boxes", "reseal_days", "BOX_RESEAL_DAYS"),
    Setting("transfers", "confirm_days", "CONFIRM_DAYS"),
    Setting("transfers", "allow_master_transfer", "MASTER_HANDOVER"),
    Setting("stamping", "change_keys", "STAMP_CHANGE_KEYS"),
    Setting("stamping", "masters", "STAMP_MASTERS"),
    Setting("stamping", "add_do_not_duplicate", "STAMP_DND"),
    Setting("stamping", "bow_characters", "BOW_CHARACTERS"),
    Setting("", "quarantine_years", "QUARANTINE_YEARS"),
    Setting("", "progression_step", "PROGRESSION_STEP"),
)
"""Every knob this shop has, one row each.

This used to be written out three times over: once in the dataclass, once in
a constructor call inside ``from_mapping``, and once again in the test that
proved the variable was read. Adding a setting meant editing all three and
the third was the one people forgot, so a setting would ship unread and the
default would quietly stand for a year.
"""


def variable_names() -> tuple[str, ...]:
    """Every environment variable this reads, which is the documentation."""
    return tuple(sorted(PREFIX + item.variable for item in SETTINGS))


def _group_of(settings: ShopSettings, name: str) -> Any:
    return settings if not name else getattr(settings, name)


def from_mapping(source: Mapping[str, str]) -> ShopSettings:
    """Build settings from a mapping, leaving anything absent at its default.

    Built by walking the table rather than by naming every field, so a knob
    that is in the table is read and a knob that is not cannot be.
    """
    changes: dict[str, dict[str, Any]] = {}
    top: dict[str, Any] = {}
    for item in SETTINGS:
        fallback = getattr(_group_of(DEFAULTS, item.group), item.attribute)
        found = item.read(source, fallback)
        if item.group:
            changes.setdefault(item.group, {})[item.attribute] = found
        else:
            top[item.attribute] = found
    groups = {
        name: replace(_group_of(DEFAULTS, name), **values) for name, values in changes.items()
    }
    return replace(DEFAULTS, **groups, **top)


def from_env() -> ShopSettings:
    """The same thing, off the process environment."""
    return from_mapping(os.environ)

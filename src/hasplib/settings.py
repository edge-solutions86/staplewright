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
from collections.abc import Mapping
from dataclasses import dataclass, field, replace

from hasplib.control.deposits import DepositRates
from hasplib.control.escrow import BoxPolicy
from hasplib.control.notices import NoticePolicy
from hasplib.errors import InputError
from hasplib.rekey.costing import RateCard
from hasplib.rekey.scope import ScopePolicy
from hasplib.spec.rules import CuttingRules

PREFIX = "HASPLIB_"


@dataclass(frozen=True)
class ShopSettings:
    """The whole of shop policy, gathered."""

    cutting: CuttingRules = field(default_factory=CuttingRules)
    deposits: DepositRates = field(default_factory=DepositRates)
    notices: NoticePolicy = field(default_factory=NoticePolicy)
    scope: ScopePolicy = field(default_factory=ScopePolicy)
    rates: RateCard = field(default_factory=RateCard)
    boxes: BoxPolicy = field(default_factory=BoxPolicy)
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


def from_mapping(source: Mapping[str, str]) -> ShopSettings:
    """Build settings from a mapping, leaving anything absent at its default."""
    return ShopSettings(
        cutting=CuttingRules(
            max_run=_whole(source, "MAX_RUN", DEFAULTS.cutting.max_run),
            min_distinct=_whole(source, "MIN_DISTINCT", DEFAULTS.cutting.min_distinct),
        ),
        deposits=DepositRates(
            change_key_cents=_whole(
                source, "DEPOSIT_CHANGE_CENTS", DEFAULTS.deposits.change_key_cents
            ),
            master_cents=_whole(source, "DEPOSIT_MASTER_CENTS", DEFAULTS.deposits.master_cents),
        ),
        notices=NoticePolicy(
            reminder_days=_whole(source, "REMINDER_DAYS", DEFAULTS.notices.reminder_days),
            first_days=_whole(source, "FIRST_NOTICE_DAYS", DEFAULTS.notices.first_days),
            second_days=_whole(source, "SECOND_NOTICE_DAYS", DEFAULTS.notices.second_days),
            hold_days=_whole(source, "HOLD_DAYS", DEFAULTS.notices.hold_days),
            write_off_days=_whole(source, "WRITE_OFF_DAYS", DEFAULTS.notices.write_off_days),
        ),
        scope=ScopePolicy(
            recut_all_on_repin=_flag(source, "STRICT_REPIN", DEFAULTS.scope.recut_all_on_repin),
            always_rekey_exterior=_flag(
                source, "REKEY_EXTERIOR", DEFAULTS.scope.always_rekey_exterior
            ),
            always_rekey_masters=_flag(
                source, "REKEY_MASTERS", DEFAULTS.scope.always_rekey_masters
            ),
            ignore_below_openings=_whole(
                source, "IGNORE_BELOW", DEFAULTS.scope.ignore_below_openings
            ),
        ),
        rates=RateCard(
            labour_cents_per_hour=_whole(
                source, "LABOUR_CENTS_HOUR", DEFAULTS.rates.labour_cents_per_hour
            ),
            blank_cents=_whole(source, "BLANK_CENTS", DEFAULTS.rates.blank_cents),
        ),
        boxes=BoxPolicy(
            lowest_level=_whole(source, "BOX_LOWEST_LEVEL", DEFAULTS.boxes.lowest_level),
            check_days=_whole(source, "BOX_CHECK_DAYS", DEFAULTS.boxes.check_days),
            reseal_days=_whole(source, "BOX_RESEAL_DAYS", DEFAULTS.boxes.reseal_days),
        ),
        quarantine_years=_whole(source, "QUARANTINE_YEARS", DEFAULTS.quarantine_years),
        progression_step=_whole(source, "PROGRESSION_STEP", DEFAULTS.progression_step),
    )


def from_env() -> ShopSettings:
    """The same thing, off the process environment."""
    return from_mapping(os.environ)

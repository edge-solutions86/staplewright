"""The options every command group had written for itself.

Three groups had each declared a day option and a function to turn it into a
date, and they had drifted: two of them called it --on and the third called
the same thing --at, and one of them defaulted to today while another
defaulted to nothing and crashed. A person typing at the wall terminal should
not have to remember which group they are in.

So the options live here and the groups import them. The names are fixed:
--on for a day, --at for a moment, and both default to now.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date, datetime
from typing import Any

import click

DAY_FORMATS = ["%Y-%m-%d"]


def day_option(help_text: str = "the day it happened", name: str = "day") -> Callable[[Any], Any]:
    """The standing --on option, which every group spells the same way.

    The variable it lands in is named because the older groups call it when
    and renaming a parameter is not worth a migration of every command.
    """
    return click.option(
        "--on",
        name,
        type=click.DateTime(DAY_FORMATS),
        default=None,
        help=help_text,
    )


def moment_option(
    help_text: str = "when it happened", name: str = "moment"
) -> Callable[[Any], Any]:
    """The standing --at option, for the things logged to the minute."""
    return click.option(
        "--at",
        name,
        type=click.DateTime(),
        default=None,
        help=help_text,
    )


def as_day(day: datetime | None) -> date:
    """A --on value, or today when nobody said."""
    return day.date() if day is not None else date.today()


def as_moment(moment: datetime | None) -> datetime:
    """An --at value, or now when nobody said."""
    return moment if moment is not None else datetime.now()


def as_given_day(day: datetime | None, otherwise: date) -> date:
    """A --on value, or a day the caller already knows."""
    return day.date() if day is not None else otherwise

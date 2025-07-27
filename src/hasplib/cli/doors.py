"""Commands for the doors: lockout calls, surveys, reviews, keyed alike sets.

These are the ones typed with one hand. A lockout is logged while somebody is
still on the telephone, so the command takes the door and the person and
nothing else, and the technician and the time go on afterwards when there is
time to type them.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from hasplib.campus.keyed_alike import KeyedAlikeSet
from hasplib.campus.lockouts import Identification
from hasplib.campus.survey import Condition, Deficiency
from hasplib.control.review import Answer
from hasplib.services.access import KeyedAlikeService, ReviewService
from hasplib.services.facilities import LockoutService, SurveyService

NOW = click.option("--at", "moment", type=click.DateTime(), default=None, help="when it happened")
DAY = click.option("--on", "day", type=click.DateTime(["%Y-%m-%d"]), default=None)


def _moment(moment: datetime | None) -> datetime:
    return moment if moment is not None else datetime.now()


def _day(day: datetime | None) -> date:
    return day.date() if day is not None else date.today()


@click.group("doors")
def doors() -> None:
    """Lockouts, surveys, reviews and keyed alike sets."""


@doors.command("lockout-series")
@click.pass_obj
def lockout_series(shop) -> None:
    """Open the lockout call counter. Once, when the shop is set up."""
    with shop.session() as session:
        LockoutService(session).open_series()
        click.echo("lockout series opened")


@doors.command("lockout")
@click.argument("opening")
@click.argument("keyholder")
@NOW
@click.pass_obj
def lockout(shop, opening, keyholder, moment) -> None:
    """Log a lockout call."""
    with shop.session() as session:
        row = LockoutService(session).log(opening, keyholder, _moment(moment))
        click.echo(f"{row.number} logged for {keyholder} at {opening}")


@doors.command("attend")
@click.argument("number")
@click.argument("technician")
@NOW
@click.pass_obj
def attend(shop, number, technician, moment) -> None:
    """Record who went."""
    with shop.session() as session:
        LockoutService(session).attend(number, technician, _moment(moment))
        click.echo(f"{number} attended by {technician}")


@doors.command("let-in")
@click.argument("number")
@click.option(
    "--how",
    type=click.Choice([item.value for item in Identification if item is not Identification.NONE]),
    default=Identification.CAMPUS_CARD.value,
)
@NOW
@click.pass_obj
def let_in(shop, number, how, moment) -> None:
    """Open the door, recording how they were identified."""
    with shop.session() as session:
        LockoutService(session).open_door(number, Identification(how), _moment(moment))
        click.echo(f"{number} opened, identified by {how}")


@doors.command("survey-start")
@click.argument("building")
@DAY
@click.pass_obj
def survey_start(shop, building, day) -> None:
    """Start a walk round a building."""
    with shop.session() as session:
        row = SurveyService(session).start(building, _day(day))
        click.echo(f"survey of {row.building_code} opened for {row.walked_on}")


@doors.command("survey-line")
@click.argument("building")
@click.argument("opening")
@click.argument("surveyor")
@click.option(
    "--condition",
    type=click.Choice([item.name.lower() for item in Condition]),
    default="good",
)
@click.option("--fault", "faults", multiple=True, help="a deficiency, repeatable")
@DAY
@click.pass_obj
def survey_line(shop, building, opening, surveyor, condition, faults, day) -> None:
    """Record one door on a walk, or correct the line already there."""
    known = {item.value: item for item in Deficiency}
    unknown = [name for name in faults if name not in known]
    if unknown:
        raise click.BadParameter(f"no such deficiency: {unknown[0]}")
    with shop.session() as session:
        SurveyService(session).record(
            building,
            _day(day),
            opening,
            surveyor,
            Condition[condition.upper()],
            tuple(known[name] for name in faults),
        )
        click.echo(f"{opening} recorded {condition}")


@doors.command("survey")
@click.argument("building")
@DAY
@click.pass_obj
def survey(shop, building, day) -> None:
    """Print one walk, worst door first."""
    with shop.session() as session:
        walk = SurveyService(session).survey(building, _day(day))
        click.echo(f"{walk.building} {walk.walked_on}, {len(walk)} doors")
        for line in walk.worst_first():
            click.echo(f"  {line.describe()}")


@doors.command("review-open")
@click.argument("department")
@click.argument("due", type=click.DateTime(["%Y-%m-%d"]))
@DAY
@click.pass_obj
def review_open(shop, department, due, day) -> None:
    """Open a review and freeze what the department holds."""
    with shop.session() as session:
        row = ReviewService(session).open_review(department, _day(day), due.date())
        click.echo(f"{row.department} review opened, due {row.due_on}")


@doors.command("review-answer")
@click.argument("department")
@click.argument("tag")
@click.option(
    "--answer",
    type=click.Choice(["confirmed", "return", "unknown"]),
    default="confirmed",
)
@click.option("--opened", type=click.DateTime(["%Y-%m-%d"]), required=True)
@DAY
@click.pass_obj
def review_answer(shop, department, tag, answer, opened, day) -> None:
    """Record what a department said about one key."""
    wanted = {
        "confirmed": Answer.CONFIRMED,
        "return": Answer.RETURN,
        "unknown": Answer.UNKNOWN,
    }[answer]
    with shop.session() as session:
        ReviewService(session).answer(department, opened.date(), tag, wanted, _day(day))
        click.echo(f"{tag} {wanted.value}")


@doors.command("reviews")
@click.pass_obj
def reviews(shop) -> None:
    """The reviews still waiting on a department."""
    with shop.session() as session:
        service = ReviewService(session)
        rows = service.open_reviews()
        if not rows:
            click.echo("nothing open")
            return
        for row in rows:
            review = service.review(row.department, row.opened_on)
            click.echo(f"{row.department:<16}due {row.due_on}  {review.summary()}")


@doors.command("alike")
@click.argument("name")
@click.argument("openings", nargs=-1, required=True)
@click.pass_obj
def alike(shop, name, openings) -> None:
    """Make a keyed alike set out of two or more doors."""
    with shop.session() as session:
        KeyedAlikeService(session).create(KeyedAlikeSet(name, tuple(openings)))
        click.echo(f"{name} keyed alike across {len(openings)} doors")


@doors.command("alike-list")
@click.pass_obj
def alike_list(shop) -> None:
    """Every keyed alike set the shop maintains."""
    with shop.session() as session:
        found = KeyedAlikeService(session).sets()
        if not found:
            click.echo("no keyed alike sets")
            return
        for item in found:
            click.echo(item.describe())

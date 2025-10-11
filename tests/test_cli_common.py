from datetime import date, datetime

import click
import pytest
from click.testing import CliRunner

from hasplib.cli.common import (
    as_day,
    as_given_day,
    as_moment,
    day_option,
    moment_option,
)

WHEN = datetime(2025, 4, 8, 14, 12)


def test_a_day_given_is_the_day_used():
    assert as_day(WHEN) == date(2025, 4, 8)


def test_no_day_given_means_today():
    assert as_day(None) == date.today()


def test_a_moment_given_is_the_moment_used():
    assert as_moment(WHEN) == WHEN


def test_no_moment_given_means_now():
    found = as_moment(None)
    assert isinstance(found, datetime)
    assert abs((found - datetime.now()).total_seconds()) < 5


def test_a_caller_can_name_its_own_fallback_day():
    assert as_given_day(None, date(2020, 1, 1)) == date(2020, 1, 1)
    assert as_given_day(WHEN, date(2020, 1, 1)) == date(2025, 4, 8)


def a_command(option):
    @click.command()
    @option
    def run(**kwargs):
        for value in kwargs.values():
            click.echo(repr(value))

    return run


def test_the_day_option_is_spelled_on_everywhere():
    found = CliRunner().invoke(a_command(day_option()), ["--on", "2025-04-08"])
    assert found.exit_code == 0
    assert "2025, 4, 8" in found.output


def test_the_day_option_defaults_to_nothing():
    found = CliRunner().invoke(a_command(day_option()), [])
    assert found.output.strip() == "None"


def test_a_day_in_the_wrong_shape_is_refused():
    found = CliRunner().invoke(a_command(day_option()), ["--on", "the eighth"])
    assert found.exit_code != 0


def test_the_moment_option_is_spelled_at_everywhere():
    found = CliRunner().invoke(a_command(moment_option()), ["--at", "2025-04-08T14:12:00"])
    assert found.exit_code == 0
    assert "14, 12" in found.output


def test_the_help_text_can_be_set():
    command = a_command(day_option("the day the key came back"))
    found = CliRunner().invoke(command, ["--help"])
    assert "the day the key came back" in found.output


def test_the_standing_help_text_is_used_when_none_is_given():
    found = CliRunner().invoke(a_command(day_option()), ["--help"])
    assert "the day it happened" in found.output


def test_the_working_helper_hands_over_a_service_on_a_session():
    from hasplib.cli.common import working

    seen = []

    class Shop:
        def session(self):
            from contextlib import contextmanager

            @contextmanager
            def held():
                seen.append("opened")
                yield "the session"
                seen.append("closed")

            return held()

    class Service:
        def __init__(self, session, label="plain"):
            self.session = session
            self.label = label

    with working(Shop(), Service) as service:
        assert service.session == "the session"
        assert service.label == "plain"
    assert seen == ["opened", "closed"]


def test_the_working_helper_passes_the_extra_arguments_through():
    from contextlib import contextmanager

    from hasplib.cli.common import working

    class Shop:
        def session(self):
            @contextmanager
            def held():
                yield "the session"

            return held()

    class Service:
        def __init__(self, session, label):
            self.label = label

    with working(Shop(), Service, "wide") as service:
        assert service.label == "wide"


def test_the_session_closes_even_when_the_body_raises():
    from contextlib import contextmanager

    from hasplib.cli.common import working

    seen = []

    class Shop:
        def session(self):
            @contextmanager
            def held():
                try:
                    yield "the session"
                finally:
                    seen.append("closed")

            return held()

    class Service:
        def __init__(self, session):
            pass

    with pytest.raises(ValueError), working(Shop(), Service):
        raise ValueError("something went wrong")
    assert seen == ["closed"]


def test_echoing_lines_prints_one_per_line():
    import click
    from click.testing import CliRunner

    from hasplib.cli.common import echo_lines

    @click.command()
    def show():
        echo_lines(("first", "second"))

    found = CliRunner().invoke(show)
    assert found.output.splitlines() == ["first", "second"]


def test_echoing_nothing_prints_nothing():
    import click
    from click.testing import CliRunner

    from hasplib.cli.common import echo_lines

    @click.command()
    def show():
        echo_lines(())

    assert CliRunner().invoke(show).output == ""

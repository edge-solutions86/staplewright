import pytest
from click.testing import CliRunner

import dbcampus
from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema

WHEN = "2025-05-12"
OPENED = "2025-09-01"
DUE = "2025-10-01"
AT = "2025-04-08T14:12:00"


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    dbcampus.build(session)
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def with_series(shop):
    call(shop, "doors", "lockout-series")
    return shop


def test_the_lockout_series_opens_once(shop):
    assert "series opened" in call(shop, "doors", "lockout-series").output
    assert call(shop, "doors", "lockout-series").exit_code != 0


def test_a_lockout_is_logged(shop):
    with_series(shop)
    found = call(shop, "doors", "lockout", "MOO-214", "kmaki", "--at", AT)
    assert "L-00001 logged for kmaki at MOO-214" in found.output


def test_a_lockout_about_a_door_nobody_has_fails(shop):
    with_series(shop)
    assert call(shop, "doors", "lockout", "ZZZ-1", "kmaki").exit_code != 0


def test_attending_and_letting_in(shop):
    with_series(shop)
    call(shop, "doors", "lockout", "MOO-214", "kmaki", "--at", AT)
    call(shop, "doors", "attend", "L-00001", "jkoivu", "--at", "2025-04-08T14:31:00")
    found = call(shop, "doors", "let-in", "L-00001", "--at", "2025-04-08T14:35:00")
    assert "opened, identified by campus card" in found.output


def test_letting_in_before_anybody_attends_fails(shop):
    with_series(shop)
    call(shop, "doors", "lockout", "MOO-214", "kmaki", "--at", AT)
    assert call(shop, "doors", "let-in", "L-00001").exit_code != 0


def test_a_survey_starts_and_takes_lines(shop):
    call(shop, "doors", "survey-start", "MOO", "--on", WHEN)
    found = call(shop, "doors", "survey-line", "MOO", "MOO-214", "jkoivu", "--on", WHEN)
    assert "MOO-214 recorded good" in found.output


def test_a_survey_starts_once_a_day(shop):
    call(shop, "doors", "survey-start", "MOO", "--on", WHEN)
    assert call(shop, "doors", "survey-start", "MOO", "--on", WHEN).exit_code != 0


def test_a_deficiency_nobody_has_heard_of_is_refused(shop):
    call(shop, "doors", "survey-start", "MOO", "--on", WHEN)
    found = call(
        shop,
        "doors",
        "survey-line",
        "MOO",
        "MOO-214",
        "jkoivu",
        "--fault",
        "haunted",
        "--on",
        WHEN,
    )
    assert found.exit_code != 0


def test_a_known_deficiency_is_recorded(shop):
    call(shop, "doors", "survey-start", "MOO", "--on", WHEN)
    call(
        shop,
        "doors",
        "survey-line",
        "MOO",
        "MOO-214",
        "jkoivu",
        "--condition",
        "failing",
        "--fault",
        "hardware needs replacing before it is rekeyed",
        "--on",
        WHEN,
    )
    found = call(shop, "doors", "survey", "MOO", "--on", WHEN)
    assert "needs replacing" in found.output


def test_the_survey_prints_worst_first(shop):
    call(shop, "doors", "survey-start", "MOO", "--on", WHEN)
    call(shop, "doors", "survey-line", "MOO", "MOO-214", "jkoivu", "--on", WHEN)
    call(
        shop,
        "doors",
        "survey-line",
        "MOO",
        "MOO-216",
        "jkoivu",
        "--condition",
        "broken",
        "--on",
        WHEN,
    )
    found = call(shop, "doors", "survey", "MOO", "--on", WHEN)
    lines = [line for line in found.output.splitlines() if line.startswith("  ")]
    assert lines[0].strip().startswith("MOO-216")


def test_a_review_opens_and_lists(shop):
    call(shop, "doors", "review-open", "geography", DUE, "--on", OPENED)
    found = call(shop, "doors", "reviews")
    assert "geography" in found.output
    assert "3 unanswered" in found.output


def test_a_review_takes_an_answer(shop):
    call(shop, "doors", "review-open", "geography", DUE, "--on", OPENED)
    found = call(
        shop,
        "doors",
        "review-answer",
        "geography",
        "41207",
        "--answer",
        "return",
        "--opened",
        OPENED,
        "--on",
        DUE,
    )
    assert "41207 asked back" in found.output


def test_an_answer_nobody_has_heard_of_is_refused(shop):
    call(shop, "doors", "review-open", "geography", DUE, "--on", OPENED)
    found = call(
        shop,
        "doors",
        "review-answer",
        "geography",
        "41207",
        "--answer",
        "maybe",
        "--opened",
        OPENED,
    )
    assert found.exit_code != 0


def test_nothing_open_says_so(shop):
    assert call(shop, "doors", "reviews").output.strip() == "nothing open"


def test_a_keyed_alike_set_is_made_and_listed(shop):
    found = call(shop, "doors", "alike", "cupboards", "MOO-214", "MOO-216")
    assert "keyed alike across 2 doors" in found.output
    assert "MOO-214, MOO-216" in call(shop, "doors", "alike-list").output


def test_a_door_already_in_a_set_is_refused(shop):
    call(shop, "doors", "alike", "cupboards", "MOO-214", "MOO-216")
    assert call(shop, "doors", "alike", "stores", "MOO-214", "MOO-218").exit_code != 0


def test_no_sets_says_so(shop):
    assert call(shop, "doors", "alike-list").output.strip() == "no keyed alike sets"

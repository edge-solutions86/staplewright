import pytest
from click.testing import CliRunner

from hasplib.cli.main import cli
from hasplib.persistence.engine import build_engine, build_sessions, create_schema
from hasplib.persistence.mappers.control import authorizer_to_row, keyholder_to_row
from people import TODAY, a_directory

WHEN = TODAY.isoformat()
LATER = "2025-06-02"


@pytest.fixture()
def shop(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    session = build_sessions(engine)()
    directory = a_directory()
    for person in directory:
        session.add(keyholder_to_row(person))
    session.flush()
    for signer in directory.authorizers():
        session.add(authorizer_to_row(signer))
    session.commit()
    session.close()
    engine.dispose()
    return url


def call(shop, *args):
    return CliRunner().invoke(cli, ["--database", shop, *args])


def with_series(shop):
    call(shop, "counter", "series")
    return shop


def a_request(shop, keyholder: str = "kmaki"):
    found = call(
        shop,
        "counter",
        "request",
        keyholder,
        "AA1",
        "MOO",
        "moved into 214",
        "--on",
        WHEN,
    )
    return found.output.split()[0]


def test_the_series_open_once(shop):
    found = call(shop, "counter", "series")
    assert "series opened" in found.output
    assert call(shop, "counter", "series").exit_code != 0


def test_a_request_comes_back_with_its_number(shop):
    with_series(shop)
    found = call(shop, "counter", "request", "kmaki", "AA1", "MOO", "moved in", "--on", WHEN)
    assert found.output.startswith("R-0001 raised")


def test_a_request_for_somebody_who_is_not_there_fails(shop):
    with_series(shop)
    found = call(shop, "counter", "request", "ghost", "AA1", "MOO", "moved in")
    assert found.exit_code != 0


def test_approving_says_who_signed(shop):
    with_series(shop)
    number = a_request(shop)
    found = call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    assert found.output.strip() == f"{number} approved by rvirta"


def test_a_signature_that_does_not_reach_fails(shop):
    with_series(shop)
    number = a_request(shop)
    assert call(shop, "counter", "approve", number, "phamm", "--on", WHEN).exit_code != 0


def test_denying_says_so(shop):
    with_series(shop)
    number = a_request(shop)
    found = call(shop, "counter", "deny", number, "rvirta", "shared room", "--on", WHEN)
    assert found.output.strip() == f"{number} denied"


def test_issuing_names_the_tag_and_the_deposit(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    found = call(shop, "counter", "issue", number, "--on", WHEN)
    assert "tag 41000 issued to kmaki" in found.output
    assert "deposit 2500" in found.output


def test_a_deposit_can_be_named_at_the_counter(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    found = call(shop, "counter", "issue", number, "--on", WHEN, "--deposit", "0")
    assert "deposit 0" in found.output


def test_a_due_date_can_be_given(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    found = call(shop, "counter", "issue", number, "--on", WHEN, "--due", LATER)
    assert found.exit_code == 0


def test_a_key_comes_back(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    call(shop, "counter", "issue", number, "--on", WHEN)
    found = call(shop, "counter", "return", "41000", "--on", LATER)
    assert found.output.strip() == "tag 41000 back"


def test_a_key_that_is_gone_is_written_off(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    call(shop, "counter", "issue", number, "--on", WHEN)
    found = call(shop, "counter", "lost", "41000", "left on a bus", "--on", LATER)
    assert "written off" in found.output


def test_a_statement_prints_with_a_place_to_sign(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    call(shop, "counter", "issue", number, "--on", WHEN)
    found = call(shop, "counter", "statement", "kmaki", "--on", WHEN)
    assert "Kirsi Maki" in found.output
    assert "signed" in found.output


def test_the_deposit_statement_prints(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    call(shop, "counter", "issue", number, "--on", WHEN)
    found = call(shop, "counter", "deposits", "kmaki")
    assert "held $25.00" in found.output


def test_the_department_summary_prints(shop):
    found = call(shop, "counter", "department", "geography", "--on", WHEN)
    assert "geography: 4 on the books" in found.output


def test_the_chase_list_is_quiet_when_nothing_is_late(shop):
    found = call(shop, "counter", "overdue", "--on", WHEN)
    assert "nothing overdue" in found.output


def test_the_letters_run_is_quiet_when_nothing_is_late(shop):
    found = call(shop, "counter", "letters", "--on", WHEN)
    assert "no letters" in found.output


def test_the_letters_run_reports_what_would_go_out(shop):
    with_series(shop)
    number = a_request(shop)
    call(shop, "counter", "approve", number, "rvirta", "--on", WHEN)
    call(shop, "counter", "issue", number, "--on", WHEN, "--due", LATER)
    found = call(shop, "counter", "letters", "--on", "2025-12-01")
    assert "letters" in found.output

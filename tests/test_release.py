import pytest
from click.testing import CliRunner

import hasplib
from hasplib.cli.main import cli
from hasplib.errors import NotFoundError
from hasplib.release import (
    CURRENT,
    RELEASES,
    describe_current,
    every_migration,
    history_sheet,
    matches_package,
    migrations_between,
    notes_between,
    release,
    unreleased,
    upgrade_sheet,
    versions,
)


def call(*args):
    return CliRunner().invoke(cli, ["--database", "sqlite+pysqlite:///:memory:", *args])


def test_the_package_version_is_the_latest_release():
    assert matches_package(hasplib.__version__)
    assert CURRENT.version == hasplib.__version__


def test_the_releases_are_oldest_first():
    parts = [item.parts for item in RELEASES]
    assert parts == sorted(parts)


def test_no_version_is_released_twice():
    assert len(versions()) == len(set(versions()))


def test_the_dates_only_move_forward():
    days = [item.released_on for item in RELEASES]
    assert days == sorted(days)


def test_every_migration_is_shipped_exactly_once():
    found = every_migration()
    assert len(found) == len(set(found))


def test_the_migrations_are_shipped_in_order():
    assert list(every_migration()) == sorted(every_migration())


def test_every_migration_on_disk_is_in_a_release():
    from pathlib import Path

    import hasplib.persistence as persistence

    root = Path(persistence.__file__).parents[3] / "migrations" / "versions"
    on_disk = {path.name.split("_")[0] for path in root.glob("0*.py")}
    assert on_disk == set(every_migration())


def test_a_release_reads_back_as_one_line():
    line = release("1.0.0").describe()
    assert line.startswith("1.0.0")
    assert "the interface is settled" in line


def test_a_version_nobody_released_is_a_404():
    with pytest.raises(NotFoundError):
        release("0.0.1")


def test_an_upgrade_crosses_the_migrations_in_between():
    assert migrations_between("0.6.0", "0.8.0") == (
        "0005",
        "0006",
        "0007",
        "0008",
        "0009",
    )


def test_an_upgrade_to_the_same_version_crosses_nothing():
    assert migrations_between("0.8.0", "0.8.0") == ()


def test_a_downgrade_is_refused():
    with pytest.raises(NotFoundError):
        migrations_between("0.8.0", "0.6.0")


def test_the_notes_come_with_the_version_that_carries_them():
    found = notes_between("0.6.0", "0.7.0")
    assert found[0].startswith("0.7.0: ")


def test_an_upgrade_with_nothing_to_read_says_nothing():
    assert notes_between("0.2.0", "0.3.0") == ()


def test_the_history_counts_the_releases():
    lines = history_sheet()
    assert lines[0] == f"{len(RELEASES)} releases, {CURRENT.version} is current"
    assert len(lines) == len(RELEASES) + 1


def test_the_upgrade_sheet_names_the_migrations():
    lines = upgrade_sheet("0.6.0", "0.7.0")
    assert lines[0] == "0.6.0 to 0.7.0"
    assert lines[1] == "2 migrations: 0005, 0006"


def test_the_upgrade_sheet_defaults_to_current():
    assert upgrade_sheet("0.9.0")[0] == f"0.9.0 to {CURRENT.version}"


def test_an_upgrade_with_no_migrations_says_so():
    assert upgrade_sheet("0.9.0", "1.0.0")[1] == "no migrations to run"


def test_the_upgrade_sheet_carries_the_notes():
    lines = upgrade_sheet("0.6.0", "0.7.0")
    assert "read first" in lines


def test_what_a_version_is_behind_by():
    assert unreleased("0.9.0") == ("1.0.0",)


def test_the_latest_version_is_behind_nothing():
    assert unreleased(CURRENT.version) == ()


def test_the_current_release_describes_itself():
    lines = describe_current()
    assert lines[0].startswith(CURRENT.version)
    assert len(lines) == len(CURRENT.notes) + 1


def test_the_version_command_prints_the_headline():
    lines = call("release", "version").output.splitlines()
    assert lines[0].startswith(hasplib.__version__)


def test_the_history_command_prints_every_release():
    lines = call("release", "history").output.splitlines()
    assert len(lines) == len(RELEASES) + 1


def test_the_upgrade_command_names_the_migrations():
    output = call("release", "upgrade", "0.6.0", "--to", "0.7.0").output
    assert "2 migrations: 0005, 0006" in output


def test_the_upgrade_command_defaults_to_current():
    assert f"to {CURRENT.version}" in call("release", "upgrade", "0.9.0").output


def test_the_upgrade_command_refuses_a_downgrade():
    assert call("release", "upgrade", "1.0.0", "--to", "0.6.0").exit_code != 0


def test_the_migrations_command_lists_them_all():
    lines = call("release", "migrations").output.splitlines()
    assert lines == list(every_migration())

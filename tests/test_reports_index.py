import pytest
from click.testing import CliRunner

from hasplib.cli.main import cli
from hasplib.errors import NotFoundError
from hasplib.reports.index import (
    MODULES,
    PAGES,
    every_module,
    for_module,
    for_reader,
    index_sheet,
    names,
    page,
    reader_sheet,
    readers,
    undocumented,
)


def call(*args):
    return CliRunner().invoke(cli, ["--database", "sqlite+pysqlite:///:memory:", *args])


def test_every_page_on_the_index_is_a_real_function():
    for item in PAGES:
        assert callable(item.resolve()), item.path


def test_every_report_function_is_on_the_index():
    for module in MODULES:
        assert undocumented(module) == (), module


def test_no_page_is_listed_twice():
    found = [item.name for item in PAGES]
    assert len(found) == len(set(found))


def test_every_page_names_a_module_that_exists():
    for item in PAGES:
        assert item.module in MODULES, item.name


def test_every_module_prints_something():
    for module in MODULES:
        assert for_module(module), module


def test_a_page_knows_where_it_lives():
    assert page("survey_report").path == "hasplib.reports.survey.survey_report"


def test_a_page_nobody_prints_is_a_404():
    with pytest.raises(NotFoundError):
        page("payroll_summary")


def test_the_names_come_back_sorted():
    assert names() == tuple(sorted(names()))


def test_a_reader_gets_their_own_pages():
    found = for_reader("bench")
    assert {item.name for item in found} >= {"cylinder_chart", "bitting_list"}


def test_the_readers_are_listed_once_each():
    assert readers() == tuple(sorted(set(readers())))


def test_a_page_reads_back_as_one_line():
    line = page("cylinder_chart").describe()
    assert line.startswith("cylinder_chart")
    assert "bench" in line


def test_the_index_counts_every_page():
    lines = index_sheet()
    assert lines[0] == f"{len(PAGES)} reports"


def test_the_index_groups_by_module():
    lines = index_sheet()
    assert "annual" in lines
    assert "survey" in lines


def test_the_index_can_be_narrowed_to_one_module():
    lines = index_sheet("stock")
    assert lines[0] == f"{len(for_module('stock'))} reports"


def test_a_module_that_prints_nothing_says_so():
    assert index_sheet("payroll") == ("nothing is printed out of payroll",)


def test_a_reader_sheet_counts_their_pages():
    lines = reader_sheet("bench")
    assert lines[0].startswith(f"{len(for_reader('bench'))} reports for bench")


def test_a_reader_nobody_serves_says_so():
    assert reader_sheet("the dean") == ("nobody described as the dean asks for anything",)


def test_the_modules_come_back_in_order():
    assert every_module() == MODULES
    assert list(MODULES) == sorted(MODULES)


def test_the_bare_command_prints_the_whole_index():
    lines = call("reports").output.splitlines()
    assert lines[0] == f"{len(PAGES)} reports"


def test_the_module_command_narrows_it():
    lines = call("reports", "module", "stock").output.splitlines()
    assert lines[0] == f"{len(for_module('stock'))} reports"


def test_the_modules_command_lists_them():
    assert call("reports", "modules").output.splitlines() == list(MODULES)


def test_the_for_command_lists_one_readers_pages():
    assert "cylinder_chart" in call("reports", "for", "bench").output


def test_the_readers_command_lists_them():
    assert "bench" in call("reports", "readers").output.splitlines()


def test_the_find_command_says_where_a_page_lives():
    lines = call("reports", "find", "survey_report").output.splitlines()
    assert lines[0] == "hasplib.reports.survey.survey_report"
    assert "asked for by management" in lines[1]


def test_finding_a_page_nobody_prints_is_refused():
    assert call("reports", "find", "payroll_summary").exit_code != 0


def test_nothing_is_unlisted():
    found = call("reports", "unlisted")
    assert found.output.strip() == "every report is on the index"
    assert found.exit_code == 0

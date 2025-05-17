from datetime import date

from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger
from hasplib.control.notices import NoticeLog, notices_due
from hasplib.reports.control import (
    department_summary,
    deposit_statement,
    key_line,
    keyholder_statement,
    notice_run,
    overdue_report,
)
from hasplib.reports.page import as_text
from people import TODAY, a_directory

DUE = date(2025, 5, 1)
LATE = date(2025, 7, 1)


def a_ledger(directory) -> KeyLedger:
    ledger = KeyLedger(directory)
    ledger.issue("41207", "AA1", "kmaki", TODAY, due_on=DUE, deposit_cents=2500)
    ledger.issue("41208", "AA", "kmaki", TODAY, deposit_cents=5000)
    ledger.issue("41209", "AA1", "dsalo", TODAY, deposit_cents=2500)
    return ledger


def a_book(ledger) -> DepositBook:
    book = DepositBook()
    for key in ledger.out():
        book.charge(key.tag, key.keyholder, key.deposit_cents, key.issued_on)
    return book


def test_a_key_line_carries_the_tag_the_cut_and_the_dates():
    ledger = a_ledger(a_directory())
    line = key_line(ledger.key("41207"))
    assert "41207" in line
    assert "AA1" in line
    assert DUE.isoformat() in line


def test_a_key_with_no_due_date_leaves_the_column_blank():
    ledger = a_ledger(a_directory())
    assert DUE.isoformat() not in key_line(ledger.key("41208"))


def test_a_line_shows_the_deposit_when_the_book_is_given():
    ledger = a_ledger(a_directory())
    assert "$25.00" in key_line(ledger.key("41207"), a_book(ledger))


def test_a_statement_lists_every_key_and_a_place_to_sign():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = keyholder_statement(directory.person("kmaki"), ledger, a_book(ledger))
    assert lines[0].startswith("Kirsi Maki (kmaki)")
    assert any("41207" in line for line in lines)
    assert "2 keys" in lines
    assert lines[-1].startswith("signed")


def test_a_statement_totals_the_deposits():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = keyholder_statement(directory.person("kmaki"), ledger, a_book(ledger))
    assert "deposits held $75.00" in lines


def test_a_statement_can_be_dated():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = keyholder_statement(directory.person("kmaki"), ledger, on=TODAY)
    assert lines[1] == f"as at {TODAY.isoformat()}"


def test_somebody_holding_nothing_still_gets_a_statement():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = keyholder_statement(directory.person("phamm"), ledger)
    assert "0 keys" in lines


def test_a_department_summary_counts_keys_per_person():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = department_summary(directory, ledger, "geography", TODAY)
    assert lines[0].startswith("geography: 4 on the books")
    assert lines[-1] == "3 keys out to this department"


def test_a_summary_flags_somebody_who_may_not_hold_keys():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    ledger = KeyLedger(directory)
    lines = department_summary(directory, ledger, "geography", TODAY)
    assert any("[separated]" in line for line in lines)


def test_a_lapsed_contractor_is_flagged_too():
    directory = a_directory()
    ledger = KeyLedger(directory)
    lines = department_summary(directory, ledger, "facilities", TODAY)
    assert any("[active]" in line for line in lines)


def test_the_chase_list_is_empty_when_nothing_is_late():
    directory = a_directory()
    ledger = a_ledger(directory)
    assert overdue_report(ledger, TODAY)[0].startswith("nothing overdue")


def test_the_chase_list_names_the_days():
    directory = a_directory()
    ledger = a_ledger(directory)
    lines = overdue_report(ledger, LATE)
    assert lines[0].startswith("1 keys overdue")
    assert "61 days" in lines[1]


def test_a_quiet_letters_run_says_so():
    assert notice_run([])[0] == "no letters to send today"


def test_a_letters_run_lists_what_goes_out():
    directory = a_directory()
    ledger = a_ledger(directory)
    due = notices_due(ledger, LATE, NoticeLog())
    lines = notice_run(due)
    assert lines[0] == "1 letters"
    assert "41207" in lines[1]


def test_a_letters_run_names_the_holds():
    directory = a_directory()
    ledger = a_ledger(directory)
    due = notices_due(ledger, date(2025, 12, 1), NoticeLog())
    assert any(line.startswith("holds to place") for line in notice_run(due))


def test_a_deposit_statement_shows_every_line_and_the_balance():
    directory = a_directory()
    ledger = a_ledger(directory)
    book = a_book(ledger)
    book.refund("41207", LATE)
    lines = deposit_statement(book, "kmaki")
    assert lines[0] == "deposits for kmaki"
    assert any("-$25.00" in line for line in lines)
    assert lines[-1] == "held $50.00"


def test_somebody_with_no_deposits_gets_an_empty_statement():
    lines = deposit_statement(DepositBook(), "phamm")
    assert lines == ("deposits for phamm", "held $0.00")


def test_lines_join_into_a_page():
    directory = a_directory()
    ledger = a_ledger(directory)
    page = as_text(keyholder_statement(directory.person("kmaki"), ledger))
    assert page.startswith("Kirsi Maki")

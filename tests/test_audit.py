from datetime import date

from hasplib.control.audit import (
    Sighting,
    check_deposits,
    holdings_by_department,
    reconcile,
)
from hasplib.control.deposits import DepositBook
from hasplib.control.issue import KeyLedger
from people import TODAY, a_directory

WALK = date(2025, 9, 30)


def a_ledger() -> KeyLedger:
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY, deposit_cents=2500)
    ledger.issue("41208", "AA1", "dsalo", TODAY, deposit_cents=2500)
    ledger.issue("41209", "AA", "rvirta", TODAY, deposit_cents=5000)
    ledger.issue("41301", "B1", "phamm", TODAY, deposit_cents=2500)
    return ledger


def a_book(ledger: KeyLedger) -> DepositBook:
    book = DepositBook()
    for key in ledger.out():
        if key.deposit_cents:
            book.charge(key.tag, key.keyholder, key.deposit_cents, key.issued_on)
    return book


def everything_seen():
    return [
        Sighting("41207", "kmaki"),
        Sighting("41208", "dsalo"),
        Sighting("41209", "rvirta"),
    ]


def test_a_department_that_produces_every_key_is_clean():
    report = reconcile(a_ledger(), "geography", everything_seen(), WALK)
    assert report.clean
    assert report.counted == 3
    assert report.expected == 3
    assert "3 counted" in report.summary()


def test_a_key_nobody_produced_is_unaccounted_for():
    seen = [s for s in everything_seen() if s.tag != "41208"]
    report = reconcile(a_ledger(), "geography", seen, WALK)
    assert [f.tag for f in report.unaccounted] == ["41208"]
    assert report.unaccounted[0].expected == "dsalo"
    assert "nobody produced it" in report.unaccounted[0].describe()


def test_a_tag_that_is_not_in_the_ledger_is_a_stray():
    seen = [*everything_seen(), Sighting("99999", "kmaki")]
    report = reconcile(a_ledger(), "geography", seen, WALK)
    findings = report.of_kind("stray")
    assert [f.tag for f in findings] == ["99999"]
    assert "not in the ledger" in findings[0].describe()


def test_a_key_the_ledger_closed_is_its_own_finding():
    ledger = a_ledger()
    ledger.take_back("41208", WALK)
    report = reconcile(ledger, "geography", everything_seen(), WALK)
    findings = report.of_kind("closed")
    assert [f.tag for f in findings] == ["41208"]
    assert "on paper" in findings[0].describe()


def test_a_key_in_the_wrong_hands_names_both_people():
    seen = [Sighting("41207", "dsalo"), Sighting("41208", "dsalo"), Sighting("41209", "rvirta")]
    report = reconcile(a_ledger(), "geography", seen, WALK)
    findings = report.of_kind("wrong_holder")
    assert findings[0].expected == "kmaki"
    assert findings[0].seen == "dsalo"


def test_a_key_from_another_department_turns_up_as_a_wrong_holder():
    seen = [*everything_seen(), Sighting("41301", "kmaki")]
    report = reconcile(a_ledger(), "geography", seen, WALK)
    findings = report.of_kind("wrong_holder")
    assert [f.tag for f in findings] == ["41301"]
    assert findings[0].expected == "phamm"


def test_a_department_that_produced_nothing_is_all_unaccounted():
    report = reconcile(a_ledger(), "geography", [], WALK)
    assert len(report.unaccounted) == 3
    assert report.counted == 0


def test_a_lost_key_is_not_expected_on_the_table():
    ledger = a_ledger()
    ledger.report_lost("41208", WALK)
    seen = [s for s in everything_seen() if s.tag != "41208"]
    report = reconcile(ledger, "geography", seen, WALK)
    assert report.clean


def test_the_deposit_book_and_the_ledger_agree_when_nothing_has_moved():
    ledger = a_ledger()
    check = check_deposits(ledger, a_book(ledger))
    assert check.balances
    assert check.difference == 0


def test_a_key_returned_without_a_refund_shows_up_as_an_orphan():
    ledger = a_ledger()
    book = a_book(ledger)
    ledger.take_back("41207", WALK)
    check = check_deposits(ledger, book)
    assert check.deposits_without_keys == ("41207",)
    assert not check.balances
    assert check.difference == -2500


def test_a_key_issued_without_a_deposit_line_shows_up_the_other_way():
    ledger = a_ledger()
    book = a_book(ledger)
    ledger.issue("41210", "AA1", "kmaki", TODAY, deposit_cents=2500)
    check = check_deposits(ledger, book)
    assert check.tags_without_deposits == ("41210",)
    assert check.difference == 2500


def test_a_key_with_no_deposit_is_not_expected_in_the_book():
    ledger = a_ledger()
    book = a_book(ledger)
    ledger.issue("41211", "AA1", "kmaki", TODAY)
    check = check_deposits(ledger, book)
    assert check.tags_without_deposits == ()
    assert check.balances


def test_holdings_are_counted_per_department_biggest_first():
    counts = holdings_by_department(a_ledger(), ["geography", "facilities"])
    assert list(counts) == ["geography", "facilities"]
    assert counts["geography"] == 3
    assert counts["facilities"] == 1


def test_a_department_holding_nothing_still_appears():
    ledger = KeyLedger(a_directory())
    counts = holdings_by_department(ledger, ["geography"])
    assert counts["geography"] == 0

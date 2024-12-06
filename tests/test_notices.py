from datetime import date, timedelta

import pytest

from hasplib.control.issue import KeyLedger
from hasplib.control.notices import (
    NoticeLevel,
    NoticeLog,
    NoticePolicy,
    holds_to_place,
    notice_for,
    notices_due,
    write_offs,
)
from hasplib.errors import ConflictError, InputError
from people import TODAY, a_directory

DUE = date(2025, 3, 20)


def a_ledger() -> KeyLedger:
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY, due_on=DUE)
    ledger.issue("41208", "AA1", "dsalo", TODAY, due_on=DUE)
    ledger.issue("41209", "AA", "rvirta", TODAY)
    return ledger


def days_after(days: int) -> date:
    return DUE + timedelta(days=days)


def test_the_steps_have_to_get_later():
    with pytest.raises(ConflictError):
        NoticePolicy(reminder_days=30, first_days=14)


def test_two_steps_cannot_land_on_the_same_day():
    with pytest.raises(ConflictError):
        NoticePolicy(first_days=14, second_days=14)


def test_a_negative_threshold_is_refused():
    with pytest.raises(InputError):
        NoticePolicy(reminder_days=-1)


@pytest.mark.parametrize(
    ("days", "level"),
    [
        (0, None),
        (2, None),
        (3, NoticeLevel.REMINDER),
        (13, NoticeLevel.REMINDER),
        (14, NoticeLevel.FIRST),
        (30, NoticeLevel.SECOND),
        (60, NoticeLevel.HOLD),
        (400, NoticeLevel.WRITTEN_OFF),
    ],
)
def test_the_policy_reads_days_into_a_step(days, level):
    assert NoticePolicy().level_for(days) is level


def test_a_key_that_is_not_late_owes_nothing():
    ledger = a_ledger()
    assert notice_for(ledger.key("41207"), DUE, NoticeLog()) is None


def test_a_key_a_few_days_late_owes_a_reminder():
    ledger = a_ledger()
    notice = notice_for(ledger.key("41207"), days_after(4), NoticeLog())
    assert notice is not None
    assert notice.level is NoticeLevel.REMINDER
    assert notice.days_late == 4
    assert "reminder" in notice.describe()


def test_a_key_with_no_due_date_owes_nothing_ever():
    ledger = a_ledger()
    assert notice_for(ledger.key("41209"), date(2030, 1, 1), NoticeLog()) is None


def test_the_same_step_does_not_go_out_twice():
    ledger = a_ledger()
    log = NoticeLog()
    first = notice_for(ledger.key("41207"), days_after(4), log)
    log.record(first)
    assert notice_for(ledger.key("41207"), days_after(6), log) is None


def test_the_next_step_does_go_out():
    ledger = a_ledger()
    log = NoticeLog()
    log.record(notice_for(ledger.key("41207"), days_after(4), log))
    later = notice_for(ledger.key("41207"), days_after(20), log)
    assert later.level is NoticeLevel.FIRST


def test_skipping_straight_to_a_late_step_is_allowed():
    ledger = a_ledger()
    notice = notice_for(ledger.key("41207"), days_after(70), NoticeLog())
    assert notice.level is NoticeLevel.HOLD


def test_recording_the_same_notice_twice_is_refused():
    ledger = a_ledger()
    log = NoticeLog()
    notice = notice_for(ledger.key("41207"), days_after(4), log)
    log.record(notice)
    with pytest.raises(ConflictError):
        log.record(notice)


def test_the_log_remembers_what_went_out():
    ledger = a_ledger()
    log = NoticeLog()
    log.record(notice_for(ledger.key("41207"), days_after(4), log))
    assert log.sent_for("41207") == frozenset({NoticeLevel.REMINDER})
    assert log.highest_sent("41207") is NoticeLevel.REMINDER
    assert log.highest_sent("41208") is None
    assert len(log) == 1


def test_clearing_a_tag_starts_the_chase_over():
    ledger = a_ledger()
    log = NoticeLog()
    log.record(notice_for(ledger.key("41207"), days_after(4), log))
    log.clear("41207")
    assert log.highest_sent("41207") is None
    assert notice_for(ledger.key("41207"), days_after(6), log) is not None


def test_a_run_covers_every_late_key():
    ledger = a_ledger()
    due = notices_due(ledger, days_after(4), NoticeLog())
    assert {notice.tag for notice in due} == {"41207", "41208"}


def test_a_run_leaves_out_keys_that_are_not_late():
    assert notices_due(a_ledger(), DUE, NoticeLog()) == ()


def test_the_worst_letter_comes_first():
    ledger = a_ledger()
    log = NoticeLog()
    log.record(notice_for(ledger.key("41208"), days_after(4), log))
    log.record(notice_for(ledger.key("41208"), days_after(20), log))
    due = notices_due(ledger, days_after(40), log)
    assert [notice.tag for notice in due] == ["41207", "41208"]
    assert due[0].level is NoticeLevel.SECOND


def test_a_returned_key_drops_out_of_the_run():
    ledger = a_ledger()
    ledger.take_back("41207", days_after(1))
    due = notices_due(ledger, days_after(40), NoticeLog())
    assert [notice.tag for notice in due] == ["41208"]


def test_a_hold_is_placed_from_the_hold_step_up():
    ledger = a_ledger()
    due = notices_due(ledger, days_after(70), NoticeLog())
    assert holds_to_place(due) == ("dsalo", "kmaki")


def test_no_hold_before_the_hold_step():
    ledger = a_ledger()
    assert holds_to_place(notices_due(ledger, days_after(20), NoticeLog())) == ()


def test_a_written_off_key_goes_on_the_rekey_list():
    ledger = a_ledger()
    due = notices_due(ledger, days_after(200), NoticeLog())
    assert write_offs(due) == ("41207", "41208")
    assert holds_to_place(due) == ("dsalo", "kmaki")


def test_nothing_is_written_off_early():
    ledger = a_ledger()
    assert write_offs(notices_due(ledger, days_after(70), NoticeLog())) == ()


def test_a_shop_that_chases_harder_can_say_so():
    ledger = a_ledger()
    policy = NoticePolicy(reminder_days=1, first_days=2, second_days=3, hold_days=4)
    due = notices_due(ledger, days_after(5), NoticeLog(), policy)
    assert all(notice.level is NoticeLevel.HOLD for notice in due)

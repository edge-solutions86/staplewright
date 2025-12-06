from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.stamping import Marking, StampPolicy
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.models.stamping import BlindCodeRow
from hasplib.services.stamping import (
    BlindCodeRepository,
    StampingService,
    issued_symbols,
    keys_for_symbol,
    unstamped_out,
)

TODAY = date(2025, 6, 30)
CHANGE_KEY = KeySymbol.parse("AA1")
MASTER = KeySymbol.parse("AA")
TOP = KeySymbol.parse("GGM")


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session, policy: StampPolicy | None = None) -> StampingService:
    return StampingService(session, policy)


def test_an_empty_book_starts_at_the_series_start(session):
    service = a_service(session)
    assert service.assign(MASTER, TODAY).code == "4000"


def test_a_symbol_keeps_the_code_it_was_given(session):
    service = a_service(session)
    first = service.assign(MASTER, TODAY).code
    assert service.assign(MASTER, TODAY).code == first


def test_the_next_symbol_takes_the_next_code(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    assert service.assign(TOP, TODAY).code == "4007"


def test_the_book_survives_a_reload(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    fresh = a_service(session)
    assert fresh.assign(TOP, TODAY).code == "4007"


def test_a_code_carries_who_assigned_it(session):
    row = a_service(session).assign(MASTER, TODAY, "rvirta")
    assert row.assigned_by == "rvirta"
    assert row.assigned_on == TODAY


def test_the_code_for_a_symbol_comes_back(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    assert service.code_for(MASTER) == "4000"


def test_a_symbol_nobody_stamped_has_no_code(session):
    with pytest.raises(NotFoundError):
        a_service(session).code_for(MASTER)


def test_a_number_on_a_key_reads_back_as_a_symbol(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    assert service.identify("4000") == "AA"


def test_a_number_with_space_round_it_still_reads(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    assert service.identify("  4000 ") == "AA"


def test_a_number_nobody_stamped_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).identify("9999")


def test_the_whole_book_comes_back_when_asked_by_name(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    service.assign(TOP, TODAY)
    assert service.the_whole_book() == (("AA", "4000"), ("GGM", "4007"))


def test_stamping_a_change_key_needs_no_code(session):
    service = a_service(session)
    found = service.stamp_key("41207", TODAY)
    assert found.text == "AA1"
    assert service.the_whole_book() == ()


def test_stamping_a_master_assigns_a_code(session):
    service = a_service(session)
    found = service.stamp_key("41209", TODAY)
    assert found.is_blind
    assert service.identify(found.text) == "AA"


def test_a_key_nobody_issued_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).stamp_key("99999", TODAY)


def test_a_batch_comes_back_in_tag_order(session):
    found = a_service(session).stamp_batch(["41211", "41207"], TODAY)
    assert [stamp.tag for stamp in found] == ["41207", "41211"]


def test_two_keys_to_one_symbol_share_a_code(session):
    service = a_service(session)
    found = service.stamp_batch(["41207", "41208"], TODAY)
    assert found[0].text == found[1].text


def test_a_batch_with_a_missing_key_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).stamp_batch(["41207", "99999"], TODAY)


def test_the_sheet_counts_the_run(session):
    lines = a_service(session).sheet(["41207", "41211"], TODAY)
    assert lines[0] == "2 keys to stamp"


def test_the_sheet_says_how_many_are_blind(session):
    lines = a_service(session).sheet(["41207", "41211"], TODAY)
    assert lines[-1].startswith("1 of them are blind")


def test_everything_out_covers_every_issued_key(session):
    found = a_service(session).everything_out(TODAY)
    assert len(found) == 5


def test_a_policy_that_stamps_nothing_leaves_keys_blank(session):
    policy = StampPolicy(change_keys=Marking.NOTHING, masters=Marking.NOTHING)
    assert len(a_service(session, policy).blank_keys(TODAY)) == 5


def test_the_default_policy_leaves_nothing_blank(session):
    assert a_service(session).blank_keys(TODAY) == ()


def test_a_code_cannot_be_retired_while_keys_carry_it(session):
    service = a_service(session)
    service.assign(MASTER, TODAY)
    with pytest.raises(ConflictError) as caught:
        service.retire_code(MASTER)
    assert caught.value.facts["keys"] == 1


def test_a_code_nobody_carries_can_be_retired(session):
    service = a_service(session)
    service.assign(KeySymbol.parse("AB1"), TODAY)
    service.retire_code(KeySymbol.parse("AB1"))
    assert service.the_whole_book() == ()


def test_retiring_a_code_nobody_has_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).retire_code(MASTER)


def test_the_repository_finds_a_row_by_its_code(session):
    a_service(session).assign(MASTER, TODAY)
    assert BlindCodeRepository(session).by_code("4000").symbol == "AA"


def test_a_code_nobody_assigned_finds_nothing(session):
    assert BlindCodeRepository(session).by_code("9999") is None


def test_one_code_cannot_stand_for_two_symbols(session):
    session.add(BlindCodeRow(symbol="AA", code="4000", assigned_on=TODAY))
    session.flush()
    session.add(BlindCodeRow(symbol="AB", code="4000", assigned_on=TODAY))
    with pytest.raises(IntegrityError):
        session.flush()


def test_an_empty_code_is_refused(session):
    session.add(BlindCodeRow(symbol="AA", code="", assigned_on=TODAY))
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_keys_cut_to_a_symbol_come_back(session):
    assert keys_for_symbol(session, CHANGE_KEY) == ("41207", "41208")


def test_a_symbol_nobody_holds_has_no_keys(session):
    assert keys_for_symbol(session, KeySymbol.parse("AB1")) == ()


def test_the_symbols_out_are_the_ones_needing_a_code(session):
    assert issued_symbols(session) == ("A", "AA", "AA1", "GGM")


def test_a_policy_that_stamps_nothing_is_visible_without_the_book(session):
    policy = StampPolicy(change_keys=Marking.NOTHING)
    assert unstamped_out(session, policy) == ("41207", "41208")


def test_the_default_policy_leaves_nothing_unstamped(session):
    assert unstamped_out(session, StampPolicy()) == ()

from datetime import date

import pytest

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.control.escrow import BoxPolicy, BoxState, OpenedBy
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.services.escrow import BoxRepository, EscrowService

SEALED = date(2025, 1, 6)
WALKED = date(2025, 6, 30)
LATE = date(2025, 12, 1)
CHANGE_KEY = KeySymbol.parse("AA1")
MASTER = KeySymbol.parse("A")


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def installed(session, policy: BoxPolicy | None = None) -> EscrowService:
    service = EscrowService(session, policy)
    service.install("KB-01", "MOO", "MOO-101", CHANGE_KEY, "44012", SEALED)
    return service


def test_a_box_goes_up_and_reads_back(session):
    service = installed(session)
    assert service.box("KB-01").state is BoxState.SEALED


def test_a_box_going_up_opens_its_first_seal(session):
    service = installed(session)
    assert service.seal_now("KB-01").seal == "44012"


def test_a_box_on_a_building_nobody_has_is_refused(session):
    service = EscrowService(session)
    with pytest.raises(NotFoundError):
        service.install("KB-01", "ZZZ", "MOO-101", CHANGE_KEY, "44012", SEALED)


def test_a_box_on_a_door_nobody_has_is_refused(session):
    service = EscrowService(session)
    with pytest.raises(NotFoundError):
        service.install("KB-01", "MOO", "MOO-999", CHANGE_KEY, "44012", SEALED)


def test_a_door_on_another_building_is_refused(session):
    service = EscrowService(session)
    with pytest.raises(ConflictError) as caught:
        service.install("KB-01", "VOS", "MOO-101", CHANGE_KEY, "44012", SEALED)
    assert caught.value.facts["actually"] == "MOO"


def test_a_second_box_on_one_door_is_refused(session):
    service = installed(session)
    with pytest.raises(ConflictError):
        service.install("KB-02", "MOO", "MOO-101", CHANGE_KEY, "44013", SEALED)


def test_a_symbol_nobody_cut_is_refused(session):
    service = EscrowService(session)
    with pytest.raises(ConflictError) as caught:
        service.install("KB-01", "MOO", "MOO-101", KeySymbol.parse("ZZ9"), "44012", SEALED)
    assert caught.value.facts["symbol"] == "ZZ9"


def test_a_master_is_still_too_wide_for_a_wall(session):
    service = EscrowService(session)
    with pytest.raises(ConflictError):
        service.install("KB-01", "MOO", "MOO-101", MASTER, "44012", SEALED)


def test_a_shop_that_allows_masters_can_put_one_up(session):
    service = EscrowService(session, BoxPolicy(lowest_level=1))
    row = service.install("KB-01", "MOO", "MOO-101", MASTER, "44012", SEALED)
    assert row.symbol.render() == "A"


def test_a_box_nobody_installed_is_a_404(session):
    with pytest.raises(NotFoundError):
        EscrowService(session).box("KB-99")


def test_reading_the_right_seal_only_records_the_walk(session):
    service = installed(session)
    row = service.check("KB-01", "44012", WALKED)
    assert row.state == "sealed"
    assert row.checked_on == WALKED
    assert service.seal_now("KB-01").is_current


def test_a_seal_that_does_not_match_opens_the_box(session):
    service = installed(session)
    row = service.check("KB-01", "44099", WALKED)
    assert row.state == "opened"
    assert row.opened_by == "nobody signed for it"


def test_a_mismatched_seal_closes_the_seal_row(session):
    service = installed(session)
    service.check("KB-01", "44099", WALKED)
    assert service.seal_now("KB-01") is None
    assert service.history("KB-01")[0].broken_on == WALKED


def test_reporting_an_opening_closes_the_seal_too(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED, "kitchen fire")
    held = service.history("KB-01")[0]
    assert held.broken_on == WALKED
    assert held.broken_by == "fire service"


def test_an_open_box_can_be_found_empty(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    row = service.empty("KB-01", LATE, "no key in it")
    assert row.state == "empty"


def test_resealing_opens_a_new_seal_and_leaves_the_old_one_closed(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    service.reseal("KB-01", "44120", CHANGE_KEY, LATE)
    found = service.history("KB-01")
    assert [row.seal for row in found] == ["44012", "44120"]
    assert found[0].broken_on == WALKED
    assert found[1].is_current


def test_resealing_moves_the_box_row_back_to_sealed(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    row = service.reseal("KB-01", "44120", CHANGE_KEY, LATE)
    assert row.state == "sealed"
    assert row.seal == "44120"
    assert row.opened_on is None


def test_resealing_onto_a_symbol_nobody_cut_is_refused(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    with pytest.raises(ConflictError):
        service.reseal("KB-01", "44120", KeySymbol.parse("ZZ9"), LATE)


def test_taking_a_box_down_closes_its_seal(session):
    service = installed(session)
    row = service.withdraw("KB-01", LATE, "building came down")
    assert row.state == "withdrawn"
    assert service.seal_now("KB-01") is None


def test_the_key_in_the_box_on_a_day_comes_off_the_history(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    service.reseal("KB-01", "44120", KeySymbol.parse("AA2"), LATE)
    assert service.key_on("KB-01", date(2025, 3, 1)).render() == "AA1"
    assert service.key_on("KB-01", date(2025, 12, 2)).render() == "AA2"


def test_a_day_before_the_box_went_up_has_no_key(session):
    service = installed(session)
    assert service.key_on("KB-01", date(2024, 1, 1)) is None


def test_the_open_boxes_come_off_the_register(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert [box.number for box in service.open_boxes()] == ["KB-01"]


def test_a_box_is_due_a_check_after_the_window(session):
    service = installed(session)
    assert [box.number for box in service.due_for_check(date(2025, 7, 6))] == ["KB-01"]


def test_a_box_open_too_long_is_overdue_a_reseal(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert [box.number for box in service.overdue_reseal(date(2025, 7, 5))] == ["KB-01"]


def test_the_walk_round_leads_with_the_open_boxes(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert service.walk_round(LATE)[0].number == "KB-01"


def test_a_building_with_no_box_is_named(session):
    service = installed(session)
    assert "VOS" in service.uncovered()


def test_a_building_with_a_box_is_not(session):
    service = installed(session)
    assert "MOO" not in service.uncovered()


def test_the_exposure_counts_open_boxes_per_building(session):
    service = installed(session)
    service.report_opened("KB-01", OpenedBy.FIRE, WALKED)
    assert service.exposure() == {"MOO": 1}


def test_the_repository_lists_a_building(session):
    installed(session)
    found = BoxRepository(session).in_building("MOO")
    assert [row.number for row in found] == ["KB-01"]


def test_the_repository_leaves_out_the_withdrawn(session):
    service = installed(session)
    service.withdraw("KB-01", LATE, "gone")
    assert BoxRepository(session).live() == []


def test_the_repository_finds_a_box_by_its_door(session):
    installed(session)
    assert BoxRepository(session).on_opening("MOO-101").number == "KB-01"


def test_a_door_with_no_box_finds_nothing(session):
    installed(session)
    assert BoxRepository(session).on_opening("MOO-110") is None

from datetime import date

import pytest

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.rekey.quarantine import Quarantine, RetirementReason

RETIRED_ON = date(2018, 6, 14)
TODAY = date(2025, 4, 8)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_quarantine() -> Quarantine:
    hold = Quarantine()
    hold.retire(b("342156"), RetirementReason.COMPROMISED, RETIRED_ON, "AA1")
    hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON, "AA2")
    hold.retire(b("382556"), RetirementReason.SUPERSEDED, date(2024, 1, 5), "AB1")
    hold.retire(b("302156"), RetirementReason.DAMAGED, RETIRED_ON)
    return hold


def test_a_retired_cut_is_refused():
    hold = a_quarantine()
    assert hold.is_retired(b("342156"))
    assert b("342156") in hold
    assert not hold.is_retired(b("999999"))


def test_a_cut_nobody_retired_is_not_in_the_quarantine():
    assert b("999999") not in a_quarantine()
    assert "342156" not in a_quarantine()


def test_a_compromised_cut_never_comes_back():
    hold = a_quarantine()
    assert hold.is_retired(b("342156"), date(2099, 1, 1))
    with pytest.raises(ConflictError):
        hold.release(b("342156"))


def test_a_superseded_cut_comes_back_after_the_window():
    hold = a_quarantine()
    assert hold.is_retired(b("362356"), date(2024, 6, 13))
    assert not hold.is_retired(b("362356"), date(2025, 6, 14))


def test_the_day_before_the_window_closes_is_still_retired():
    hold = a_quarantine()
    assert hold.is_retired(b("362356"), date(2025, 6, 13))


def test_a_shop_with_a_shorter_window_releases_sooner():
    hold = Quarantine(release_after_years=3)
    hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON)
    assert not hold.is_retired(b("362356"), TODAY)


def test_a_window_of_nothing_is_refused():
    with pytest.raises(ValueError):
        Quarantine(release_after_years=0)


def test_asking_without_a_date_treats_everything_as_retired():
    hold = a_quarantine()
    assert hold.is_retired(b("362356"))


def test_a_superseded_cut_can_be_released_by_hand():
    hold = a_quarantine()
    released = hold.release(b("362356"))
    assert released.symbol == "AA2"
    assert b("362356") not in hold


def test_releasing_something_that_was_never_retired():
    with pytest.raises(NotFoundError):
        a_quarantine().release(b("999999"))


def test_a_cut_cannot_be_retired_twice_for_the_same_kind_of_reason():
    hold = a_quarantine()
    with pytest.raises(ConflictError):
        hold.retire(b("362356"), RetirementReason.SUPERSEDED, TODAY)


def test_a_compromise_overwrites_a_softer_retirement():
    hold = a_quarantine()
    hold.retire(b("362356"), RetirementReason.COMPROMISED, TODAY, note="turned up on ebay")
    assert hold.entry(b("362356")).is_permanent
    assert hold.is_retired(b("362356"), date(2099, 1, 1))


def test_a_compromised_cut_cannot_be_softened():
    hold = a_quarantine()
    with pytest.raises(ConflictError):
        hold.retire(b("342156"), RetirementReason.SUPERSEDED, TODAY)


def test_asking_about_a_cut_that_is_not_retired():
    with pytest.raises(NotFoundError):
        a_quarantine().entry(b("999999"))


def test_filtering_an_array_drops_the_retired_cuts():
    hold = a_quarantine()
    candidates = [b("342156"), b("362356"), b("442156")]
    assert hold.available(candidates) == (b("442156"),)


def test_filtering_with_a_date_lets_the_old_ones_through():
    hold = a_quarantine()
    candidates = [b("342156"), b("362356"), b("442156")]
    assert hold.available(candidates, date(2026, 1, 1)) == (b("362356"), b("442156"))


def test_the_permanent_list_is_only_the_compromised_ones():
    found = a_quarantine().permanent()
    assert [entry.bitting.render() for entry in found] == ["342156"]


def test_the_releasable_list_is_what_has_sat_out_its_window():
    found = a_quarantine().releasable(date(2026, 1, 1))
    assert [entry.bitting.render() for entry in found] == ["302156", "362356"]


def test_a_recent_retirement_is_not_releasable():
    assert a_quarantine().releasable(TODAY) == ()


def test_the_reasons_are_counted():
    counts = a_quarantine().by_reason()
    assert counts == {"compromised": 1, "damaged": 1, "superseded": 2}


def test_the_quarantine_counts_and_iterates_in_cut_order():
    hold = a_quarantine()
    assert len(hold) == 4
    assert [entry.bitting.render() for entry in hold] == [
        "302156",
        "342156",
        "362356",
        "382556",
    ]

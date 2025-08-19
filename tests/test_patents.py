from datetime import date

import pytest

from hasplib.errors import InputError, KeywayError
from hasplib.spec.keyway import KeywayFamily, KeywaySection
from hasplib.spec.patents import (
    Exposure,
    closing_sections,
    dates_in_order,
    expiry_of,
    exposed_below,
    exposure_of,
    exposures,
    migration_order,
    next_expiry,
    open_sections,
    patent_report,
    refuse_unsafe_section,
    section_is_safe,
    still_restricted,
    systems_at_risk,
)

TODAY = date(2025, 6, 30)


def a_family() -> KeywayFamily:
    family = KeywayFamily(
        "everest 29",
        KeywaySection("C", "Schlage", restricted=True, patent_expires="2031-04-01"),
    )
    family.add(KeywaySection("CE", "Schlage", restricted=True, patent_expires="2027-02-14"), "C")
    family.add(KeywaySection("CF", "Schlage", restricted=True, patent_expires="2023-11-30"), "C")
    family.add(KeywaySection("CEA", "Schlage"), "CE")
    family.add(KeywaySection("CEB", "Schlage", restricted=True), "CE")
    return family


def test_a_section_with_no_date_has_no_expiry():
    assert expiry_of(KeywaySection("CEA", "Schlage")) is None


def test_a_date_the_catalogue_printed_comes_back_as_a_day():
    section = KeywaySection("CE", "Schlage", restricted=True, patent_expires="2027-02-14")
    assert expiry_of(section) == date(2027, 2, 14)


def test_a_date_nobody_can_read_is_an_error():
    section = KeywaySection("CE", "Schlage", restricted=True, patent_expires="february")
    with pytest.raises(KeywayError) as caught:
        expiry_of(section)
    assert caught.value.facts["section"] == "CE"


def test_an_open_keyway_is_open_whatever_the_date():
    assert Exposure("CEA", False, None).is_open(TODAY)


def test_a_contract_only_restriction_never_goes_open_on_its_own():
    assert not Exposure("CEB", True, None).is_open(TODAY)


def test_a_patent_that_has_run_leaves_the_section_open():
    assert Exposure("CF", True, date(2023, 11, 30)).is_open(TODAY)


def test_a_patent_expiring_today_has_already_gone():
    assert Exposure("CF", True, TODAY).is_open(TODAY)


def test_the_days_left_count_down_to_the_date():
    assert Exposure("CE", True, date(2025, 7, 10)).days_left(TODAY) == 10


def test_a_section_with_no_date_counts_down_to_nothing():
    assert Exposure("CEB", True, None).days_left(TODAY) is None


def test_a_section_going_inside_the_horizon_is_closing():
    assert Exposure("CE", True, date(2027, 2, 14)).is_closing(TODAY)


def test_a_section_outside_the_horizon_is_not_closing_yet():
    assert not Exposure("C", True, date(2031, 4, 1)).is_closing(TODAY)


def test_a_section_already_open_is_not_closing_it_is_gone():
    assert not Exposure("CF", True, date(2023, 11, 30)).is_closing(TODAY)


def test_the_horizon_can_be_stretched():
    assert Exposure("C", True, date(2031, 4, 1)).is_closing(TODAY, within_years=10)


def test_a_horizon_that_runs_backwards_is_refused():
    with pytest.raises(InputError):
        Exposure("C", True, date(2031, 4, 1)).is_closing(TODAY, within_years=-1)


def test_a_horizon_crossing_a_leap_day_still_lands():
    leap = Exposure("C", True, date(2029, 2, 28))
    assert leap.is_closing(date(2024, 2, 29), within_years=5)


def test_the_exposures_come_back_soonest_first():
    found = [item.section for item in exposures(a_family())]
    assert found[:3] == ["CF", "CE", "C"]


def test_sections_with_no_date_sort_to_the_back():
    found = [item.section for item in exposures(a_family())]
    assert set(found[3:]) == {"CEA", "CEB"}


def test_the_open_sections_are_the_expired_and_the_unrestricted():
    assert open_sections(a_family(), TODAY) == ("CF", "CEA")


def test_the_closing_sections_are_the_ones_inside_the_horizon():
    assert closing_sections(a_family(), TODAY) == ("CE",)


def test_a_wider_horizon_pulls_in_more():
    assert closing_sections(a_family(), TODAY, within_years=10) == ("CE", "C")


def test_the_next_expiry_is_the_soonest_one_still_ahead():
    found = next_expiry(a_family(), TODAY)
    assert found.section == "CE"


def test_a_family_with_nothing_ahead_of_it_has_no_next():
    family = KeywayFamily("old", KeywaySection("A", "Best", True, "2001-01-01"))
    assert next_expiry(family, TODAY) is None


def test_what_is_still_restricted_comes_back_in_tree_order():
    assert still_restricted(a_family(), TODAY) == ("C", "CE", "CEB")


def test_a_section_with_years_left_is_safe_to_key_on():
    assert section_is_safe(a_family(), "C", TODAY)


def test_a_section_going_inside_the_horizon_is_not_safe():
    assert not section_is_safe(a_family(), "CE", TODAY)


def test_an_expired_section_is_not_safe():
    assert not section_is_safe(a_family(), "CF", TODAY)


def test_an_unrestricted_section_is_never_safe():
    assert not section_is_safe(a_family(), "CEA", TODAY)


def test_a_contract_only_section_is_safe_because_it_has_no_clock():
    assert section_is_safe(a_family(), "CEB", TODAY)


def test_keying_on_a_safe_section_is_allowed_through():
    refuse_unsafe_section(a_family(), "C", TODAY)


def test_keying_on_a_section_that_is_going_is_refused_with_the_date():
    with pytest.raises(KeywayError) as caught:
        refuse_unsafe_section(a_family(), "CE", TODAY)
    assert caught.value.facts["expires"] == "2027-02-14"


def test_keying_on_an_open_section_says_it_was_never_restricted():
    with pytest.raises(KeywayError) as caught:
        refuse_unsafe_section(a_family(), "CEA", TODAY)
    assert caught.value.facts["expires"] == "never restricted"


def test_a_root_section_reaches_the_open_sections_under_it():
    assert exposed_below(a_family(), "C", TODAY) == ("CF", "CEA")


def test_a_leaf_that_is_still_restricted_exposes_nothing():
    assert exposed_below(a_family(), "CEB", TODAY) == ()


def test_only_the_sections_in_use_are_reported_at_risk():
    assert systems_at_risk(a_family(), ["C", "CF", "CEB"], TODAY) == ("CF",)


def test_nothing_in_use_is_nothing_at_risk():
    assert systems_at_risk(a_family(), [], TODAY) == ()


def test_the_widest_exposure_gets_moved_first():
    family = a_family()
    family.add(KeywaySection("CFA", "Schlage", True, "2022-01-01"), "CF")
    assert migration_order(family, TODAY) == ("CF", "CFA")


def test_a_section_that_was_never_restricted_is_not_a_migration():
    assert "CEA" not in migration_order(a_family(), TODAY)


def test_the_report_leads_with_what_is_already_gone():
    lines = patent_report(a_family(), TODAY)
    assert lines[0] == "everest 29 patent position at 2025-06-30"
    assert lines[1] == "1 sections are open and were not meant to be"


def test_a_clean_family_says_so():
    family = KeywayFamily("clean", KeywaySection("A", "Best", True, "2044-01-01"))
    lines = patent_report(family, TODAY)
    assert lines[1] == "every restricted section is still restricted"


def test_the_report_names_the_next_one_to_go():
    lines = patent_report(a_family(), TODAY)
    assert lines[-1] == "next is CE restricted until 2027-02-14"


def test_the_wall_planner_drops_everything_without_a_date():
    found = dates_in_order(list(a_family()))
    assert [code for code, _ in found] == ["CF", "CE", "C"]


def test_a_description_says_what_kind_of_restriction_it_is():
    assert exposure_of(a_family().section("CEB")).describe(TODAY).endswith("by contract only")
    assert exposure_of(a_family().section("CEA")).describe(TODAY) == "CEA open keyway"

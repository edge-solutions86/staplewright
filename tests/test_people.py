from datetime import date

import pytest

from hasplib.control.people import (
    Affiliation,
    Authorizer,
    Directory,
    Keyholder,
    Standing,
)
from hasplib.errors import ConflictError, InputError, NotFoundError
from people import TODAY, a_directory


def test_a_keyholder_carries_a_label_the_counter_reads():
    assert a_directory().person("kmaki").label == "Kirsi Maki (kmaki)"


def test_a_keyholder_needs_an_identifier_and_a_name():
    with pytest.raises(InputError):
        Keyholder("  ", "Kirsi Maki", Affiliation.FACULTY, "geography")
    with pytest.raises(InputError):
        Keyholder("kmaki", " ", Affiliation.FACULTY, "geography")


@pytest.mark.parametrize("affiliation", [Affiliation.CONTRACTOR, Affiliation.STUDENT])
def test_a_temporary_keyholder_needs_an_end_date(affiliation):
    with pytest.raises(ConflictError):
        Keyholder("temp", "A Person", affiliation, "geography")


def test_a_permanent_keyholder_does_not_need_one():
    assert Keyholder("kmaki", "Kirsi Maki", Affiliation.FACULTY, "geography").ends_on is None


def test_a_separated_record_needs_the_date_it_happened():
    with pytest.raises(ConflictError):
        Keyholder(
            "kmaki",
            "Kirsi Maki",
            Affiliation.FACULTY,
            "geography",
            standing=Standing.SEPARATED,
        )


def test_an_active_keyholder_may_hold_keys():
    assert a_directory().person("kmaki").may_hold_keys(TODAY)


def test_a_contract_that_ran_out_stops_the_keys_on_its_own():
    person = a_directory().person("jwexler")
    assert person.standing is Standing.ACTIVE
    assert person.lapsed_on(TODAY)
    assert not person.may_hold_keys(TODAY)


def test_a_contract_still_running_is_fine():
    person = a_directory().person("tkoski")
    assert not person.lapsed_on(TODAY)
    assert person.may_hold_keys(TODAY)


def test_the_day_the_contract_ends_is_still_good():
    person = a_directory().person("tkoski")
    assert person.may_hold_keys(date(2025, 5, 16))
    assert not person.may_hold_keys(date(2025, 5, 17))


def test_separating_somebody_stops_them_holding_keys():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    assert directory.person("kmaki").standing is Standing.SEPARATED
    assert not directory.person("kmaki").may_hold_keys(TODAY)


def test_separating_twice_is_refused():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    with pytest.raises(ConflictError):
        directory.separate("kmaki", TODAY)


def test_suspending_stops_the_keys_without_closing_the_record():
    directory = a_directory()
    directory.suspend("kmaki")
    assert directory.person("kmaki").standing is Standing.SUSPENDED
    assert not directory.person("kmaki").may_hold_keys(TODAY)


def test_a_separated_keyholder_cannot_be_suspended():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    with pytest.raises(ConflictError):
        directory.suspend("kmaki")


def test_reinstating_puts_somebody_back():
    directory = a_directory()
    directory.separate("kmaki", TODAY)
    directory.reinstate("kmaki")
    person = directory.person("kmaki")
    assert person.standing is Standing.ACTIVE
    assert person.separated_on is None


def test_extending_a_contract_moves_the_date_out():
    directory = a_directory()
    directory.extend("jwexler", date(2025, 6, 30))
    assert directory.person("jwexler").may_hold_keys(TODAY)


def test_an_extension_cannot_go_backward():
    directory = a_directory()
    with pytest.raises(ConflictError):
        directory.extend("jwexler", date(2024, 12, 1))


def test_a_permanent_record_has_no_date_to_extend():
    directory = a_directory()
    with pytest.raises(ConflictError):
        directory.extend("kmaki", date(2026, 1, 1))


def test_an_identifier_cannot_be_used_twice():
    directory = a_directory()
    with pytest.raises(ConflictError):
        directory.add(Keyholder("kmaki", "Someone Else", Affiliation.STAFF, "physics"))


def test_asking_about_somebody_who_is_not_there():
    with pytest.raises(NotFoundError):
        a_directory().person("nobody")


def test_a_department_lists_its_own_people():
    identifiers = [p.identifier for p in a_directory().in_department("geography")]
    assert identifiers == ["dsalo", "kmaki", "rvirta", "tkoski"]


def test_the_active_list_leaves_out_lapsed_records():
    identifiers = [p.identifier for p in a_directory().active(TODAY)]
    assert "jwexler" not in identifiers
    assert "kmaki" in identifiers


def test_a_lapsed_record_nobody_closed_is_reported():
    lapsed = [p.identifier for p in a_directory().lapsed(TODAY)]
    assert lapsed == ["jwexler"]


def test_a_separated_record_is_not_also_reported_as_lapsed():
    directory = a_directory()
    directory.separate("jwexler", TODAY)
    assert directory.lapsed(TODAY) == ()


def test_an_authorizer_signs_for_a_department_and_its_buildings():
    directory = a_directory()
    assert directory.may_authorize("rvirta", "geography", "MOO", TODAY)
    assert not directory.may_authorize("rvirta", "geography", "VOS", TODAY)
    assert not directory.may_authorize("rvirta", "physics", "MOO", TODAY)


def test_an_authorizer_with_no_buildings_signs_for_all_of_them():
    directory = a_directory()
    assert directory.may_authorize("phamm", "facilities", "VOS", TODAY)
    assert directory.may_authorize("phamm", "facilities", "HEI", TODAY)


def test_somebody_who_does_not_sign_cannot_sign():
    assert not a_directory().may_authorize("kmaki", "geography", "MOO", TODAY)


def test_a_separated_authorizer_stops_signing():
    directory = a_directory()
    directory.separate("rvirta", TODAY)
    assert not directory.may_authorize("rvirta", "geography", "MOO", TODAY)


def test_an_authorizer_has_to_be_in_the_directory_first():
    directory = a_directory()
    with pytest.raises(NotFoundError):
        directory.add_authorizer(Authorizer("ghost", "geography"))


def test_the_same_authorizer_cannot_be_added_twice():
    directory = a_directory()
    with pytest.raises(ConflictError):
        directory.add_authorizer(Authorizer("rvirta", "geography"))


def test_asking_about_an_authorizer_who_does_not_sign():
    with pytest.raises(NotFoundError):
        a_directory().authorizer("kmaki")


def test_the_directory_is_a_container_in_identifier_order():
    directory = a_directory()
    assert "kmaki" in directory
    assert "nobody" not in directory
    assert len(directory) == 6
    identifiers = [p.identifier for p in directory]
    assert identifiers == sorted(identifiers)


def test_an_empty_directory_holds_nothing():
    assert len(Directory()) == 0
    assert Directory().authorizers() == ()

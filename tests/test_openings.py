import pytest

from campus import a_campus
from hasplib.campus.openings import (
    Building,
    Function,
    Opening,
    OpeningSchedule,
)
from hasplib.errors import ConflictError, InputError, NotFoundError


def test_a_building_carries_its_code_and_its_year():
    moore = a_campus().building("MOO")
    assert moore.name == "Moore Hall"
    assert moore.year_built == 1953


@pytest.mark.parametrize("code", ["", "  ", "moo", " MOO"])
def test_a_building_code_is_upper_case_and_trimmed(code):
    with pytest.raises(InputError):
        Building(code, "Moore Hall")


@pytest.mark.parametrize("year", [1700, 2200])
def test_a_building_year_has_to_be_plausible(year):
    with pytest.raises(InputError):
        Building("MOO", "Moore Hall", year)


def test_a_building_with_no_year_is_fine():
    assert Building("MOO", "Moore Hall").year_built is None


def test_an_opening_names_where_it_is():
    opening = a_campus().opening("MOO-214")
    assert opening.label == "MOO 214"
    assert opening.describe() == "MOO-214: MOO floor 2 room 214, office"


def test_a_keyed_function_takes_a_cylinder_and_privacy_does_not():
    campus = a_campus()
    assert campus.opening("MOO-214").is_keyed
    assert campus.opening("HEI-G12").is_keyed
    assert not campus.opening("MOO-260").is_keyed


def test_an_opening_needs_a_number_and_a_building():
    with pytest.raises(InputError):
        Opening("", "MOO", "1", "101", Function.OFFICE)
    with pytest.raises(InputError):
        Opening("MOO-101", "  ", "1", "101", Function.OFFICE)


@pytest.mark.parametrize("function", [Function.PRIVACY, Function.CABINET])
def test_some_functions_do_not_belong_on_an_exterior_door(function):
    with pytest.raises(ConflictError):
        Opening("MOO-999", "MOO", "1", "999", function, exterior=True)


def test_the_same_function_inside_is_fine():
    assert Opening("MOO-999", "MOO", "1", "999", Function.PRIVACY).number == "MOO-999"


def test_openings_come_back_by_building_in_walking_order():
    numbers = [o.number for o in a_campus().in_building("MOO")]
    assert numbers == ["MOO-101", "MOO-110", "MOO-214", "MOO-216", "MOO-218", "MOO-260"]


def test_a_floor_is_a_slice_of_a_building():
    numbers = [o.number for o in a_campus().on_floor("MOO", "2")]
    assert numbers == ["MOO-214", "MOO-216", "MOO-218", "MOO-260"]
    assert a_campus().on_floor("MOO", "9") == ()


def test_the_floors_of_a_building_are_reported_once_each():
    assert a_campus().floors("MOO") == ("1", "2")
    assert a_campus().floors("HEI") == ("1", "B")


def test_openings_can_be_pulled_out_by_function():
    numbers = [o.number for o in a_campus().with_function(Function.STOREROOM)]
    assert numbers == ["HEI-001", "MOO-218"]


def test_the_keyed_openings_leave_out_the_ones_that_take_no_cylinder():
    campus = a_campus()
    keyed = {o.number for o in campus.keyed_openings()}
    assert "MOO-260" not in keyed
    assert len(keyed) == len(campus) - 1


def test_the_exterior_openings_are_the_ones_facing_out():
    numbers = [o.number for o in a_campus().exterior_openings()]
    assert numbers == ["MOO-101", "VOS-100"]


def test_a_count_by_building_covers_every_building():
    counts = a_campus().count_by_building()
    assert counts == {"HEI": 2, "MOO": 6, "VOS": 3}


def test_a_building_with_no_openings_still_counts_zero():
    campus = a_campus()
    campus.add_building(Building("SCI", "Science Annex"))
    assert campus.count_by_building()["SCI"] == 0


def test_an_opening_in_a_building_nobody_added_is_refused():
    campus = a_campus()
    with pytest.raises(NotFoundError):
        campus.add(Opening("ZZZ-1", "ZZZ", "1", "1", Function.OFFICE))


def test_two_openings_cannot_share_a_number():
    campus = a_campus()
    with pytest.raises(ConflictError):
        campus.add(Opening("MOO-214", "MOO", "2", "214", Function.OFFICE))


def test_a_building_cannot_be_added_twice():
    campus = a_campus()
    with pytest.raises(ConflictError):
        campus.add_building(Building("MOO", "Moore Hall"))


def test_asking_for_something_that_is_not_there():
    campus = a_campus()
    with pytest.raises(NotFoundError):
        campus.opening("MOO-999")
    with pytest.raises(NotFoundError):
        campus.building("ZZZ")


def test_a_door_that_got_bricked_up_comes_off_the_list():
    campus = a_campus()
    before = len(campus)
    campus.remove("MOO-218")
    assert len(campus) == before - 1
    assert "MOO-218" not in campus


def test_removing_a_door_twice_is_refused():
    campus = a_campus()
    campus.remove("MOO-218")
    with pytest.raises(NotFoundError):
        campus.remove("MOO-218")


def test_the_schedule_is_a_container_in_number_order():
    campus = a_campus()
    assert "MOO-214" in campus
    assert "MOO-999" not in campus
    numbers = [o.number for o in campus]
    assert numbers == sorted(numbers)


def test_an_empty_schedule_holds_nothing():
    assert len(OpeningSchedule()) == 0
    assert list(OpeningSchedule()) == []

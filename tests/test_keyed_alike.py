import pytest

from campus import a_campus
from hasplib.campus.hardware import Cylinder, CylinderFormat, HardwareRegister, Position
from hasplib.campus.keyed_alike import (
    KeyedAlikeRegister,
    KeyedAlikeSet,
    can_be_keyed_alike,
    cylinders_in,
    problems,
)
from hasplib.errors import ConflictError, InputError, NotFoundError


def a_set(name: str = "moore cupboards", *openings: str, **overrides) -> KeyedAlikeSet:
    doors = openings or ("MOO-214", "MOO-216")
    return KeyedAlikeSet(name, doors, overrides.get("note", ""))


def a_register(**shapes) -> HardwareRegister:
    """Cylinders in MOO-214, MOO-216 and MOO-218, shaped as asked."""
    register = HardwareRegister(a_campus())
    doors = {"C-1": "MOO-214", "C-2": "MOO-216", "C-3": "MOO-218"}
    for serial, opening in doors.items():
        settings = {
            "serial": serial,
            "format": CylinderFormat.MORTISE,
            "section": "LC",
            "chambers": 6,
        }
        settings.update(shapes.get(serial, {}))
        register.add(Cylinder(**settings))
        register.install(serial, opening, Position.LOCKSET)
    return register


def test_a_set_needs_a_name_and_two_doors():
    with pytest.raises(InputError):
        KeyedAlikeSet("  ", ("MOO-214", "MOO-216"))
    with pytest.raises(ConflictError):
        KeyedAlikeSet("one", ("MOO-214",))


def test_a_door_cannot_be_in_a_set_twice():
    with pytest.raises(ConflictError):
        KeyedAlikeSet("twice", ("MOO-214", "MOO-214"))


def test_a_set_knows_what_is_in_it():
    alike = a_set()
    assert alike.size == 2
    assert alike.holds("MOO-214")
    assert not alike.holds("MOO-218")
    assert alike.describe().startswith("moore cupboards: MOO-214, MOO-216")


def test_the_cylinders_come_back_in_door_order():
    found = cylinders_in(a_register(), a_set())
    assert [cylinder.serial for cylinder in found] == ["C-1", "C-2"]


def test_a_matched_set_has_nothing_wrong_with_it():
    assert can_be_keyed_alike(a_register(), a_set())
    assert problems(a_register(), a_set()) == ()


def test_a_door_with_nothing_in_it_is_reported():
    register = a_register()
    register.pull("C-2")
    found = problems(register, a_set())
    assert found == ("MOO-216 has nothing in the lockset",)


def test_a_set_spanning_sections_is_reported():
    register = a_register(**{"C-2": {"section": "LD"}})
    assert any("keyway sections" in line for line in problems(register, a_set()))


def test_a_set_spanning_chamber_counts_is_reported():
    register = a_register(**{"C-2": {"chambers": 7}})
    assert any("chamber counts" in line for line in problems(register, a_set()))


def test_mortise_and_rim_can_be_keyed_alike():
    register = a_register(**{"C-2": {"format": CylinderFormat.RIM}})
    assert can_be_keyed_alike(register, a_set())


def test_a_core_and_a_mortise_cannot():
    register = a_register(**{"C-2": {"format": CylinderFormat.SFIC}})
    assert any("do not take one cut" in line for line in problems(register, a_set()))


def test_a_set_with_no_cylinders_at_all_reports_only_the_empty_doors():
    register = HardwareRegister(a_campus())
    found = problems(register, a_set())
    assert len(found) == 2
    assert all("nothing in the lockset" in line for line in found)


def test_a_set_goes_on_the_register_once():
    register = KeyedAlikeRegister()
    register.add(a_set())
    with pytest.raises(ConflictError):
        register.add(a_set("another", "MOO-214", "MOO-218"))


def test_a_door_belongs_to_one_set():
    register = KeyedAlikeRegister()
    register.add(a_set())
    assert register.set_holding("MOO-214").name == "moore cupboards"
    assert register.set_holding("MOO-260") is None


def test_the_other_doors_go_up_with_it():
    register = KeyedAlikeRegister()
    register.add(a_set("moore cupboards", "MOO-214", "MOO-216", "MOO-218"))
    assert register.openings_alike_with("MOO-214") == ("MOO-216", "MOO-218")


def test_a_door_in_no_set_takes_nothing_with_it():
    assert KeyedAlikeRegister().openings_alike_with("MOO-214") == ()


def test_two_sets_merge_into_one():
    register = KeyedAlikeRegister()
    register.add(a_set("cupboards", "MOO-214", "MOO-216"))
    register.add(a_set("stores", "MOO-218", "MOO-110"))
    joined = register.merge("cupboards", "stores", "moore ground")
    assert joined.size == 4
    assert len(register) == 1
    assert "cupboards" not in register


def test_a_set_splits_into_two():
    register = KeyedAlikeRegister()
    register.add(a_set("moore", "MOO-214", "MOO-216", "MOO-218", "MOO-110"))
    taken = register.split("moore", ["MOO-218", "MOO-110"], "moore stores")
    assert taken.size == 2
    assert register.get("moore").size == 2
    assert register.openings_alike_with("MOO-218") == ("MOO-110",)


def test_a_split_that_would_leave_one_door_is_refused():
    register = KeyedAlikeRegister()
    register.add(a_set("moore", "MOO-214", "MOO-216", "MOO-218"))
    with pytest.raises(ConflictError):
        register.split("moore", ["MOO-218"], "alone")


def test_a_split_naming_a_door_that_is_not_in_the_set():
    register = KeyedAlikeRegister()
    register.add(a_set("moore", "MOO-214", "MOO-216", "MOO-218", "MOO-110"))
    with pytest.raises(NotFoundError):
        register.split("moore", ["MOO-999", "MOO-218"], "elsewhere")


def test_a_set_can_be_taken_off_the_register():
    register = KeyedAlikeRegister()
    register.add(a_set())
    register.remove("moore cupboards")
    assert len(register) == 0
    assert register.set_holding("MOO-214") is None


def test_a_set_that_is_not_there():
    with pytest.raises(NotFoundError):
        KeyedAlikeRegister().get("nothing")


def test_the_register_lists_its_sets_in_name_order():
    register = KeyedAlikeRegister()
    register.add(a_set("stores", "MOO-218", "MOO-110"))
    register.add(a_set("cupboards", "MOO-214", "MOO-216"))
    assert [alike.name for alike in register] == ["cupboards", "stores"]

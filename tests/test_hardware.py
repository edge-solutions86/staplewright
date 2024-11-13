import pytest

from campus import a_campus
from hasplib.campus.hardware import (
    Cylinder,
    CylinderFormat,
    HardwareRegister,
    Position,
)
from hasplib.errors import ConflictError, InputError, NotFoundError


def a_cylinder(serial: str, **overrides) -> Cylinder:
    settings = {
        "serial": serial,
        "format": CylinderFormat.MORTISE,
        "section": "LC",
        "chambers": 6,
        "manufacturer": "Sargent",
    }
    settings.update(overrides)
    return Cylinder(**settings)


def a_register() -> HardwareRegister:
    register = HardwareRegister(a_campus())
    for serial in ["C-1001", "C-1002", "C-1003", "C-1004"]:
        register.add(a_cylinder(serial))
    register.add(a_cylinder("K-2001", format=CylinderFormat.SFIC, chambers=7, section="A2"))
    return register


def test_a_new_cylinder_starts_on_the_shelf():
    register = a_register()
    assert not register.cylinder("C-1001").is_installed
    assert len(register.on_the_shelf()) == 5


def test_a_serial_is_required():
    with pytest.raises(InputError):
        a_cylinder("  ")


def test_a_cylinder_has_chambers():
    with pytest.raises(InputError):
        a_cylinder("C-9", chambers=0)


def test_a_cylinder_is_installed_or_shelved_and_not_half_of_each():
    with pytest.raises(ConflictError):
        a_cylinder("C-9", installed_at="MOO-214")
    with pytest.raises(ConflictError):
        a_cylinder("C-9", position=Position.LOCKSET)


def test_a_core_knows_it_is_a_core():
    register = a_register()
    assert register.cylinder("K-2001").is_core
    assert not register.cylinder("C-1001").is_core


def test_installing_puts_the_cylinder_in_the_door():
    register = a_register()
    installed = register.install("C-1001", "MOO-214", Position.LOCKSET)
    assert installed.installed_at == "MOO-214"
    assert installed.position is Position.LOCKSET
    assert register.at("MOO-214", Position.LOCKSET).serial == "C-1001"


def test_a_shelved_cylinder_describes_itself_that_way():
    register = a_register()
    assert "on the shelf" in register.cylinder("C-1001").describe()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    assert "MOO-214" in register.cylinder("C-1001").describe()


def test_one_opening_takes_a_lockset_and_a_deadbolt():
    register = a_register()
    register.install("C-1001", "MOO-218", Position.LOCKSET)
    register.install("C-1002", "MOO-218", Position.DEADBOLT)
    assert [c.serial for c in register.on_opening("MOO-218")] == ["C-1002", "C-1001"]


def test_two_cylinders_cannot_share_a_position():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    with pytest.raises(ConflictError) as caught:
        register.install("C-1002", "MOO-214", Position.LOCKSET)
    assert caught.value.facts["serial"] == "C-1001"


def test_a_cylinder_cannot_be_in_two_doors():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    with pytest.raises(ConflictError):
        register.install("C-1001", "MOO-216", Position.LOCKSET)


def test_an_opening_that_takes_no_cylinder_refuses_one():
    register = a_register()
    with pytest.raises(ConflictError) as caught:
        register.install("C-1001", "MOO-260", Position.LOCKSET)
    assert caught.value.facts["function"] == "privacy"


def test_installing_into_a_door_nobody_has_heard_of():
    register = a_register()
    with pytest.raises(NotFoundError):
        register.install("C-1001", "ZZZ-1", Position.LOCKSET)


def test_pulling_a_cylinder_puts_it_back_on_the_shelf():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    register.pull("C-1001")
    assert register.at("MOO-214", Position.LOCKSET) is None
    assert not register.cylinder("C-1001").is_installed


def test_pulling_a_shelved_cylinder_is_refused():
    register = a_register()
    with pytest.raises(ConflictError):
        register.pull("C-1001")


def test_moving_a_cylinder_frees_the_position_it_left():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    register.move("C-1001", "MOO-216", Position.LOCKSET)
    assert register.at("MOO-214", Position.LOCKSET) is None
    assert register.at("MOO-216", Position.LOCKSET).serial == "C-1001"


def test_a_cylinder_joins_the_register_shelved():
    register = a_register()
    with pytest.raises(ConflictError):
        register.add(a_cylinder("C-9", installed_at="MOO-214", position=Position.LOCKSET))


def test_a_serial_cannot_be_registered_twice():
    register = a_register()
    with pytest.raises(ConflictError):
        register.add(a_cylinder("C-1001"))


def test_cylinders_come_back_by_building():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    register.install("C-1002", "VOS-311", Position.LOCKSET)
    assert [c.serial for c in register.in_building("MOO")] == ["C-1001"]
    assert [c.serial for c in register.in_building("VOS")] == ["C-1002"]


def test_a_group_can_be_written_on_a_cylinder():
    register = a_register()
    register.assign_group("C-1001", "moore second floor offices")
    assert register.cylinder("C-1001").group == "moore second floor offices"
    assert [c.serial for c in register.in_group("moore second floor offices")] == ["C-1001"]


def test_an_installed_cylinder_with_no_group_is_flagged():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    register.install("C-1002", "MOO-216", Position.LOCKSET)
    register.assign_group("C-1002", "moore second floor offices")
    assert [c.serial for c in register.unkeyed()] == ["C-1001"]


def test_a_shelved_cylinder_is_not_flagged_as_unkeyed():
    assert a_register().unkeyed() == ()


def test_keyed_openings_with_nothing_in_them_are_reported():
    register = a_register()
    before = len(register.uncovered_openings())
    register.install("C-1001", "MOO-214", Position.LOCKSET)
    assert len(register.uncovered_openings()) == before - 1


def test_a_deadbolt_alone_does_not_cover_an_opening():
    register = a_register()
    register.install("C-1001", "MOO-214", Position.DEADBOLT)
    numbers = {opening.number for opening in register.uncovered_openings()}
    assert "MOO-214" in numbers


def test_the_register_is_a_container_in_serial_order():
    register = a_register()
    assert "C-1001" in register
    assert "C-9999" not in register
    serials = [c.serial for c in register]
    assert serials == sorted(serials)
    assert len(register) == 5

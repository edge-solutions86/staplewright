import pytest

from charts import six_pin_chart
from hasplib.errors import CapacityError, PinningError
from hasplib.keys.bitting import Bitting
from hasplib.pinning.cylinder import (
    build_from_kit,
    pin_cylinder,
    strip_to_kit,
)
from hasplib.pinning.segments import DriverProfile, SegmentKind, standard_kit
from hasplib.pinning.stack import PinningSpec


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_spec() -> PinningSpec:
    return PinningSpec(six_pin_chart(), 150, 480)


def a_kit():
    return standard_kit(
        "bench",
        bottom_lengths=[150 + 15 * step for step in range(10)],
        wafer_lengths=[30, 45, 60, 75, 90],
        driver_lengths=[105 + 15 * step for step in range(18)],
        count=40,
    )


def test_a_cylinder_for_one_key_has_no_wafers():
    pinning = pin_cylinder(a_spec(), [b("342156")])
    assert pinning.chambers == 6
    assert pinning.wafer_count == 0
    assert pinning.mastered_chambers == ()
    assert pinning.piece_count == 12


def test_a_master_and_a_change_key_split_where_they_differ():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    assert pinning.mastered_chambers == (2, 4)
    assert pinning.wafer_count == 2
    assert pinning.stack(2).wafers_mils == (30,)
    assert pinning.stack(1).wafers_mils == ()


def test_a_cylinder_reports_the_depths_each_chamber_takes():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    assert pinning.break_depths(2) == (4, 6)
    assert pinning.break_depths(1) == (3,)


def test_asking_about_a_chamber_the_cylinder_does_not_have():
    pinning = pin_cylinder(a_spec(), [b("342156")])
    with pytest.raises(PinningError):
        pinning.stack(9)
    with pytest.raises(PinningError):
        pinning.break_depths(0)


def test_the_keys_it_was_built_for_all_operate_it():
    keys = [b("342156"), b("362356"), b("382556")]
    pinning = pin_cylinder(a_spec(), keys)
    for key in keys:
        assert pinning.operates(key)


def test_an_incidental_key_operates_it_too():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    assert pinning.operates(b("342356"))
    assert pinning.incidental_key_count == 2
    assert pinning.operating_key_count == 4


def test_a_key_that_misses_one_chamber_does_not_operate_it():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    assert not pinning.operates(b("352156"))


def test_a_key_of_the_wrong_length_does_not_operate_it():
    pinning = pin_cylinder(a_spec(), [b("342156")])
    assert not pinning.operates(b("3421"))


def test_a_cylinder_with_no_keys_is_refused():
    with pytest.raises(PinningError):
        pin_cylinder(a_spec(), [])


def test_a_key_of_the_wrong_length_cannot_be_pinned_for():
    with pytest.raises(PinningError):
        pin_cylinder(a_spec(), [b("342156"), b("3421")])


def test_spooling_a_cylinder_spools_every_chamber():
    pinning = pin_cylinder(a_spec(), [b("342156")], DriverProfile.SPOOL)
    drivers = [part for part in pinning.parts() if part.kind is SegmentKind.DRIVER]
    assert len(drivers) == 6
    assert all(part.profile is DriverProfile.SPOOL for part in drivers)


def test_the_parts_list_covers_every_chamber():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    assert len(pinning.parts()) == pinning.piece_count


def test_building_from_the_kit_takes_the_brass_out():
    kit = a_kit()
    before = kit.total()
    pinning = build_from_kit(a_spec(), kit, [b("342156"), b("362356")])
    assert kit.total() == before - pinning.piece_count


def test_a_kit_that_cannot_fill_the_cylinder_says_so():
    kit = standard_kit("thin", [195], [30], [285], count=2)
    with pytest.raises(CapacityError):
        build_from_kit(a_spec(), kit, [b("342156")])
    assert kit.total() == 6


def test_a_cylinder_says_whether_the_kit_can_build_it():
    kit = a_kit()
    pinning = pin_cylinder(a_spec(), [b("342156")])
    assert pinning.can_be_built_from(kit)
    assert not pinning.can_be_built_from(standard_kit("empty", [], [], []))


def test_stripping_a_cylinder_puts_the_brass_back():
    kit = a_kit()
    before = kit.total()
    pinning = build_from_kit(a_spec(), kit, [b("342156"), b("362356")])
    strip_to_kit(kit, pinning)
    assert kit.total() == before

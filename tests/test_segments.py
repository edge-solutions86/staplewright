import pytest

from hasplib.errors import CapacityError, PinningError
from hasplib.pinning.segments import (
    DriverProfile,
    PinKit,
    PinSegment,
    SegmentKind,
    standard_kit,
)


def bottom(mils: int) -> PinSegment:
    return PinSegment(SegmentKind.BOTTOM, mils)


def wafer(mils: int) -> PinSegment:
    return PinSegment(SegmentKind.WAFER, mils)


def driver(mils: int, profile: DriverProfile = DriverProfile.STANDARD) -> PinSegment:
    return PinSegment(SegmentKind.DRIVER, mils, profile)


def a_kit() -> PinKit:
    return standard_kit("bench", [150, 165, 180], [30, 45, 60], [175, 190], count=4)


def test_a_bin_code_names_the_kind_and_the_length():
    assert bottom(150).bin_code == "B150"
    assert wafer(30).bin_code == "W30"
    assert driver(190).bin_code == "D190"


def test_a_security_driver_says_so_on_the_bin():
    assert driver(190, DriverProfile.SPOOL).bin_code == "D190S"
    assert driver(190, DriverProfile.SERRATED).bin_code == "D190S"
    assert str(driver(190, DriverProfile.SPOOL)) == "D190S"


def test_a_segment_needs_a_length():
    with pytest.raises(PinningError):
        bottom(0)


def test_only_drivers_come_in_profiles():
    with pytest.raises(PinningError):
        PinSegment(SegmentKind.BOTTOM, 150, DriverProfile.SPOOL)


def test_two_segments_of_the_same_shape_are_the_same_part():
    assert bottom(150) == bottom(150)
    assert len({bottom(150), bottom(150)}) == 1
    assert bottom(150) != wafer(150)


def test_a_new_kit_is_stocked_evenly():
    kit = a_kit()
    assert kit.stock_of(bottom(150)) == 4
    assert kit.stock_of(wafer(45)) == 4
    assert kit.total() == 8 * 4


def test_a_bin_nobody_stocked_holds_nothing():
    assert a_kit().stock_of(bottom(999)) == 0
    assert not a_kit().holds(bottom(999))


def test_the_lengths_on_hand_come_back_in_order():
    kit = a_kit()
    assert kit.lengths(SegmentKind.BOTTOM) == (150, 165, 180)
    assert kit.lengths(SegmentKind.WAFER) == (30, 45, 60)


def test_an_empty_bin_drops_out_of_the_lengths():
    kit = a_kit()
    kit.draw([bottom(165)] * 4)
    assert kit.lengths(SegmentKind.BOTTOM) == (150, 180)


def test_lengths_can_be_asked_for_one_profile():
    kit = a_kit()
    kit.add(driver(190, DriverProfile.SPOOL), 6)
    assert kit.lengths(SegmentKind.DRIVER) == (175, 190)
    assert kit.lengths(SegmentKind.DRIVER, DriverProfile.SPOOL) == (190,)


def test_a_draw_takes_every_part_it_asked_for():
    kit = a_kit()
    kit.draw([bottom(150), bottom(150), wafer(30)])
    assert kit.stock_of(bottom(150)) == 2
    assert kit.stock_of(wafer(30)) == 3


def test_a_draw_that_cannot_be_filled_takes_nothing():
    kit = a_kit()
    with pytest.raises(CapacityError):
        kit.draw([bottom(150)] * 5)
    assert kit.stock_of(bottom(150)) == 4


def test_the_shortfall_names_the_bins_and_the_counts():
    kit = a_kit()
    short = kit.shortfall([bottom(150)] * 6 + [wafer(999)])
    assert short[bottom(150)] == 2
    assert short[wafer(999)] == 1


def test_a_draw_that_fits_reports_no_shortfall():
    assert a_kit().shortfall([bottom(150), wafer(30)]) == {}


def test_shortfall_takes_nothing_off_the_shelf():
    kit = a_kit()
    kit.shortfall([bottom(150)] * 6)
    assert kit.stock_of(bottom(150)) == 4


def test_parts_off_a_cylinder_go_back_in_the_drawer():
    kit = a_kit()
    kit.draw([bottom(150), wafer(30)])
    kit.put_back([bottom(150), wafer(30), driver(175)])
    assert kit.stock_of(bottom(150)) == 4
    assert kit.stock_of(driver(175)) == 5


def test_restocking_a_bin_that_was_never_there():
    kit = PinKit("new")
    kit.add(bottom(150), 12)
    assert kit.stock_of(bottom(150)) == 12


def test_restocking_nothing_is_refused():
    with pytest.raises(PinningError):
        PinKit("new").add(bottom(150), 0)


def test_a_kit_cannot_start_with_a_negative_bin():
    with pytest.raises(PinningError):
        PinKit("broken", {bottom(150): -2})


def test_a_bin_stocked_at_zero_is_not_a_bin():
    kit = PinKit("new", {bottom(150): 0, wafer(30): 3})
    assert kit.bins() == ((wafer(30), 3),)


def test_bins_print_in_bin_code_order():
    kit = PinKit("new", {driver(190): 1, bottom(150): 2, wafer(30): 3})
    assert [segment.bin_code for segment, _ in kit.bins()] == ["B150", "D190", "W30"]


def test_a_copy_does_not_share_the_drawer():
    kit = a_kit()
    clone = kit.copy()
    clone.draw([bottom(150)] * 4)
    assert kit.stock_of(bottom(150)) == 4
    assert clone.stock_of(bottom(150)) == 0

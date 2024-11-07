import pytest

from charts import six_pin_chart
from hasplib.errors import PinningError
from hasplib.pinning.segments import DriverProfile, SegmentKind
from hasplib.pinning.stack import PinningSpec, build_stack


def a_spec(**overrides) -> PinningSpec:
    settings = {
        "chart": six_pin_chart(),
        "bottom_at_shallowest_mils": 150,
        "column_mils": 480,
        "min_wafer_mils": 30,
        "min_driver_mils": 90,
    }
    settings.update(overrides)
    return PinningSpec(**settings)


def test_a_bottom_pin_grows_with_the_cut_depth():
    spec = a_spec()
    assert spec.bottom_pin(0) == 150
    assert spec.bottom_pin(1) == 165
    assert spec.bottom_pin(6) == 240


def test_a_depth_off_the_chart_has_no_pin():
    with pytest.raises(PinningError):
        a_spec().bottom_pin(11)


def test_a_wafer_is_the_distance_between_two_breaks():
    spec = a_spec()
    assert spec.wafer(2, 4) == 30
    assert spec.wafer(1, 7) == 90


def test_a_wafer_needs_two_different_depths():
    spec = a_spec()
    with pytest.raises(PinningError):
        spec.wafer(4, 4)
    with pytest.raises(PinningError):
        spec.wafer(6, 2)


def test_a_chamber_with_one_break_is_a_pin_and_a_driver():
    stack = build_stack(a_spec(), 1, [3])
    assert stack.bottom_mils == 195
    assert stack.wafers_mils == ()
    assert stack.driver_mils == 285
    assert stack.piece_count == 2


def test_every_stack_reaches_the_shear_line_exactly():
    spec = a_spec()
    for depths in ([3], [3, 5], [1, 5, 9], [0, 2, 4, 6]):
        assert build_stack(spec, 1, depths).height_mils == spec.column_mils


def test_a_master_chamber_carries_one_wafer():
    stack = build_stack(a_spec(), 2, [4, 6])
    assert stack.bottom_mils == 210
    assert stack.wafers_mils == (30,)
    assert stack.driver_mils == 240


def test_the_order_the_depths_arrive_in_does_not_matter():
    spec = a_spec()
    assert build_stack(spec, 2, [6, 4]) == build_stack(spec, 2, [4, 6])


def test_a_depth_named_twice_is_one_break():
    stack = build_stack(a_spec(), 2, [4, 6, 4])
    assert stack.wafers_mils == (30,)


def test_breaks_are_reported_as_heights_from_the_floor():
    stack = build_stack(a_spec(), 2, [2, 4, 8])
    assert stack.break_heights == (180, 210, 270)


def test_a_one_step_split_is_too_thin_to_stand_up():
    with pytest.raises(PinningError) as caught:
        build_stack(a_spec(), 2, [4, 5])
    assert caught.value.facts["thickness"] == 15


def test_a_shop_that_accepts_thin_wafers_gets_them():
    stack = build_stack(a_spec(min_wafer_mils=15), 2, [4, 5])
    assert stack.wafers_mils == (15,)


def test_too_many_breaks_leave_no_room_for_a_driver():
    with pytest.raises(PinningError) as caught:
        build_stack(a_spec(column_mils=300), 2, [1, 3, 5, 7, 9])
    assert caught.value.facts["shortest"] == 90
    assert caught.value.facts["driver"] == 15


def test_a_taller_cylinder_takes_the_same_stack():
    stack = build_stack(a_spec(column_mils=600), 2, [1, 3, 5, 7, 9])
    assert stack.piece_count == 6


def test_a_chamber_that_breaks_nowhere_is_refused():
    with pytest.raises(PinningError):
        build_stack(a_spec(), 2, [])


@pytest.mark.parametrize("chamber", [0, 7, -2])
def test_a_chamber_off_the_end_is_refused(chamber):
    with pytest.raises(PinningError):
        build_stack(a_spec(), chamber, [3])


def test_the_parts_list_reads_bottom_then_wafers_then_driver():
    stack = build_stack(a_spec(), 2, [2, 4, 8])
    parts = stack.segments()
    assert [part.kind for part in parts] == [
        SegmentKind.BOTTOM,
        SegmentKind.WAFER,
        SegmentKind.WAFER,
        SegmentKind.DRIVER,
    ]
    assert [part.length_mils for part in parts] == [180, 30, 60, 210]


def test_a_security_driver_carries_its_profile_into_the_parts_list():
    stack = build_stack(a_spec(), 2, [4], DriverProfile.SPOOL)
    assert stack.segments()[-1].profile is DriverProfile.SPOOL
    assert stack.segments()[-1].bin_code.endswith("S")


def test_a_column_shorter_than_its_bottom_pin_is_refused():
    with pytest.raises(PinningError):
        a_spec(column_mils=120)


@pytest.mark.parametrize("broken", [{"bottom_at_shallowest_mils": 0}, {"min_wafer_mils": 0}])
def test_a_spec_that_contradicts_itself_is_refused(broken):
    with pytest.raises(PinningError):
        a_spec(**broken)

import pytest

from charts import seven_pin_core_chart
from hasplib.errors import PinningError
from hasplib.keys.bitting import Bitting
from hasplib.pinning.core import (
    CoreSpec,
    build_core_stack,
    control_cut_range,
    pin_core,
)
from hasplib.pinning.segments import SegmentKind
from hasplib.pinning.stack import PinningSpec


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_spec(**overrides) -> CoreSpec:
    pinning = PinningSpec(
        seven_pin_core_chart(),
        overrides.get("bottom", 100),
        overrides.get("column", 600),
        overrides.get("min_wafer", 25),
        overrides.get("min_driver", 75),
    )
    return CoreSpec(pinning, overrides.get("gap", 50))


def test_the_control_break_sits_the_gap_above_the_operating_one():
    spec = a_spec()
    assert spec.operating_break(4) == 200
    assert spec.control_break(4) == 250


def test_a_control_gap_of_nothing_is_refused():
    with pytest.raises(PinningError):
        a_spec(gap=0)


def test_a_control_gap_thinner_than_a_wafer_is_refused():
    with pytest.raises(PinningError):
        a_spec(gap=10)


def test_a_core_chamber_carries_a_control_wafer_even_with_one_key():
    stack = build_core_stack(a_spec(), 1, [4], 4)
    assert stack.wafers_mils == ()
    assert stack.control_wafer_mils == 50
    assert stack.piece_count == 3


def test_the_column_still_adds_up_to_the_chamber_height():
    spec = a_spec()
    stack = build_core_stack(spec, 1, [2, 6], 5)
    assert stack.height_mils == spec.pinning.column_mils


def test_the_control_break_is_above_every_operating_break():
    stack = build_core_stack(a_spec(), 1, [2, 6], 5)
    assert stack.control_break > max(stack.operating_breaks)


def test_a_mastered_chamber_holds_its_wafers_under_the_control_wafer():
    stack = build_core_stack(a_spec(), 1, [2, 6], 6)
    assert stack.wafers_mils == (100,)
    assert stack.control_wafer_mils == 50


def test_a_control_cut_too_shallow_leaves_no_room_for_the_wafer():
    with pytest.raises(PinningError) as caught:
        build_core_stack(a_spec(), 1, [2, 6], 3)
    assert caught.value.facts["deepest_operating"] == 6


def test_a_control_cut_too_deep_leaves_no_room_for_a_driver():
    with pytest.raises(PinningError) as caught:
        build_core_stack(a_spec(column=400), 1, [2], 9)
    assert caught.value.facts["shortest"] == 75


def test_the_workable_control_cuts_are_a_run_in_the_middle():
    workable = control_cut_range(a_spec(column=400), [2, 6])
    assert workable == (5, 6, 7)


def test_every_workable_control_cut_actually_builds():
    spec = a_spec(column=400)
    for depth in control_cut_range(spec, [2, 6]):
        build_core_stack(spec, 1, [2, 6], depth)


def test_a_cut_outside_the_range_does_not_build():
    spec = a_spec(column=400)
    workable = set(control_cut_range(spec, [2, 6]))
    for depth in spec.pinning.chart.depths:
        if depth in workable:
            continue
        with pytest.raises(PinningError):
            build_core_stack(spec, 1, [2, 6], depth)


def test_a_chamber_with_no_operating_cuts_has_no_range():
    with pytest.raises(PinningError):
        control_cut_range(a_spec(), [])


def test_a_whole_core_is_pinned_for_its_keys_and_the_control():
    core = pin_core(a_spec(), [b("2462462"), b("2662462")], b("6666666"))
    assert core.chambers == 7
    assert core.piece_count == 7 * 3 + 1
    assert core.stack(2).wafers_mils == (50,)


def test_every_row_of_the_core_chart_shows_its_control_wafer():
    core = pin_core(a_spec(), [b("2462462")], b("6666666"))
    assert all("[" in line for line in core.chart_lines())
    assert len(core.chart_lines()) == 7


def test_the_parts_list_has_a_wafer_for_every_control_break():
    core = pin_core(a_spec(), [b("2462462")], b("6666666"))
    wafers = [part for part in core.parts() if part.kind is SegmentKind.WAFER]
    assert len(wafers) == 7


def test_a_core_with_no_keys_is_refused():
    with pytest.raises(PinningError):
        pin_core(a_spec(), [], b("6666666"))


def test_a_control_key_of_the_wrong_length_is_refused():
    with pytest.raises(PinningError):
        pin_core(a_spec(), [b("2462462")], b("666666"))


def test_asking_a_core_about_a_chamber_it_does_not_have():
    core = pin_core(a_spec(), [b("2462462")], b("6666666"))
    with pytest.raises(PinningError):
        core.stack(9)

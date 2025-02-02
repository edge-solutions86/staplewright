import pytest

from charts import six_pin_chart
from hasplib.errors import BittingError, InputError
from hasplib.keys.bitting import Bitting
from hasplib.keys.machine import (
    Calibration,
    cut_card,
    drift,
    duplicate_wear,
    measure,
    verify,
    within_tolerance,
)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def dimensions_for(text: str) -> list[int]:
    chart = six_pin_chart()
    return [chart.dimension_for(depth) for depth in b(text)]


def test_a_card_carries_one_line_a_chamber():
    card = cut_card(six_pin_chart(), b("342156"))
    assert card.cuts == 6
    assert card.instructions[0].chamber == 1
    assert card.instructions[0].depth == 3


def test_the_carriage_position_comes_off_the_spacing():
    card = cut_card(six_pin_chart(), b("342156"))
    assert [item.space_mils for item in card.instructions] == list(six_pin_chart().spaces())


def test_the_cutter_depth_comes_off_the_chart():
    card = cut_card(six_pin_chart(), b("342156"))
    assert card.instructions[0].depth_mils == six_pin_chart().dimension_for(3)


def test_a_machine_out_of_calibration_shifts_every_cut():
    card = cut_card(six_pin_chart(), b("342156"), Calibration(space_offset_mils=4))
    assert card.instructions[0].space_mils == six_pin_chart().space_for(1) + 4
    assert card.instructions[5].space_mils == six_pin_chart().space_for(6) + 4


def test_a_worn_cutter_shifts_every_depth():
    card = cut_card(six_pin_chart(), b("342156"), Calibration(depth_offset_mils=3))
    for item in card.instructions:
        assert item.depth_mils == six_pin_chart().dimension_for(item.depth) + 3


def test_a_true_machine_says_so():
    assert Calibration().is_true
    assert not Calibration(depth_offset_mils=1).is_true


def test_a_key_the_chart_will_not_cut_gets_no_card():
    with pytest.raises(BittingError):
        cut_card(six_pin_chart(), b("3421"))


def test_the_card_prints_a_header_and_a_line_a_cut():
    lines = cut_card(six_pin_chart(), b("342156")).lines()
    assert lines[0].endswith("342156")
    assert len(lines) == 7
    assert lines[1].startswith("1  depth 3")


def test_a_cut_key_reads_back_to_what_it_was_cut_to():
    assert measure(six_pin_chart(), dimensions_for("342156")) == b("342156")


def test_a_key_of_the_wrong_length_cannot_be_measured():
    with pytest.raises(InputError):
        measure(six_pin_chart(), [335, 320])


def test_a_cut_nowhere_near_a_depth_is_refused():
    with pytest.raises(BittingError):
        measure(six_pin_chart(), [312, 320, 305, 320, 260, 245])


def test_a_good_key_verifies_clean():
    assert verify(six_pin_chart(), b("342156"), dimensions_for("342156")) == ()


def test_a_wrong_cut_names_its_chamber():
    dims = dimensions_for("342156")
    dims[2] = six_pin_chart().dimension_for(6)
    assert verify(six_pin_chart(), b("342156"), dims) == (3,)


def test_two_wrong_cuts_name_both():
    dims = dimensions_for("342156")
    dims[0] = six_pin_chart().dimension_for(5)
    dims[4] = six_pin_chart().dimension_for(7)
    assert verify(six_pin_chart(), b("342156"), dims) == (1, 5)


def test_a_constant_error_across_a_key_is_the_machine():
    dims = [mils + 2 for mils in dimensions_for("342156")]
    assert drift(six_pin_chart(), b("342156"), dims) == 2


def test_a_key_cut_true_has_no_drift():
    assert drift(six_pin_chart(), b("342156"), dimensions_for("342156")) is None


def test_one_bad_chamber_is_not_a_machine_fault():
    dims = dimensions_for("342156")
    dims[2] += 4
    assert drift(six_pin_chart(), b("342156"), dims) is None


def test_drift_needs_a_key_of_the_right_length():
    with pytest.raises(InputError):
        drift(six_pin_chart(), b("342156"), [335, 320])


def test_a_traced_copy_reports_how_shallow_it_came_out():
    original = dimensions_for("342156")
    copy = [mils + 3 for mils in original]
    assert duplicate_wear(original, copy) == 3


def test_a_copy_cut_true_wears_nothing():
    original = dimensions_for("342156")
    assert duplicate_wear(original, list(original)) == 0


def test_the_worst_chamber_is_the_one_reported():
    original = dimensions_for("342156")
    copy = list(original)
    copy[0] += 2
    copy[3] += 6
    assert duplicate_wear(original, copy) == 6


def test_two_keys_of_different_lengths_cannot_be_compared():
    with pytest.raises(InputError):
        duplicate_wear([335, 320], [335])


def test_comparing_nothing_is_refused():
    with pytest.raises(InputError):
        duplicate_wear([], [])


def test_tolerance_is_a_yes_or_no_for_the_bench():
    assert within_tolerance(six_pin_chart(), dimensions_for("342156"))
    assert not within_tolerance(six_pin_chart(), [312, 320, 305, 320, 260, 245])
    assert not within_tolerance(six_pin_chart(), [335])

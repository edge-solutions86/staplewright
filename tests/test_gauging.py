import pytest

from charts import six_pin_chart
from hasplib.errors import BittingError, InputError
from hasplib.spec.gauging import (
    Blade,
    blade_long_enough,
    check_blade,
    drift_on,
    from_tip_to_shoulder,
    position_of,
    positions_for,
    shoulder_positions,
    tip_positions,
    within_tolerance,
)


def a_blade(**overrides) -> Blade:
    settings = {"part_number": "LC-6", "length_mils": 1400, "nominal_mils": 1400}
    settings.update(overrides)
    return Blade(**settings)


def test_a_blade_has_a_length():
    with pytest.raises(InputError):
        a_blade(length_mils=0)
    with pytest.raises(InputError):
        a_blade(nominal_mils=0)


def test_a_blade_reports_how_far_off_nominal_it_is():
    assert a_blade().is_nominal
    assert a_blade(length_mils=1406).error_mils == 6
    assert a_blade(length_mils=1394).error_mils == -6


def test_the_shoulder_positions_are_the_chart():
    assert shoulder_positions(six_pin_chart()) == six_pin_chart().spaces()


def test_the_tip_positions_measure_back_from_the_tip():
    found = tip_positions(six_pin_chart(), a_blade())
    assert found[0] == 1400 - 231
    assert found[-1] == 1400 - 1011


def test_the_tip_positions_stay_in_chamber_order():
    found = tip_positions(six_pin_chart(), a_blade())
    assert found == tuple(sorted(found, reverse=True))


def test_the_conversion_goes_back_where_it_came_from():
    chart = six_pin_chart()
    blade = a_blade()
    assert from_tip_to_shoulder(blade, tip_positions(chart, blade)) == shoulder_positions(chart)


def test_a_shoulder_gauged_family_uses_the_chart_positions():
    assert positions_for(six_pin_chart(), a_blade()) == six_pin_chart().spaces()


def test_a_shoulder_gauged_family_does_not_care_about_a_long_blank():
    chart = six_pin_chart()
    assert positions_for(chart, a_blade(length_mils=1406)) == chart.spaces()
    assert drift_on(chart, a_blade(length_mils=1406)) == 0


def test_a_tip_gauged_family_on_a_nominal_blank_lands_in_the_same_place():
    chart = six_pin_chart(gauged_from_tip=True)
    assert positions_for(chart, a_blade()) == chart.spaces()


def test_a_tip_gauged_family_drifts_on_a_long_blank():
    chart = six_pin_chart(gauged_from_tip=True)
    assert drift_on(chart, a_blade(length_mils=1406)) == 6


def test_a_tip_gauged_family_drifts_the_other_way_on_a_short_blank():
    chart = six_pin_chart(gauged_from_tip=True)
    assert drift_on(chart, a_blade(length_mils=1392)) == -8


def test_a_small_error_is_inside_the_allowance():
    chart = six_pin_chart(gauged_from_tip=True)
    assert within_tolerance(chart, a_blade(length_mils=1403))
    assert not within_tolerance(chart, a_blade(length_mils=1410))


def test_a_shoulder_gauged_family_is_always_inside_the_allowance():
    chart = six_pin_chart()
    assert within_tolerance(chart, a_blade(length_mils=1500))


def test_a_tighter_allowance_catches_more():
    chart = six_pin_chart(gauged_from_tip=True)
    assert not within_tolerance(chart, a_blade(length_mils=1403), allowance_mils=1)


def test_a_negative_allowance_is_refused():
    with pytest.raises(InputError):
        within_tolerance(six_pin_chart(), a_blade(), allowance_mils=-1)


def test_a_blade_needs_metal_past_the_last_chamber():
    assert blade_long_enough(six_pin_chart(), a_blade())
    assert not blade_long_enough(six_pin_chart(), a_blade(length_mils=1050))


def test_the_clearance_can_be_named():
    assert blade_long_enough(six_pin_chart(), a_blade(length_mils=1050), clearance_mils=30)


def test_a_good_blank_has_nothing_wrong_with_it():
    assert check_blade(six_pin_chart(), a_blade()) == ()


def test_a_short_blank_is_reported():
    found = check_blade(six_pin_chart(), a_blade(length_mils=1040))
    assert "too short" in found[0]


def test_a_long_blank_on_a_tip_gauged_family_is_reported():
    chart = six_pin_chart(gauged_from_tip=True)
    found = check_blade(chart, a_blade(length_mils=1412))
    assert "off nominal" in found[0]
    assert "gauges from the tip" in found[0]


def test_both_problems_are_reported_together():
    chart = six_pin_chart(gauged_from_tip=True)
    found = check_blade(chart, a_blade(length_mils=1040, nominal_mils=1400))
    assert len(found) == 2


def test_one_chamber_can_be_asked_for():
    assert position_of(six_pin_chart(), 1, a_blade()) == 231
    assert position_of(six_pin_chart(), 6, a_blade()) == 1011


@pytest.mark.parametrize("chamber", [0, 7, -1])
def test_a_chamber_off_the_end_is_refused(chamber):
    with pytest.raises(BittingError):
        position_of(six_pin_chart(), chamber, a_blade())

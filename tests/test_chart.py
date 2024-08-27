import pytest

from hasplib.errors import BittingError, SpecificationError
from hasplib.keys.bitting import Bitting
from hasplib.spec.chart import DepthAndSpaceChart


def a_six_pin_chart(**overrides):
    settings = {
        "manufacturer": "Schlage",
        "family": "C keyway, six pin",
        "chambers": 6,
        "depths": tuple(range(0, 10)),
        "root_mils": 335,
        "increment_mils": 15,
        "first_space_mils": 231,
        "space_increment_mils": 156,
        "macs": 7,
    }
    settings.update(overrides)
    return DepthAndSpaceChart(**settings)


def test_the_shallowest_cut_sits_at_the_root():
    chart = a_six_pin_chart()
    assert chart.dimension_for(0) == 335


def test_each_step_takes_one_increment_of_metal():
    chart = a_six_pin_chart()
    assert chart.dimension_for(1) == 320
    assert chart.dimension_for(9) == 200


def test_a_depth_the_chart_skipped_is_not_cuttable():
    chart = a_six_pin_chart(depths=(0, 1, 2, 3, 4, 6, 7, 8, 9))
    assert not chart.allows_depth(5)
    with pytest.raises(BittingError):
        chart.dimension_for(5)


def test_spacing_walks_along_the_blade():
    chart = a_six_pin_chart()
    assert chart.space_for(1) == 231
    assert chart.space_for(2) == 387
    assert chart.spaces() == (231, 387, 543, 699, 855, 1011)


@pytest.mark.parametrize("chamber", [0, 7, -3])
def test_asking_about_a_chamber_that_is_not_there(chamber):
    with pytest.raises(BittingError):
        a_six_pin_chart().space_for(chamber)


def test_a_measured_key_reads_back_to_a_depth():
    chart = a_six_pin_chart()
    assert chart.depth_for_dimension(320) == 1
    assert chart.depth_for_dimension(319) == 1
    assert chart.depth_for_dimension(322) == 1


def test_a_measurement_between_depths_is_refused():
    chart = a_six_pin_chart()
    with pytest.raises(BittingError):
        chart.depth_for_dimension(312)


def test_a_wider_tolerance_accepts_the_same_measurement():
    chart = a_six_pin_chart(depth_tolerance_mils=8)
    assert chart.depth_for_dimension(314) == 1


def test_a_key_of_the_right_shape_validates():
    chart = a_six_pin_chart()
    chart.validate(Bitting.parse("342156"))
    assert chart.is_cuttable(Bitting.parse("342156"))


def test_a_key_of_the_wrong_length_does_not():
    chart = a_six_pin_chart()
    with pytest.raises(BittingError) as caught:
        chart.validate(Bitting.parse("3421"))
    assert caught.value.facts["got"] == 4


def test_a_key_carrying_a_withdrawn_depth_does_not():
    chart = a_six_pin_chart(depths=(0, 1, 2, 3, 4, 6, 7, 8, 9))
    with pytest.raises(BittingError) as caught:
        chart.validate(Bitting.parse("342156"))
    assert caught.value.facts["chamber"] == 5
    assert not chart.is_cuttable(Bitting.parse("342156"))


def test_the_chart_reports_its_own_range():
    chart = a_six_pin_chart()
    assert chart.shallowest == 0
    assert chart.deepest == 9
    assert chart.depth_count == 10
    assert "six pin" in chart.describe()


@pytest.mark.parametrize(
    "broken",
    [
        {"chambers": 0},
        {"depths": ()},
        {"depths": (0, 2, 1)},
        {"depths": (0, 0, 1)},
        {"increment_mils": 0},
        {"space_increment_mils": -4},
        {"macs": 0},
        {"root_mils": 100},
    ],
)
def test_a_chart_that_contradicts_itself_is_refused(broken):
    with pytest.raises(SpecificationError):
        a_six_pin_chart(**broken)


def test_notes_do_not_change_what_a_chart_is():
    assert a_six_pin_chart(notes="from the 2011 catalogue") == a_six_pin_chart()

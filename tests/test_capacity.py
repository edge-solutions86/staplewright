import pytest

from charts import six_pin_chart
from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting
from hasplib.progression.array import KeyBittingArray
from hasplib.progression.capacity import (
    best_option,
    forecast,
    headroom,
    widening_options,
    years_left,
)
from hasplib.progression.plan import ProgressionPlan


def a_plan(**overrides) -> ProgressionPlan:
    settings = {
        "chart": six_pin_chart(macs=5),
        "master": Bitting.parse("342156"),
        "progressed": (2, 4),
        "step": 2,
    }
    settings.update(overrides)
    return ProgressionPlan(**settings)


def an_array(**overrides) -> KeyBittingArray:
    return KeyBittingArray(a_plan(**overrides))


def test_headroom_counts_what_is_left():
    room = headroom(an_array(), used=4)
    assert room.used == 4
    assert room.free == room.cuttable - 4
    assert room.capacity >= room.cuttable


def test_the_reserve_is_a_fraction_of_the_cuttable_keys():
    room = headroom(an_array(), used=0, reserve_fraction=0.5)
    assert room.reserve_wanted == room.cuttable // 2


def test_a_system_under_its_reserve_says_so():
    array = an_array()
    room = headroom(array, used=array.usable_count, reserve_fraction=0.2)
    assert room.under_reserve
    assert room.reserve_left < 0


def test_a_system_with_room_is_not_under_its_reserve():
    assert not headroom(an_array(), used=0).under_reserve


def test_a_full_system_has_no_free_keys():
    array = an_array()
    room = headroom(array, used=array.usable_count + 5)
    assert room.free == 0
    assert room.used_fraction > 1


def test_the_used_fraction_is_rounded_for_a_report():
    array = an_array()
    room = headroom(array, used=array.usable_count // 2)
    assert 0.4 < room.used_fraction < 0.6


def test_headroom_describes_itself():
    assert "free" in headroom(an_array(), used=3).describe()


@pytest.mark.parametrize("broken", [{"used": -1}, {"reserve_fraction": 1.0}])
def test_headroom_that_makes_no_sense_is_refused(broken):
    settings = {"used": 0}
    settings.update(broken)
    with pytest.raises(InputError):
        headroom(an_array(), **settings)


def test_the_years_left_fall_out_of_the_rate():
    room = headroom(an_array(), used=0, reserve_fraction=0)
    assert years_left(room, room.free // 4) == 4


def test_a_system_nobody_is_growing_does_not_run_out():
    assert years_left(headroom(an_array(), used=0), 0) is None


def test_a_system_already_past_its_reserve_has_no_years():
    array = an_array()
    room = headroom(array, used=array.usable_count)
    assert years_left(room, 5) == 0


def test_a_negative_rate_is_refused():
    with pytest.raises(InputError):
        years_left(headroom(an_array(), used=0), -1)


def test_the_forecast_walks_forward_at_this_years_rate():
    assert forecast(10, 6, 4) == 34
    assert forecast(10, 0, 4) == 10


def test_a_forecast_cannot_run_backward():
    with pytest.raises(InputError):
        forecast(10, 6, -1)


def test_progressing_another_chamber_is_offered():
    options = widening_options(a_plan())
    names = [option.name for option in options]
    assert any(name.startswith("progress chamber") for name in names)


def test_every_option_is_wider_than_the_plan():
    plan = a_plan()
    current = KeyBittingArray(plan).usable_count
    for option in widening_options(plan):
        assert option.gain_over(current) > 0


def test_a_chamber_already_progressed_is_not_offered_again():
    options = widening_options(a_plan())
    assert not any(option.name == "progress chamber 2" for option in options)


def test_progressing_a_chamber_costs_a_wafer():
    options = widening_options(a_plan())
    chamber = next(o for o in options if o.name.startswith("progress chamber"))
    assert chamber.wafers_added == 1


def test_a_tighter_step_is_offered_and_costs_no_wafer():
    options = widening_options(a_plan())
    tighter = [o for o in options if o.name.startswith("progress 1 steps")]
    assert tighter
    assert tighter[0].wafers_added == 0
    assert tighter[0].thinnest_split == 15


def test_a_one_step_plan_offers_no_tighter_step():
    options = widening_options(a_plan(step=1))
    assert not any("steps instead" in option.name for option in options)


def test_the_widest_option_comes_first():
    options = widening_options(a_plan())
    assert [o.capacity for o in options] == sorted((o.capacity for o in options), reverse=True)


def test_the_best_option_respects_the_thinnest_wafer():
    options = widening_options(a_plan())
    picked = best_option(options, thinnest_allowed=30)
    assert picked is not None
    assert picked.thinnest_split >= 30


def test_nothing_workable_comes_back_as_nothing():
    options = widening_options(a_plan())
    assert best_option(options, thinnest_allowed=500) is None


def test_a_plan_with_nothing_left_to_widen_offers_nothing():
    plan = a_plan(progressed=(1, 2, 3, 4, 5, 6), step=1)
    assert widening_options(plan) == ()

import pytest

from charts import five_pin_chart, six_pin_chart
from hasplib.errors import ProgressionError
from hasplib.keys.bitting import Bitting, parse_pattern
from hasplib.progression.plan import ProgressionPlan, available_depths, plan_from_pattern


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_plan(**overrides) -> ProgressionPlan:
    settings = {
        "chart": six_pin_chart(),
        "master": b("342156"),
        "progressed": (2, 4, 6),
        "step": 2,
    }
    settings.update(overrides)
    return ProgressionPlan(**settings)


def test_two_step_depths_come_off_the_master_in_both_directions():
    assert available_depths(six_pin_chart(), 4, 2) == (0, 2, 6, 8)


def test_the_master_cut_is_never_in_its_own_list():
    assert 4 not in available_depths(six_pin_chart(), 4, 2)


def test_one_step_progression_uses_every_other_depth_on_the_chart():
    assert available_depths(six_pin_chart(), 4, 1) == (0, 1, 2, 3, 5, 6, 7, 8, 9)


def test_a_master_cut_at_the_end_of_the_chart_has_room_one_way_only():
    assert available_depths(six_pin_chart(), 0, 2) == (2, 4, 6, 8)
    assert available_depths(six_pin_chart(), 9, 2) == (1, 3, 5, 7)


def test_a_chart_with_a_hole_in_it_loses_that_depth():
    chart = six_pin_chart(depths=(0, 1, 2, 3, 4, 5, 7, 8, 9))
    assert available_depths(chart, 4, 2) == (0, 2, 8)


def test_a_depth_the_chart_never_published_is_refused():
    with pytest.raises(ProgressionError):
        available_depths(six_pin_chart(depths=(0, 1, 2)), 7, 2)


def test_a_step_of_zero_is_refused():
    with pytest.raises(ProgressionError):
        available_depths(six_pin_chart(), 4, 0)


def test_constants_are_everything_the_plan_did_not_name():
    assert a_plan().constants == (1, 3, 5)


def test_a_constant_chamber_offers_only_the_master_cut():
    plan = a_plan()
    assert plan.depths_in(1) == (3,)
    assert plan.depths_in(3) == (2,)


def test_a_progressed_chamber_offers_its_whole_run():
    assert a_plan().depths_in(2) == (0, 2, 6, 8)


def test_capacity_is_the_product_of_the_moving_chambers():
    plan = a_plan()
    assert len(plan.depths_in(2)) == 4
    assert len(plan.depths_in(4)) == 4
    assert len(plan.depths_in(6)) == 4
    assert plan.capacity == 64


def test_capacity_shrinks_when_a_master_cut_sits_at_the_end_of_the_chart():
    assert a_plan(master=b("340150")).capacity == 4 * 4 * 4
    assert a_plan(master=b("342156"), progressed=(2,)).capacity == 4


def test_every_combination_is_produced_once():
    keys = list(a_plan().combinations())
    assert len(keys) == 64
    assert len({key.depths for key in keys}) == 64


def test_the_leftmost_moving_chamber_turns_over_slowest():
    keys = [key.render() for key in a_plan(progressed=(2, 6)).combinations()]
    assert keys[:5] == ["302150", "302152", "302154", "302158", "322150"]


def test_the_constants_never_move():
    for key in a_plan().combinations():
        assert key.at(1) == 3
        assert key.at(3) == 2
        assert key.at(5) == 5


def test_the_master_itself_is_not_one_of_the_combinations():
    assert b("342156") not in list(a_plan().combinations())


def test_a_plan_recognises_its_own_keys():
    plan = a_plan()
    assert plan.holds(b("302350"))
    assert not plan.holds(b("312350"))
    assert not plan.holds(b("402350"))
    assert not plan.holds(b("342156"))
    assert not plan.holds(b("3023"))


def test_the_order_names_the_fastest_chamber_first():
    assert a_plan().order() == (6, 4, 2)


def test_a_master_of_the_wrong_length_is_refused():
    with pytest.raises(ProgressionError):
        a_plan(master=b("3421"))


def test_a_plan_with_nothing_moving_is_refused():
    with pytest.raises(ProgressionError):
        a_plan(progressed=())


def test_a_chamber_named_twice_is_refused():
    with pytest.raises(ProgressionError):
        a_plan(progressed=(2, 2, 4))


@pytest.mark.parametrize("chamber", [0, 7, -1])
def test_a_chamber_off_the_end_is_refused(chamber):
    with pytest.raises(ProgressionError):
        a_plan(progressed=(chamber,))


def test_a_chamber_with_nowhere_to_go_is_refused():
    chart = five_pin_chart(depths=(0, 1, 2))
    with pytest.raises(ProgressionError) as caught:
        ProgressionPlan(chart, b("10101"), (1,), step=4)
    assert caught.value.facts["chamber"] == 1


def test_a_pattern_builds_the_plan_the_schedule_drew():
    plan = plan_from_pattern(six_pin_chart(), b("342156"), parse_pattern("3x2x5x", 6))
    assert plan.progressed == (2, 4, 6)
    assert plan.capacity == 64


def test_a_pattern_of_the_wrong_length_is_refused():
    with pytest.raises(ProgressionError):
        plan_from_pattern(six_pin_chart(), b("342156"), parse_pattern("3x2x", 4))


def test_the_plan_describes_itself_for_the_cover_sheet():
    assert a_plan().describe() == "master 342156, chambers 2,4,6 progressed 2 steps, holds 64"

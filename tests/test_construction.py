import pytest

from charts import six_pin_chart
from hasplib.errors import ConflictError, InputError
from hasplib.keys.bitting import Bitting
from hasplib.rekey.construction import (
    BallKeying,
    CoreSwap,
    build_construction_key,
    swap_plan,
    unswapped,
    workable_ball_chambers,
)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_keying(**overrides) -> BallKeying:
    settings = {
        "permanent": b("342156"),
        "construction": b("542156"),
        "ball_chambers": (1,),
        "ball_steps": 2,
    }
    settings.update(overrides)
    return BallKeying(**settings)


def test_a_sound_arrangement_has_nothing_to_report():
    keying = a_keying()
    assert keying.is_sound
    assert keying.problems() == ()
    assert keying.voided_by_first_use()


def test_the_chambers_without_a_ball_have_to_match():
    keying = a_keying(construction=b("542356"))
    assert "chamber 4" in keying.problems()[0]
    assert not keying.is_sound


def test_a_construction_cut_shallower_than_the_permanent_one_is_wrong():
    keying = a_keying(construction=b("142156"))
    assert "deeper" in keying.problems()[0]


def test_a_construction_cut_the_same_as_the_permanent_one_is_wrong():
    keying = a_keying(construction=b("342156"))
    assert keying.problems()
    assert not keying.voided_by_first_use()


def test_the_step_has_to_be_the_ball():
    keying = a_keying(construction=b("742156"))
    assert "worth 2" in keying.problems()[0]


def test_a_bigger_ball_can_be_declared():
    keying = a_keying(construction=b("742156"), ball_steps=4)
    assert keying.is_sound


def test_two_ball_chambers_both_have_to_be_right():
    keying = a_keying(construction=b("542356"), ball_chambers=(1, 4))
    assert keying.is_sound
    assert keying.plain_chambers == (2, 3, 5, 6)


def test_a_cylinder_with_no_ball_is_not_construction_keyed():
    with pytest.raises(ConflictError):
        a_keying(ball_chambers=())


def test_a_ball_in_a_chamber_that_is_not_there():
    with pytest.raises(ConflictError):
        a_keying(ball_chambers=(9,))


def test_two_keys_of_different_lengths_are_refused():
    with pytest.raises(ConflictError):
        a_keying(construction=b("5421"))


def test_a_ball_worth_nothing_is_refused():
    with pytest.raises(InputError):
        a_keying(ball_steps=0)


def test_a_construction_key_is_cut_from_the_permanent_one():
    made = build_construction_key(six_pin_chart(), b("342156"), [1])
    assert made.render() == "542156"


def test_two_ball_chambers_both_move():
    made = build_construction_key(six_pin_chart(), b("342156"), [1, 4])
    assert made.render() == "542356"


def test_a_permanent_cut_near_the_bottom_leaves_no_room():
    with pytest.raises(ConflictError) as caught:
        build_construction_key(six_pin_chart(), b("942156"), [1])
    assert caught.value.facts["wanted"] == 11


def test_a_ball_chamber_that_is_not_on_the_key():
    with pytest.raises(ConflictError):
        build_construction_key(six_pin_chart(), b("342156"), [9])


def test_the_workable_chambers_are_the_ones_with_room():
    workable = workable_ball_chambers(six_pin_chart(), b("342156"))
    assert workable == (1, 2, 3, 4, 5, 6)


def test_a_key_cut_deep_loses_chambers():
    workable = workable_ball_chambers(six_pin_chart(), b("998156"))
    assert workable == (4, 5, 6)


def test_a_wider_ball_loses_more_chambers():
    assert workable_ball_chambers(six_pin_chart(), b("342156"), ball_steps=6) == (1, 3, 4)


def test_a_swap_lines_up_both_cores_against_a_door():
    plan = swap_plan(["MOO-214", "MOO-216"], ["X-1", "X-2"], ["K-1", "K-2"])
    assert [item.opening for item in plan] == ["MOO-214", "MOO-216"]
    assert plan[0].permanent_core == "K-1"


def test_a_door_cannot_swap_a_core_for_itself():
    with pytest.raises(ConflictError):
        CoreSwap("MOO-214", "K-1", "K-1")


def test_every_door_needs_both_cores():
    with pytest.raises(InputError):
        swap_plan(["MOO-214", "MOO-216"], ["X-1"], ["K-1", "K-2"])


def test_a_permanent_core_cannot_be_promised_twice():
    with pytest.raises(ConflictError):
        swap_plan(["MOO-214", "MOO-216"], ["X-1", "X-2"], ["K-1", "K-1"])


def test_the_doors_still_on_construction_cores_are_reported():
    doors = ["MOO-214", "MOO-216", "MOO-218"]
    plan = swap_plan(doors, ["X-1", "X-2", "X-3"], ["K-1", "K-2", "K-3"])
    assert unswapped(plan, ["MOO-216"]) == ("MOO-214", "MOO-218")


def test_a_finished_handover_leaves_nothing():
    plan = swap_plan(["MOO-214"], ["X-1"], ["K-1"])
    assert unswapped(plan, ["MOO-214"]) == ()

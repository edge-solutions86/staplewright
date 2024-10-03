import pytest

from charts import six_pin_chart
from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting
from hasplib.progression.incidental import (
    break_depths,
    cross_keying_cost,
    cuttable_incidentals,
    incidental_count,
    incidental_keys,
    is_clean,
    operating_count,
    operating_keys,
    worst_chamber,
)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def test_one_key_alone_breaks_once_a_chamber():
    breaks = break_depths([b("342156")])
    assert all(len(depths) == 1 for depths in breaks.values())
    assert operating_count([b("342156")]) == 1
    assert is_clean([b("342156")])


def test_a_master_and_one_change_key_split_where_they_differ():
    breaks = break_depths([b("342156"), b("362156")])
    assert breaks[2] == (4, 6)
    assert breaks[1] == (3,)


def test_a_single_split_makes_no_incidental_keys():
    keys = [b("342156"), b("362156")]
    assert operating_count(keys) == 2
    assert incidental_count(keys) == 0
    assert is_clean(keys)


def test_two_splits_give_away_two_keys_nobody_drew():
    keys = [b("342156"), b("362356")]
    assert operating_count(keys) == 4
    assert incidental_count(keys) == 2
    found = {key.render() for key in incidental_keys(keys)}
    assert found == {"342356", "362156"}


def test_the_intended_keys_are_never_in_the_incidental_list():
    keys = [b("342156"), b("362356"), b("382556")]
    intended = {key.depths for key in keys}
    for key in incidental_keys(keys):
        assert key.depths not in intended


def test_three_keys_over_two_chambers_hold_nine_combinations():
    keys = [b("342156"), b("362356"), b("382556")]
    assert operating_count(keys) == 9
    assert incidental_count(keys) == 6


def test_every_operating_key_is_produced_once():
    keys = [b("342156"), b("362356")]
    produced = [key.render() for key in operating_keys(keys)]
    assert len(produced) == len(set(produced)) == 4


def test_the_worst_chamber_is_the_one_carrying_the_most_breaks():
    keys = [b("342156"), b("362156"), b("382156"), b("342356")]
    assert worst_chamber(keys) == 2


def test_the_shallowest_chamber_wins_a_tie():
    keys = [b("342156"), b("362356")]
    assert worst_chamber(keys) == 2


def test_a_cylinder_with_no_keys_is_refused():
    with pytest.raises(InputError):
        break_depths([])


def test_keys_of_different_lengths_are_refused():
    with pytest.raises(InputError):
        operating_count([b("342156"), b("3421")])


def test_an_incidental_key_that_breaks_macs_cannot_be_cut():
    keys = [b("342156"), b("392856")]
    assert incidental_count(keys) == 2
    cuttable = cuttable_incidentals(six_pin_chart(macs=3), keys)
    assert len(cuttable) < len(incidental_keys(keys))


def test_a_loose_chart_leaves_every_incidental_key_cuttable():
    keys = [b("342156"), b("362356")]
    cuttable = cuttable_incidentals(six_pin_chart(macs=9), keys)
    assert len(cuttable) == incidental_count(keys)


def test_adding_a_key_to_a_cylinder_costs_what_it_costs():
    intended = [b("342156"), b("362156")]
    assert cross_keying_cost(intended, b("362356")) == 1


def test_a_cross_key_that_lines_up_costs_nothing():
    intended = [b("342156"), b("362156")]
    assert cross_keying_cost(intended, b("382156")) == 0


def test_cross_keying_an_unrelated_key_is_expensive():
    intended = [b("342156"), b("362156")]
    cost = cross_keying_cost(intended, b("563748"))
    assert cost > 20

import pytest

from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting
from hasplib.keys.relations import (
    compare,
    duplicate_bittings,
    keys_are_distinct,
    shallowest_split,
    steps_are_multiples_of,
    wafer_count,
    wafers_for,
)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def test_two_keys_cut_the_same_split_nowhere():
    report = compare(b("342156"), b("342156"))
    assert report.is_identical
    assert report.split_count == 0
    assert report.maintained_chambers == (1, 2, 3, 4, 5, 6)


def test_a_split_names_both_depths_and_the_step():
    report = compare(b("342156"), b("362156"))
    assert report.split_chambers == (2,)
    split = report.splits[0]
    assert (split.above, split.below, split.step) == (4, 6, 2)
    assert split.below_is_deeper


def test_a_change_key_cut_shallower_reads_the_other_way():
    split = compare(b("362156"), b("342156")).splits[0]
    assert not split.below_is_deeper
    assert split.step == 2


def test_the_chambers_that_are_not_split_are_the_ones_left_over():
    report = compare(b("342156"), b("362356"))
    assert report.split_chambers == (2, 4)
    assert report.maintained_chambers == (1, 3, 5, 6)


def test_the_wafer_count_is_the_split_count():
    assert wafer_count(b("342156"), b("362356")) == 2
    assert wafer_count(b("342156"), b("342156")) == 0


def test_asking_about_a_chamber_that_did_not_split():
    report = compare(b("342156"), b("362156"))
    assert report.step_in(2) == 2
    assert report.step_in(1) == 0


def test_keys_of_different_lengths_are_not_one_system():
    with pytest.raises(InputError):
        compare(b("3421"), b("342156"))


def test_a_cylinder_breaks_at_every_depth_any_of_its_keys_cuts():
    breaks = wafers_for(b("342156"), [b("362156"), b("342356")])
    assert breaks[1] == (3,)
    assert breaks[2] == (4, 6)
    assert breaks[4] == (1, 3)
    assert breaks[6] == (6,)


def test_a_master_on_its_own_breaks_once_a_chamber():
    breaks = wafers_for(b("342156"), [])
    assert all(len(depths) == 1 for depths in breaks.values())


def test_break_depths_come_back_shallow_to_deep():
    breaks = wafers_for(b("542156"), [b("142156"), b("342156")])
    assert breaks[1] == (1, 3, 5)


def test_a_change_key_of_the_wrong_length_is_refused():
    with pytest.raises(InputError):
        wafers_for(b("342156"), [b("3421")])


def test_two_step_progression_passes_when_every_split_is_even():
    assert steps_are_multiples_of(b("342156"), b("362356"), 2)
    assert not steps_are_multiples_of(b("342156"), b("352156"), 2)


def test_every_key_is_a_multiple_of_one_step():
    assert steps_are_multiples_of(b("342156"), b("352156"), 1)


def test_a_step_of_zero_is_refused():
    with pytest.raises(InputError):
        steps_are_multiples_of(b("342156"), b("352156"), 0)


def test_the_tightest_split_is_the_thin_wafer():
    assert shallowest_split(b("342156"), b("372356")) == 2
    assert shallowest_split(b("342156"), b("342156")) is None


def test_a_set_of_keys_reports_its_own_duplicates():
    keys = [b("342156"), b("362156"), b("342156")]
    assert not keys_are_distinct(keys)
    assert duplicate_bittings(keys) == (b("342156"),)


def test_a_clean_set_has_no_duplicates():
    keys = [b("342156"), b("362156")]
    assert keys_are_distinct(keys)
    assert duplicate_bittings(keys) == ()

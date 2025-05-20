import pytest

from charts import six_pin_chart
from hasplib.errors import CapacityError, ProgressionError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.array import KeyBittingArray
from hasplib.progression.blocks import (
    by_blocks,
    by_rotating_constant,
    constant_depths,
    is_partition,
    master_of,
    unallocated,
    waste,
)
from hasplib.progression.plan import ProgressionPlan


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def an_array() -> KeyBittingArray:
    plan = ProgressionPlan(six_pin_chart(macs=5), Bitting.parse("342156"), (2, 4, 6), 2)
    return KeyBittingArray(plan)


def masters(count: int) -> list[KeySymbol]:
    return [sym(f"A{letter}") for letter in "ABCDEFGH"[:count]]


def test_blocks_hand_out_runs_in_order():
    array = an_array()
    found = by_blocks(array, masters(3), 10)
    assert [item.symbol.render() for item in found] == ["AA", "AB", "AC"]
    assert found[0].keys == array.blocks(10)[0]


def test_a_block_allocation_names_no_constant():
    found = by_blocks(an_array(), masters(2), 10)
    assert found[0].constant_chamber is None
    assert found[0].describe().endswith("10 keys")


def test_more_masters_than_blocks_is_refused():
    with pytest.raises(CapacityError):
        by_blocks(an_array(), masters(8), 30)


def test_a_master_cannot_take_two_blocks():
    array = an_array()
    with pytest.raises(ProgressionError):
        by_blocks(array, [sym("AA"), sym("AA")], 10)


def test_the_constant_depths_are_what_the_array_uses():
    assert constant_depths(an_array(), 2) == (0, 2, 6)


def test_a_chamber_outside_the_family_is_refused():
    with pytest.raises(ProgressionError):
        constant_depths(an_array(), 9)


def test_a_rotating_constant_gives_each_master_one_depth():
    found = by_rotating_constant(an_array(), masters(3), 2)
    assert [item.constant_depth for item in found] == [0, 2, 6]
    assert all(item.constant_chamber == 2 for item in found)


def test_every_key_in_an_allocation_carries_the_constant():
    found = by_rotating_constant(an_array(), masters(2), 2)
    for allocation in found:
        assert all(key.at(2) == allocation.constant_depth for key in allocation.keys)


def test_a_rotating_allocation_describes_its_constant():
    found = by_rotating_constant(an_array(), masters(1), 2)
    assert "chamber 2 held at 0" in found[0].describe()


def test_more_masters_than_depths_is_refused():
    with pytest.raises(CapacityError) as caught:
        by_rotating_constant(an_array(), masters(8), 2)
    assert caught.value.facts["depths"] == 3


def test_a_master_cannot_take_two_depths():
    with pytest.raises(ProgressionError):
        by_rotating_constant(an_array(), [sym("AA"), sym("AA")], 2)


def test_a_rotating_constant_partitions_the_whole_array():
    array = an_array()
    found = by_rotating_constant(array, masters(3), 2)
    assert is_partition(array, found)
    assert waste(array, found) == 0


def test_blocks_leave_the_tail_of_the_array_unallocated():
    array = an_array()
    found = by_blocks(array, masters(2), 10)
    assert waste(array, found) == array.usable_count - 20
    assert not is_partition(array, found)


def test_the_unallocated_keys_are_the_ones_nobody_got():
    array = an_array()
    found = by_blocks(array, masters(1), 10)
    left = unallocated(array, found)
    assert len(left) == array.usable_count - 10
    assert all(key not in found[0].keys for key in left)


def test_a_key_reads_back_to_its_master():
    array = an_array()
    found = by_rotating_constant(array, masters(3), 2)
    key = found[1].keys[0]
    assert master_of(found, key) == found[1].symbol


def test_a_key_nobody_was_given_has_no_master():
    array = an_array()
    found = by_blocks(array, masters(1), 10)
    assert master_of(found, unallocated(array, found)[0]) is None


def test_the_two_methods_hand_out_different_sets():
    array = an_array()
    blocks = by_blocks(array, masters(3), 10)
    rotating = by_rotating_constant(array, masters(3), 2)
    assert blocks[0].keys != rotating[0].keys

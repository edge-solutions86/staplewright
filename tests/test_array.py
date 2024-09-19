import pytest

from charts import six_pin_chart
from hasplib.errors import CapacityError, ProgressionError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.array import KeyBittingArray, assign_blocks
from hasplib.progression.plan import ProgressionPlan
from hasplib.spec.rules import CuttingRules


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def an_array(**overrides) -> KeyBittingArray:
    plan = ProgressionPlan(
        overrides.get("chart", six_pin_chart(macs=5)),
        overrides.get("master", b("342156")),
        overrides.get("progressed", (2, 4, 6)),
        overrides.get("step", 2),
    )
    return KeyBittingArray(plan, overrides.get("rules", CuttingRules()))


def test_the_array_is_the_plan_minus_what_the_rules_refuse():
    array = an_array()
    assert array.capacity == 64
    assert array.usable_count + array.loss == 64
    assert array.usable_count < 64


def test_every_key_in_the_array_passes_the_rules():
    array = an_array()
    for key in array:
        assert array.plan.holds(key)
    assert len(set(array.usable)) == array.usable_count


def test_nothing_the_rules_refused_is_in_the_usable_list():
    array = an_array()
    for key, _ in array.rejected:
        assert not array.holds(key)


def test_the_reasons_are_counted_worst_first():
    reasons = an_array().rejection_reasons()
    assert "macs" in reasons
    assert list(reasons.values()) == sorted(reasons.values(), reverse=True)


def test_a_looser_macs_throws_away_fewer_keys():
    tight = an_array(chart=six_pin_chart(macs=3))
    loose = an_array(chart=six_pin_chart(macs=9))
    assert tight.usable_count < loose.usable_count
    assert loose.loss == 0


def test_blocks_come_off_in_printed_order():
    array = an_array()
    blocks = array.blocks(10)
    assert blocks[0] == array.usable[:10]
    assert blocks[1] == array.usable[10:20]


def test_the_last_block_is_short_rather_than_dropped():
    array = an_array()
    blocks = array.blocks(10)
    assert sum(len(block) for block in blocks) == array.usable_count
    assert len(blocks[-1]) <= 10


def test_a_block_of_one_is_one_block_a_key():
    array = an_array()
    assert array.block_count(1) == array.usable_count


def test_a_block_bigger_than_the_array_is_one_block():
    array = an_array()
    assert array.block_count(500) == 1


def test_a_key_reports_the_block_it_sits_in():
    array = an_array()
    first = array.usable[0]
    eleventh = array.usable[10]
    assert array.block_holding(first, 10) == 1
    assert array.block_holding(eleventh, 10) == 2


def test_a_key_that_is_not_in_the_array_sits_in_no_block():
    assert an_array().block_holding(b("111111"), 10) is None


def test_a_block_size_of_zero_is_refused():
    with pytest.raises(ProgressionError):
        an_array().blocks(0)


def test_taking_keys_walks_the_array_in_order():
    array = an_array()
    assert array.take(3) == array.usable[:3]
    assert array.take(3, skip=3) == array.usable[3:6]


def test_taking_more_than_is_left_is_refused():
    array = an_array()
    with pytest.raises(CapacityError) as caught:
        array.take(5, skip=array.usable_count - 2)
    assert caught.value.facts["left"] == 2


def test_taking_nothing_is_allowed():
    assert an_array().take(0) == ()


def test_taking_a_negative_run_is_refused():
    with pytest.raises(ProgressionError):
        an_array().take(-1)


def test_each_master_gets_its_own_block():
    array = an_array()
    symbols = [KeySymbol.parse(text) for text in ["AA", "AB", "AC"]]
    handed = assign_blocks(array, symbols, 10)
    assert list(handed) == symbols
    assert handed[symbols[0]] == array.blocks(10)[0]
    assert handed[symbols[1]] == array.blocks(10)[1]


def test_no_key_is_handed_to_two_masters():
    array = an_array()
    symbols = [KeySymbol.parse(text) for text in ["AA", "AB", "AC"]]
    handed = assign_blocks(array, symbols, 10)
    seen = [key for block in handed.values() for key in block]
    assert len(seen) == len(set(seen))


def test_more_masters_than_blocks_is_refused():
    array = an_array()
    symbols = [KeySymbol.parse(f"A{letter}") for letter in "ABCDEFGHIJ"]
    with pytest.raises(CapacityError) as caught:
        assign_blocks(array, symbols, 40)
    assert caught.value.facts["wanted"] == 10


def test_the_same_master_cannot_take_two_blocks():
    array = an_array()
    symbol = KeySymbol.parse("AA")
    with pytest.raises(ProgressionError):
        assign_blocks(array, [symbol, symbol], 10)


def test_the_array_describes_its_own_losses():
    array = an_array()
    described = array.describe()
    assert str(array.usable_count) in described
    assert str(array.loss) in described

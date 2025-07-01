import pytest

from charts import five_pin_chart, six_pin_chart
from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting
from hasplib.progression.cache import (
    ArrayCache,
    CacheStats,
    array_for,
    cache_key,
    shared_cache,
)
from hasplib.progression.plan import ProgressionPlan
from hasplib.spec.rules import CuttingRules


def a_plan(**overrides) -> ProgressionPlan:
    settings = {
        "chart": six_pin_chart(macs=5),
        "master": Bitting.parse("342156"),
        "progressed": (2, 4),
        "step": 2,
    }
    settings.update(overrides)
    return ProgressionPlan(**settings)


def test_the_key_covers_the_plan_and_the_policy():
    first = cache_key(a_plan(), CuttingRules())
    second = cache_key(a_plan(), CuttingRules(max_run=1))
    assert first != second


def test_two_equal_plans_share_a_key():
    assert cache_key(a_plan(), CuttingRules()) == cache_key(a_plan(), CuttingRules())


def test_the_chamber_order_does_not_change_the_key():
    first = cache_key(a_plan(progressed=(2, 4)), CuttingRules())
    second = cache_key(a_plan(progressed=(4, 2)), CuttingRules())
    assert first == second


def test_a_different_master_is_a_different_key():
    first = cache_key(a_plan(), CuttingRules())
    second = cache_key(a_plan(master=Bitting.parse("542156")), CuttingRules())
    assert first != second


def test_a_different_chart_is_a_different_key():
    first = cache_key(a_plan(), CuttingRules())
    second = cache_key(
        ProgressionPlan(five_pin_chart(), Bitting.parse("13024"), (2,), 2), CuttingRules()
    )
    assert first != second


def test_the_tip_rule_reaches_the_key():
    first = cache_key(a_plan(), CuttingRules())
    second = cache_key(a_plan(), CuttingRules(shallow_tip_limit=4))
    assert first != second


def test_the_first_look_builds_and_the_second_does_not():
    cache = ArrayCache()
    first = cache.get(a_plan())
    second = cache.get(a_plan())
    assert first is second
    assert cache.stats.hits == 1
    assert cache.stats.misses == 1


def test_a_cache_reports_whether_it_holds_something():
    cache = ArrayCache()
    assert not cache.holds(a_plan())
    cache.get(a_plan())
    assert cache.holds(a_plan())


def test_a_different_policy_builds_again():
    cache = ArrayCache()
    first = cache.get(a_plan(), CuttingRules())
    second = cache.get(a_plan(), CuttingRules(max_run=1))
    assert first is not second
    assert cache.stats.misses == 2


def test_the_oldest_goes_out_when_it_is_full():
    cache = ArrayCache(size=2)
    cache.get(a_plan())
    cache.get(a_plan(master=Bitting.parse("542156")))
    cache.get(a_plan(master=Bitting.parse("742156")))
    assert len(cache) == 2
    assert cache.stats.evictions == 1
    assert not cache.holds(a_plan())


def test_a_hit_moves_something_back_to_the_front():
    cache = ArrayCache(size=2)
    cache.get(a_plan())
    cache.get(a_plan(master=Bitting.parse("542156")))
    cache.get(a_plan())
    cache.get(a_plan(master=Bitting.parse("742156")))
    assert cache.holds(a_plan())
    assert not cache.holds(a_plan(master=Bitting.parse("542156")))


def test_a_cache_of_nothing_is_refused():
    with pytest.raises(InputError):
        ArrayCache(size=0)


def test_clearing_empties_it_and_keeps_the_counters():
    cache = ArrayCache()
    cache.get(a_plan())
    cache.clear()
    assert len(cache) == 0
    assert cache.stats.misses == 1


def test_the_keys_can_be_listed():
    cache = ArrayCache()
    cache.get(a_plan())
    assert len(cache.keys()) == 1
    assert cache.keys()[0][1] == "342156"


def test_the_stats_read_as_a_rate():
    stats = CacheStats(hits=3, misses=1)
    assert stats.lookups == 4
    assert stats.hit_rate == 0.75
    assert "rate 0.75" in stats.describe()


def test_a_cache_nobody_has_used_has_no_rate():
    assert CacheStats().hit_rate == 0.0


def test_the_shared_cache_is_one_object():
    assert shared_cache() is shared_cache()


def test_the_helper_goes_through_the_shared_cache():
    shared_cache().clear()
    first = array_for(a_plan())
    second = array_for(a_plan())
    assert first is second

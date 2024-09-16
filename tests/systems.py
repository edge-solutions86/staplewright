"""Whole systems the tests build on, one step up from a bare chart."""

from charts import six_pin_chart
from hasplib.keys.schedule import KeyingSchedule
from hasplib.keys.symbol import KeySymbol
from hasplib.spec.keyway import KeywayFamily, KeywaySection
from hasplib.spec.rules import CuttingRules, RuleSet


def campus_family() -> KeywayFamily:
    family = KeywayFamily("Sargent LA", KeywaySection("L", "Sargent"))
    family.add(KeywaySection("LA", "Sargent"), under="L")
    family.add(KeywaySection("LB", "Sargent"), under="L")
    family.add(KeywaySection("LC", "Sargent", restricted=True), under="LA")
    family.add(KeywaySection("LD", "Sargent", restricted=True), under="LA")
    family.add(KeywaySection("LE", "Sargent"), under="LB")
    return family


def empty_schedule(**rule_overrides) -> KeyingSchedule:
    ruleset = RuleSet(six_pin_chart(), CuttingRules(**rule_overrides))
    return KeyingSchedule("chester park campus", ruleset, campus_family(), "L")


def small_schedule() -> KeyingSchedule:
    """Two grand masters, two masters under one of them, four change keys."""
    schedule = empty_schedule()
    schedule.add(KeySymbol.parse("A"), section="LA")
    schedule.add(KeySymbol.parse("B"), section="LB")
    schedule.add(KeySymbol.parse("AA"), section="LC")
    schedule.add(KeySymbol.parse("AB"), section="LD")
    for number in (1, 2):
        schedule.add(KeySymbol.parse(f"AA{number}"))
        schedule.add(KeySymbol.parse(f"AB{number}"))
    return schedule

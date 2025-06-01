import pytest

from hasplib.errors import NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.pinning.core import build_core_stack
from hasplib.pinning.cylinder import pin_cylinder
from hasplib.spec.catalog import (
    BEST_A2,
    CHARTS,
    CORBIN_60,
    FAMILIES,
    SARGENT_LA,
    catalogue,
    core_families,
    families_for,
    family,
    manufacturers,
)


def test_every_chart_is_internally_consistent():
    for chart in CHARTS:
        assert chart.deepest in chart.depths
        assert chart.dimension_for(chart.deepest) > 0
        assert len(chart.spaces()) == chart.chambers


def test_the_charts_are_named_once_each():
    keys = [(chart.manufacturer, chart.family) for chart in CHARTS]
    assert len(keys) == len(set(keys))


def test_a_withdrawn_depth_is_missing_from_that_chart():
    withdrawn = next(chart for chart in CHARTS if "withdrawn" in chart.family)
    assert 4 not in withdrawn.depths
    assert not withdrawn.allows_depth(4)


def test_the_padlock_chart_has_the_tight_macs():
    assert CORBIN_60.macs == 4
    assert CORBIN_60.chambers == 5


def test_the_core_chart_is_seven_chambers():
    assert BEST_A2.chambers == 7
    assert BEST_A2.increment_mils == 25


def test_the_catalogue_is_keyed_the_way_a_part_number_reads():
    found = catalogue()
    assert "Sargent/LA six pin" in found
    assert found["Sargent/LA six pin"].chart is SARGENT_LA


def test_a_family_can_be_asked_for_by_name():
    found = family("Best", "A2 seven pin core")
    assert found.chart is BEST_A2
    assert found.takes_cores


def test_a_family_nobody_stocks():
    with pytest.raises(NotFoundError):
        family("Medeco", "biaxial")


def test_the_families_of_one_manufacturer_come_back_together():
    found = families_for("Sargent")
    assert len(found) == 2
    assert all(item.chart.manufacturer == "Sargent" for item in found)


def test_a_manufacturer_nobody_stocks_has_no_families():
    assert families_for("Medeco") == ()


def test_the_manufacturers_are_listed_once_each():
    assert manufacturers() == ("Best", "Corbin", "Sargent", "Schlage")


def test_only_the_core_family_takes_cores():
    found = core_families()
    assert [item.chart.manufacturer for item in found] == ["Best"]
    assert found[0].core is not None


def test_every_family_carries_a_pinning_spec_for_its_own_chart():
    for item in FAMILIES:
        assert item.pinning.chart is item.chart


def test_a_conventional_family_pins_a_cylinder():
    item = family("Sargent", "LA six pin")
    pinning = pin_cylinder(item.pinning, [Bitting.parse("342156")])
    assert pinning.chambers == 6
    assert pinning.stacks[0].height_mils == item.pinning.column_mils


def test_the_core_family_pins_a_core():
    item = family("Best", "A2 seven pin core")
    stack = build_core_stack(item.core, 1, [2, 6], 6)
    assert stack.control_wafer_mils == 50


def test_the_padlock_family_pins_five_chambers():
    item = family("Corbin", "60 keyway five pin")
    pinning = pin_cylinder(item.pinning, [Bitting.parse("13024")])
    assert pinning.chambers == 5


def test_every_family_has_a_readable_key():
    for item in FAMILIES:
        assert "/" in item.key
        assert item.key.startswith(item.chart.manufacturer)

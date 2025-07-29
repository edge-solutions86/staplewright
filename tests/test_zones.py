import pytest

from campus import a_campus
from hasplib.campus.zones import Zone, ZoneMap, spans_zones
from hasplib.errors import ConflictError, InputError, NotFoundError


def a_map() -> ZoneMap:
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core", "geography crew"))
    zones.add(Zone("SVC", "service yards", "night facilities"))
    zones.place("MOO", "ACAD")
    zones.place("VOS", "ACAD")
    zones.place("HEI", "SVC")
    return zones


@pytest.mark.parametrize("code", ["", "  ", "acad", " ACAD"])
def test_a_zone_code_is_upper_case_and_trimmed(code):
    with pytest.raises(InputError):
        Zone(code, "academic core")


def test_a_zone_needs_a_name():
    with pytest.raises(InputError):
        Zone("ACAD", "  ")


def test_a_building_lands_in_a_zone():
    zones = a_map()
    assert zones.zone_of("MOO").code == "ACAD"
    assert zones.buildings_in("ACAD") == ("MOO", "VOS")


def test_a_building_nobody_placed_is_in_no_zone():
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core"))
    assert zones.zone_of("MOO") is None


def test_a_building_is_in_one_zone():
    zones = a_map()
    with pytest.raises(ConflictError) as caught:
        zones.place("MOO", "SVC")
    assert caught.value.facts["zone"] == "ACAD"


def test_placing_a_building_where_it_already_is_is_quiet():
    zones = a_map()
    assert zones.place("MOO", "ACAD") == "ACAD"


def test_a_building_can_be_moved_deliberately():
    zones = a_map()
    zones.move("MOO", "SVC")
    assert zones.zone_of("MOO").code == "SVC"
    assert zones.buildings_in("ACAD") == ("VOS",)


def test_a_building_that_is_not_placed_cannot_be_moved():
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core"))
    with pytest.raises(NotFoundError):
        zones.move("MOO", "ACAD")


def test_a_building_nobody_has_heard_of():
    zones = a_map()
    with pytest.raises(NotFoundError):
        zones.place("ZZZ", "ACAD")


def test_a_zone_nobody_has_heard_of():
    zones = a_map()
    with pytest.raises(NotFoundError):
        zones.place("MOO", "NOWHERE")


def test_a_zone_cannot_be_added_twice():
    zones = a_map()
    with pytest.raises(ConflictError):
        zones.add(Zone("ACAD", "another academic core"))


def test_the_doors_in_a_zone_are_the_keyed_ones():
    zones = a_map()
    found = zones.openings_in("ACAD")
    assert "MOO-260" not in found
    assert "MOO-214" in found
    assert "VOS-311" in found


def test_a_zone_with_no_buildings_has_no_doors():
    zones = a_map()
    zones.add(Zone("PT", "the point"))
    assert zones.openings_in("PT") == ()
    assert zones.empty_zones() == ("PT",)


def test_the_unplaced_buildings_are_reported():
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core"))
    zones.place("MOO", "ACAD")
    assert zones.unplaced() == ("HEI", "VOS")


def test_a_finished_map_leaves_nothing_unplaced():
    assert a_map().unplaced() == ()


def test_the_counts_put_the_biggest_zone_first():
    counts = a_map().counts()
    assert list(counts) == ["ACAD", "SVC"]
    assert counts["ACAD"] > counts["SVC"]


def test_the_rota_comes_off_the_zone():
    assert a_map().rota_for("MOO") == "geography crew"
    assert a_map().rota_for("HEI") == "night facilities"


def test_a_building_in_no_zone_has_no_rota():
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core"))
    with pytest.raises(NotFoundError):
        zones.rota_for("MOO")


def test_a_set_of_buildings_inside_one_zone_does_not_span():
    assert not spans_zones(a_map(), ["MOO", "VOS"])


def test_a_set_reaching_two_zones_spans():
    assert spans_zones(a_map(), ["MOO", "HEI"])


def test_an_unplaced_building_does_not_make_a_span_on_its_own():
    zones = ZoneMap(a_campus())
    zones.add(Zone("ACAD", "academic core"))
    zones.place("MOO", "ACAD")
    assert not spans_zones(zones, ["MOO", "HEI"])


def test_the_map_is_a_container_in_code_order():
    zones = a_map()
    assert "ACAD" in zones
    assert "PT" not in zones
    assert [zone.code for zone in zones] == ["ACAD", "SVC"]
    assert len(zones) == 2

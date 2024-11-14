import pytest

from campus import a_campus
from hasplib.campus.access import AccessGroup, AccessMatrix
from hasplib.errors import ConflictError, InputError, NotFoundError


def a_matrix() -> AccessMatrix:
    matrix = AccessMatrix(a_campus())
    matrix.add_group(AccessGroup("geography faculty", department="geography"))
    matrix.add_group(AccessGroup("geography office", department="geography"))
    matrix.add_group(AccessGroup("night custodial", department="facilities"))
    matrix.add_group(AccessGroup("lock shop", department="facilities"))
    matrix.require_all("geography faculty", ["MOO-214", "MOO-216", "MOO-110"])
    matrix.require_all("geography office", ["MOO-214", "MOO-216", "MOO-218"])
    matrix.require_building("night custodial", "MOO")
    matrix.require_all("lock shop", ["HEI-001", "HEI-G12", "MOO-101"])
    return matrix


def test_a_group_needs_a_name():
    with pytest.raises(InputError):
        AccessGroup("  ")


def test_a_group_cannot_be_added_twice():
    matrix = a_matrix()
    with pytest.raises(ConflictError):
        matrix.add_group(AccessGroup("lock shop"))


def test_asking_about_a_group_nobody_created():
    with pytest.raises(NotFoundError):
        a_matrix().group("provost")


def test_a_requirement_reads_both_ways():
    matrix = a_matrix()
    assert "MOO-214" in matrix.openings_for("geography faculty")
    assert "geography faculty" in matrix.groups_for("MOO-214")
    assert matrix.wants("geography faculty", "MOO-214")
    assert not matrix.wants("geography faculty", "MOO-218")


def test_asking_for_a_door_that_takes_no_key_is_refused():
    matrix = a_matrix()
    with pytest.raises(ConflictError):
        matrix.require("geography faculty", "MOO-260")


def test_asking_for_a_door_nobody_has_heard_of():
    matrix = a_matrix()
    with pytest.raises(NotFoundError):
        matrix.require("geography faculty", "ZZZ-1")


def test_requiring_a_whole_building_skips_the_unkeyed_doors():
    matrix = a_matrix()
    wanted = matrix.openings_for("night custodial")
    assert "MOO-260" not in wanted
    assert len(wanted) == 5


def test_the_same_requirement_twice_is_one_requirement():
    matrix = a_matrix()
    before = len(matrix.openings_for("geography faculty"))
    matrix.require("geography faculty", "MOO-214")
    assert len(matrix.openings_for("geography faculty")) == before


def test_dropping_a_requirement_takes_it_off():
    matrix = a_matrix()
    matrix.drop("geography faculty", "MOO-110")
    assert "MOO-110" not in matrix.openings_for("geography faculty")


def test_dropping_something_that_was_never_asked_for_is_quiet():
    matrix = a_matrix()
    matrix.drop("geography faculty", "MOO-218")
    assert "MOO-218" not in matrix.openings_for("geography faculty")


def test_the_signature_of_a_door_is_the_groups_that_want_it():
    matrix = a_matrix()
    assert matrix.signature("MOO-214") == (
        "geography faculty",
        "geography office",
        "night custodial",
    )


def test_doors_wanted_by_the_same_groups_can_be_keyed_alike():
    sets = a_matrix().keyed_alike_sets()
    assert ("MOO-214", "MOO-216") in sets


def test_a_door_wanted_by_a_different_set_is_on_its_own():
    sets = a_matrix().keyed_alike_sets()
    assert ("MOO-218",) in sets
    assert ("MOO-110",) in sets


def test_the_biggest_keyed_alike_set_comes_first():
    sets = a_matrix().keyed_alike_sets()
    assert len(sets[0]) >= len(sets[-1])


def test_a_door_nobody_asked_for_is_in_no_keyed_alike_set():
    matrix = a_matrix()
    flat = {number for group in matrix.keyed_alike_sets() for number in group}
    assert "VOS-311" not in flat
    assert "VOS-311" in matrix.unreachable_openings()


def test_a_door_somebody_asked_for_is_reachable():
    assert "MOO-214" not in a_matrix().unreachable_openings()


def test_a_group_inside_one_building_reaches_one_building():
    assert a_matrix().buildings_reached("geography faculty") == ("MOO",)


def test_a_group_across_two_buildings_is_flagged():
    matrix = a_matrix()
    assert matrix.buildings_reached("lock shop") == ("HEI", "MOO")
    assert matrix.cross_building_groups() == ("lock shop",)


def test_a_group_with_no_doors_reaches_nothing():
    matrix = a_matrix()
    matrix.add_group(AccessGroup("new hires"))
    assert matrix.buildings_reached("new hires") == ()
    assert matrix.coverage()["new hires"] == 0


def test_coverage_counts_doors_per_group_worst_first():
    coverage = a_matrix().coverage()
    assert list(coverage.values()) == sorted(coverage.values(), reverse=True)
    assert coverage["night custodial"] == 5


def test_the_matrix_is_a_container_of_its_groups():
    matrix = a_matrix()
    assert "lock shop" in matrix
    assert "provost" not in matrix
    assert len(matrix) == 4
    assert [group.name for group in matrix] == sorted(g.name for g in matrix.groups())

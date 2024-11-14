import pytest

from campus import a_campus
from hasplib.campus.access import AccessGroup, AccessMatrix
from hasplib.campus.derive import derive, over_grants, unsatisfied
from hasplib.keys.symbol import KeySymbol


def a_matrix() -> AccessMatrix:
    matrix = AccessMatrix(a_campus())
    matrix.add_group(AccessGroup("geography faculty"))
    matrix.add_group(AccessGroup("geography office"))
    matrix.add_group(AccessGroup("night custodial"))
    matrix.require_all("geography faculty", ["MOO-214", "MOO-216", "MOO-110"])
    matrix.require_all("geography office", ["MOO-214", "MOO-216", "MOO-218"])
    matrix.require_building("night custodial", "MOO")
    return matrix


def a_two_building_matrix() -> AccessMatrix:
    matrix = a_matrix()
    matrix.add_group(AccessGroup("shop"))
    matrix.require_all("shop", ["HEI-001", "HEI-G12", "MOO-101"])
    matrix.add_group(AccessGroup("voss faculty"))
    matrix.require_all("voss faculty", ["VOS-311", "VOS-312"])
    return matrix


def test_each_building_gets_a_grand_master():
    proposal = derive(a_two_building_matrix())
    grand = [g for g in proposal.grants if g.symbol.level == 1]
    assert [g.symbol.render() for g in grand] == ["A", "B", "C"]
    assert all("grand master" in g.reason for g in grand)


def test_one_building_alone_takes_the_first_letter():
    proposal = derive(a_matrix())
    assert [g.symbol.render() for g in proposal.masters()] == ["A", "AA", "AB", "AC"]


def test_a_group_covering_two_alike_sets_becomes_a_master():
    proposal = derive(a_matrix())
    reasons = {g.symbol.render(): g.reason for g in proposal.masters()}
    assert "master for night custodial" in reasons.values()


def test_a_master_opens_everything_the_group_asked_for():
    matrix = a_matrix()
    proposal = derive(matrix)
    for grant in proposal.masters():
        if grant.reason == "master for night custodial":
            assert set(matrix.openings_for("night custodial")) <= set(grant.openings)


def test_every_alike_set_becomes_one_change_key():
    matrix = a_matrix()
    proposal = derive(matrix)
    sets = {tuple(sorted(g.openings)) for g in proposal.change_keys()}
    assert sets == set(matrix.keyed_alike_sets())


def test_a_change_key_hangs_under_a_master_that_covers_it():
    proposal = derive(a_matrix())
    for grant in proposal.change_keys():
        parent = grant.symbol.parent()
        assert parent is not None
        assert set(grant.openings) <= set(proposal.opens(parent))


def test_the_grand_master_opens_its_whole_building():
    matrix = a_matrix()
    proposal = derive(matrix)
    keyed = {o.number for o in matrix.openings.in_building("MOO") if o.is_keyed}
    reachable = set(proposal.opens(KeySymbol.parse("A")))
    assert reachable <= keyed
    assert "MOO-214" in reachable


def test_a_door_nobody_asked_for_stays_out_of_the_draft():
    matrix = a_matrix()
    proposal = derive(matrix)
    everything = {number for grant in proposal.grants for number in grant.openings}
    assert "VOS-311" not in everything
    assert any("VOS-311" in note for note in proposal.notes)


def test_a_cross_building_group_is_called_out_rather_than_keyed():
    proposal = derive(a_two_building_matrix())
    assert any("shop" in note and "top key" in note for note in proposal.notes)


def test_the_proposal_is_ordered_shallow_first():
    proposal = derive(a_two_building_matrix())
    levels = [grant.symbol.level for grant in proposal.grants]
    assert levels == sorted(levels)


def test_asking_the_proposal_about_a_symbol_it_does_not_hold():
    proposal = derive(a_matrix())
    assert proposal.opens(KeySymbol.parse("ZZ")) == ()
    assert proposal.grant_for(KeySymbol.parse("ZZ")) is None


def a_shrunken_matrix() -> AccessMatrix:
    """The same campus after somebody took two doors off a group."""
    matrix = a_matrix()
    matrix.drop("night custodial", "MOO-110")
    matrix.drop("night custodial", "MOO-216")
    return matrix


def test_a_draft_against_the_matrix_it_came_from_hands_out_nothing_spare():
    matrix = a_matrix()
    assert over_grants(matrix, derive(matrix)) == ()


def test_a_stale_draft_gives_a_group_more_than_it_now_asks_for():
    proposal = derive(a_matrix())
    reported = {item.group for item in over_grants(a_shrunken_matrix(), proposal)}
    assert "night custodial" in reported


def test_an_over_grant_names_the_extra_doors():
    matrix = a_shrunken_matrix()
    found = over_grants(matrix, derive(a_matrix()))
    assert found
    for item in found:
        wanted = set(matrix.openings_for(item.group))
        assert item.extra
        assert not set(item.extra) & wanted


def test_the_worst_over_grant_comes_first():
    found = over_grants(a_shrunken_matrix(), derive(a_matrix()))
    sizes = [len(item.extra) for item in found]
    assert sizes == sorted(sizes, reverse=True)


def test_a_cross_building_group_is_unsatisfied_by_a_single_key():
    matrix = a_two_building_matrix()
    assert "shop" in unsatisfied(matrix, derive(matrix))


def test_a_group_the_draft_covers_is_not_unsatisfied():
    matrix = a_matrix()
    assert "night custodial" not in unsatisfied(matrix, derive(matrix))


def test_a_group_with_no_doors_is_neither_satisfied_nor_reported():
    matrix = a_matrix()
    matrix.add_group(AccessGroup("new hires"))
    proposal = derive(matrix)
    assert "new hires" not in unsatisfied(matrix, proposal)
    assert "new hires" not in {item.group for item in over_grants(matrix, proposal)}


def test_an_empty_matrix_derives_an_empty_proposal():
    matrix = AccessMatrix(a_campus())
    proposal = derive(matrix)
    assert proposal.grants == ()
    assert len(proposal.notes) == len(matrix.openings.keyed_openings())


@pytest.mark.parametrize("group", ["geography faculty", "night custodial"])
def test_every_named_group_reaches_one_building_here(group):
    assert len(a_matrix().buildings_reached(group)) == 1

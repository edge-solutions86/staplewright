import pytest

from hasplib.errors import KeywayError
from hasplib.spec.keyway import (
    Blank,
    KeywayFamily,
    KeywaySection,
    blanks_for,
    restricted_sections,
)


def a_family() -> KeywayFamily:
    """The campus family. L enters everything, the leaves are the buildings."""
    family = KeywayFamily("Sargent LA", KeywaySection("L", "Sargent"))
    family.add(KeywaySection("LA", "Sargent"), under="L")
    family.add(KeywaySection("LB", "Sargent"), under="L")
    family.add(KeywaySection("LC", "Sargent", restricted=True), under="LA")
    family.add(KeywaySection("LD", "Sargent", restricted=True), under="LA")
    family.add(KeywaySection("LE", "Sargent"), under="LB")
    return family


def test_the_root_enters_every_section():
    family = a_family()
    assert set(family.sections_entered("L")) == {"L", "LA", "LB", "LC", "LD", "LE"}


def test_a_branch_enters_only_what_hangs_off_it():
    assert set(a_family().sections_entered("LA")) == {"LA", "LC", "LD"}


def test_a_leaf_enters_only_itself():
    assert a_family().sections_entered("LC") == ("LC",)


def test_entering_reads_down_the_tree_not_up():
    family = a_family()
    assert family.enters("LA", "LC")
    assert not family.enters("LC", "LA")
    assert family.enters("LC", "LC")


def test_two_branches_do_not_enter_each_other():
    family = a_family()
    assert not family.enters("LA", "LE")
    assert not family.enters("LB", "LC")


def test_ancestry_walks_up_to_the_root():
    assert a_family().ancestry("LC") == ("LC", "LA", "L")


def test_the_common_section_is_the_lowest_one_that_covers_both():
    family = a_family()
    assert family.common_section("LC", "LD") == "LA"
    assert family.common_section("LC", "LE") == "L"
    assert family.common_section("LC", "LC") == "LC"
    assert family.common_section("LC", "LA") == "LA"


def test_depth_counts_from_the_root():
    family = a_family()
    assert family.depth_of("L") == 0
    assert family.depth_of("LA") == 1
    assert family.depth_of("LC") == 2


def test_the_leaves_are_the_sections_with_nothing_under_them():
    assert a_family().leaves() == ("LC", "LD", "LE")


def test_parent_and_children_read_both_ways():
    family = a_family()
    assert family.parent_of("LC").code == "LA"
    assert family.parent_of("L") is None
    assert [s.code for s in family.children_of("LA")] == ["LC", "LD"]
    assert family.children_of("LC") == ()


def test_a_family_is_a_container_of_its_sections():
    family = a_family()
    assert "LC" in family
    assert "LZ" not in family
    assert len(family) == 6
    assert [section.code for section in family] == ["L", "LA", "LB", "LC", "LD", "LE"]


def test_a_section_cannot_be_added_twice():
    family = a_family()
    with pytest.raises(KeywayError):
        family.add(KeywaySection("LC", "Sargent"), under="LB")


def test_a_section_cannot_hang_off_a_parent_that_is_not_there():
    family = a_family()
    with pytest.raises(KeywayError):
        family.add(KeywaySection("LZ", "Sargent"), under="LQ")


def test_a_family_holds_one_manufacturer():
    family = a_family()
    with pytest.raises(KeywayError) as caught:
        family.add(KeywaySection("LZ", "Best"), under="L")
    assert caught.value.facts["expected"] == "Sargent"


def test_asking_about_a_section_that_is_not_there():
    with pytest.raises(KeywayError):
        a_family().section("LZ")


@pytest.mark.parametrize("code", ["", "  ", "lc", " LC"])
def test_a_section_code_is_upper_case_and_trimmed(code):
    with pytest.raises(KeywayError):
        KeywaySection(code, "Sargent")


def test_a_family_needs_a_name():
    with pytest.raises(KeywayError):
        KeywayFamily("  ", KeywaySection("L", "Sargent"))


def test_restricted_sections_are_reported_in_order():
    assert restricted_sections(a_family()) == ("LC", "LD")


def test_blanks_come_back_deepest_stock_first():
    drawer = [
        Blank("LC-6", "LC", "Sargent", 6, stock=4),
        Blank("LC-7", "LC", "Sargent", 7, stock=19),
        Blank("LA-6", "LA", "Sargent", 6, stock=40),
    ]
    assert [b.part_number for b in blanks_for(drawer, "LC")] == ["LC-7", "LC-6"]
    assert blanks_for(drawer, "LZ") == ()


def test_two_blanks_at_the_same_stock_sort_by_part_number():
    drawer = [
        Blank("LC-7", "LC", "Sargent", 7, stock=4),
        Blank("LC-6", "LC", "Sargent", 6, stock=4),
    ]
    assert [b.part_number for b in blanks_for(drawer, "LC")] == ["LC-6", "LC-7"]


@pytest.mark.parametrize("broken", [{"chambers": 0}, {"stock": -1}])
def test_an_impossible_blank_is_refused(broken):
    settings = {"part_number": "LC-6", "section": "LC", "manufacturer": "Sargent", "chambers": 6}
    settings.update(broken)
    with pytest.raises(KeywayError):
        Blank(**settings)

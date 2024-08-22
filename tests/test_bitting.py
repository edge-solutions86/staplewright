import pytest

from hasplib.errors import InputError
from hasplib.keys.bitting import Bitting, matches_pattern, parse_pattern


def test_packed_digits_are_one_cut_each():
    assert Bitting.parse("342156").depths == (3, 4, 2, 1, 5, 6)


def test_dotted_form_reads_double_digit_depths():
    assert Bitting.parse("10.4.12.3").depths == (10, 4, 12, 3)


def test_dashes_and_spaces_are_the_same_as_dots():
    assert Bitting.parse(" 10-4-12-3 ") == Bitting.parse("10.4.12.3")


def test_chambers_count_from_one():
    b = Bitting.parse("342156")
    assert b.at(1) == 3
    assert b.at(6) == 6


@pytest.mark.parametrize("chamber", [0, -1, 7])
def test_a_chamber_off_the_end_is_refused(chamber):
    with pytest.raises(InputError):
        Bitting.parse("342156").at(chamber)


def test_with_depth_leaves_the_original_alone():
    original = Bitting.parse("342156")
    changed = original.with_depth(3, 7)
    assert changed.render() == "347156"
    assert original.render() == "342156"


def test_differs_from_names_the_chambers():
    assert Bitting.parse("342156").differs_from(Bitting.parse("352158")) == (2, 6)


def test_differing_lengths_cannot_be_compared():
    with pytest.raises(InputError):
        Bitting.parse("3421").differs_from(Bitting.parse("342156"))


def test_adjacent_differences_are_absolute():
    assert Bitting.parse("342156").adjacent_differences() == (1, 2, 1, 4, 1)


def test_render_goes_dotted_on_its_own_past_nine():
    assert Bitting((10, 2, 3)).render() == "10.2.3"
    assert Bitting((1, 2, 3)).render() == "123"
    assert Bitting((1, 2, 3)).render(dotted=True) == "1.2.3"


def test_bittings_compare_and_hash_by_value():
    assert Bitting.parse("342156") == Bitting((3, 4, 2, 1, 5, 6))
    assert len({Bitting.parse("1234"), Bitting((1, 2, 3, 4))}) == 1
    assert Bitting.parse("1234") < Bitting.parse("1235")


def test_deepest_and_shallowest():
    b = Bitting.parse("342156")
    assert b.deepest() == 6
    assert b.shallowest() == 1


@pytest.mark.parametrize("text", ["", "  ", "34a156", "3..4", "3.4.x"])
def test_junk_is_refused(text):
    with pytest.raises(InputError):
        Bitting.parse(text)


def test_a_negative_cut_is_refused():
    with pytest.raises(InputError):
        Bitting([3, -1, 2])


def test_a_boolean_is_not_a_depth():
    with pytest.raises(InputError):
        Bitting([True, 2, 3])


def test_an_empty_bitting_is_refused():
    with pytest.raises(InputError):
        Bitting([])


def test_iterating_gives_the_cuts_in_order():
    assert list(Bitting.parse("3421")) == [3, 4, 2, 1]
    assert len(Bitting.parse("3421")) == 4
    assert Bitting.parse("3421")[0] == 3


def test_pattern_holes_come_back_as_none():
    assert parse_pattern("3x21x6", 6) == (3, None, 2, 1, None, 6)


def test_pattern_length_has_to_match_the_system():
    with pytest.raises(InputError):
        parse_pattern("3x21", 6)


def test_a_pattern_takes_depths_or_an_x_and_nothing_else():
    with pytest.raises(InputError):
        parse_pattern("3q21x6", 6)


def test_matching_ignores_the_open_chambers():
    pattern = parse_pattern("3x21x6", 6)
    assert matches_pattern(Bitting.parse("342156"), pattern)
    assert matches_pattern(Bitting.parse("392186"), pattern)
    assert not matches_pattern(Bitting.parse("442156"), pattern)


def test_matching_a_key_of_the_wrong_length_is_false_not_an_error():
    assert not matches_pattern(Bitting.parse("3421"), parse_pattern("3x21x6", 6))

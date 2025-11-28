import pytest

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.keys.stamping import (
    BlindCodeBook,
    Marking,
    StampPolicy,
    identify,
    stamp_for,
    stamp_run,
    stamp_sheet,
    unstamped,
)
from hasplib.keys.symbol import KeySymbol

CHANGE_KEY = KeySymbol.parse("AA1")
MASTER = KeySymbol.parse("AA")
TOP = KeySymbol.parse("GGM")


def a_book() -> BlindCodeBook:
    return BlindCodeBook()


def test_a_bow_has_to_hold_something():
    with pytest.raises(InputError):
        StampPolicy(bow_characters=3)


def test_a_change_key_gets_its_symbol_by_default():
    assert StampPolicy().marking_for(CHANGE_KEY) is Marking.SYMBOL


def test_a_master_goes_blind_by_default():
    assert StampPolicy().marking_for(MASTER) is Marking.BLIND


def test_a_shop_can_stamp_everything_the_same_way():
    policy = StampPolicy(change_keys=Marking.TAG, masters=Marking.TAG)
    assert policy.marking_for(MASTER) is Marking.TAG


def test_a_stamp_goes_on_a_tagged_key():
    with pytest.raises(InputError):
        stamp_for(" ", CHANGE_KEY, StampPolicy())


def test_a_change_key_carries_its_symbol():
    found = stamp_for("41207", CHANGE_KEY, StampPolicy())
    assert found.text == "AA1"
    assert not found.is_blind


def test_a_master_carries_a_number_from_the_book():
    book = a_book()
    found = stamp_for("41211", MASTER, StampPolicy(), book)
    assert found.text == "4000"
    assert found.is_blind


def test_a_blind_stamp_without_the_book_is_refused():
    with pytest.raises(ConflictError):
        stamp_for("41211", MASTER, StampPolicy())


def test_a_key_can_carry_its_tag_instead():
    policy = StampPolicy(change_keys=Marking.TAG)
    assert stamp_for("41207", CHANGE_KEY, policy).text == "41207"


def test_a_key_can_carry_nothing():
    policy = StampPolicy(change_keys=Marking.NOTHING)
    found = stamp_for("41207", CHANGE_KEY, policy)
    assert found.text == ""
    assert found.describe().startswith("41207 blank")


def test_something_too_long_for_the_bow_is_refused():
    policy = StampPolicy(change_keys=Marking.TAG, bow_characters=4)
    with pytest.raises(ConflictError) as caught:
        stamp_for("412075555", CHANGE_KEY, policy)
    assert caught.value.facts["room"] == 4


def test_a_stamp_reads_back_as_one_line():
    found = stamp_for("41207", CHANGE_KEY, StampPolicy())
    assert found.describe() == "41207 AA1 (the key symbol)"


def test_a_blind_series_starts_where_it_was_told():
    book = BlindCodeBook(start=9000, step=3)
    assert book.assign(MASTER) == "9000"
    assert book.assign(TOP) == "9003"


def test_a_series_starts_at_one_or_later():
    with pytest.raises(InputError):
        BlindCodeBook(start=0)


def test_a_series_steps_forward():
    with pytest.raises(InputError):
        BlindCodeBook(step=0)


def test_a_symbol_keeps_the_code_it_was_given():
    book = a_book()
    first = book.assign(MASTER)
    assert book.assign(MASTER) == first


def test_the_code_for_a_symbol_comes_back():
    book = a_book()
    book.assign(MASTER)
    assert book.code_for(MASTER) == "4000"


def test_a_symbol_nobody_stamped_has_no_code():
    with pytest.raises(NotFoundError):
        a_book().code_for(MASTER)


def test_a_number_on_a_key_comes_back_as_a_symbol():
    book = a_book()
    code = book.assign(MASTER)
    assert identify(code, book) == "AA"


def test_a_number_with_space_round_it_still_reads():
    book = a_book()
    code = book.assign(MASTER)
    assert book.symbol_for(f"  {code} ") == "AA"


def test_a_number_nobody_stamped_is_a_404():
    with pytest.raises(NotFoundError):
        identify("9999", a_book())


def test_a_stored_pair_comes_back():
    book = a_book()
    book.load("AA", "4000")
    assert book.symbol_for("4000") == "AA"


def test_a_stored_pair_moves_the_series_past_it():
    book = a_book()
    book.load("AA", "4021")
    assert book.assign(TOP) == "4028"


def test_a_code_cannot_be_reused_for_another_symbol():
    book = a_book()
    book.load("AA", "4000")
    with pytest.raises(ConflictError):
        book.load("AB", "4000")


def test_loading_the_same_pair_twice_is_fine():
    book = a_book()
    book.load("AA", "4000")
    book.load("AA", "4000")
    assert len(book) == 1


def test_the_book_counts_and_contains():
    book = a_book()
    book.assign(MASTER)
    assert len(book) == 1
    assert "4000" in book
    assert list(book) == ["4000"]


def test_the_pairs_come_back_symbol_first():
    book = a_book()
    book.assign(TOP)
    book.assign(MASTER)
    assert book.codes() == (("4007", "AA"), ("4000", "GGM"))


def test_a_run_comes_back_in_tag_order():
    book = a_book()
    found = stamp_run([("41211", MASTER), ("41207", CHANGE_KEY)], StampPolicy(), book)
    assert [stamp.tag for stamp in found] == ["41207", "41211"]


def test_a_run_gives_one_code_per_symbol():
    book = a_book()
    found = stamp_run([("41211", MASTER), ("41212", MASTER)], StampPolicy(), book)
    assert found[0].text == found[1].text


def test_an_empty_sheet_says_so():
    assert stamp_sheet([], StampPolicy()) == ("nothing to stamp",)


def test_the_sheet_counts_the_keys():
    book = a_book()
    found = stamp_run([("41207", CHANGE_KEY)], StampPolicy(), book)
    lines = stamp_sheet(found, StampPolicy())
    assert lines[0] == "1 keys to stamp"


def test_the_sheet_adds_the_do_not_duplicate_line():
    book = a_book()
    found = stamp_run([("41207", CHANGE_KEY)], StampPolicy(), book)
    assert "DND" in stamp_sheet(found, StampPolicy())[1]


def test_a_shop_that_does_not_bother_leaves_it_off():
    book = a_book()
    policy = StampPolicy(add_do_not_duplicate=False)
    found = stamp_run([("41207", CHANGE_KEY)], policy, book)
    assert "DND" not in stamp_sheet(found, policy)[1]


def test_the_sheet_says_how_many_are_blind():
    book = a_book()
    found = stamp_run([("41211", MASTER)], StampPolicy(), book)
    assert stamp_sheet(found, StampPolicy())[-1].startswith("1 of them are blind")


def test_a_sheet_with_nothing_blind_says_nothing_about_it():
    book = a_book()
    found = stamp_run([("41207", CHANGE_KEY)], StampPolicy(), book)
    assert not any("blind" in line for line in stamp_sheet(found, StampPolicy()))


def test_the_unstamped_keys_are_named():
    policy = StampPolicy(change_keys=Marking.NOTHING)
    found = stamp_run([("41207", CHANGE_KEY)], policy, a_book())
    assert unstamped(found) == ("41207",)


def test_a_stamped_key_is_not_on_that_list():
    found = stamp_run([("41207", CHANGE_KEY)], StampPolicy(), a_book())
    assert unstamped(found) == ()

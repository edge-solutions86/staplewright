import pytest

from hasplib.errors import ConflictError, NotFoundError
from hasplib.store import Registry


def a_registry() -> Registry[str]:
    shelf: Registry[str] = Registry("cylinder", "serial")
    shelf.add("C-3", "mortise")
    shelf.add("C-1", "rim")
    shelf.add("C-2", "cam")
    return shelf


def test_things_come_back_in_the_order_they_arrived():
    assert a_registry().values() == ("mortise", "rim", "cam")
    assert a_registry().keys() == ("C-3", "C-1", "C-2")


def test_key_order_is_a_separate_question():
    assert a_registry().by_key() == ("rim", "cam", "mortise")
    assert a_registry().sorted_keys() == ("C-1", "C-2", "C-3")


def test_a_key_cannot_be_used_twice():
    shelf = a_registry()
    with pytest.raises(ConflictError) as caught:
        shelf.add("C-1", "another")
    assert caught.value.facts == {"serial": "C-1"}
    assert "cylinder" in caught.value.message


def test_putting_over_something_keeps_its_place_in_the_order():
    shelf = a_registry()
    shelf.put("C-3", "sfic")
    assert shelf.keys() == ("C-3", "C-1", "C-2")
    assert shelf.get("C-3") == "sfic"


def test_putting_something_new_adds_it_at_the_end():
    shelf = a_registry()
    shelf.put("C-9", "padlock")
    assert shelf.keys()[-1] == "C-9"


def test_a_missing_key_names_the_noun_and_the_field():
    shelf = a_registry()
    with pytest.raises(NotFoundError) as caught:
        shelf.get("C-9")
    assert caught.value.message == "no such cylinder"
    assert caught.value.facts == {"serial": "C-9"}


def test_find_treats_missing_as_an_answer():
    shelf = a_registry()
    assert shelf.find("C-1") == "rim"
    assert shelf.find("C-9") is None


def test_removing_takes_it_out_of_both_the_map_and_the_order():
    shelf = a_registry()
    assert shelf.remove("C-1") == "rim"
    assert shelf.keys() == ("C-3", "C-2")
    assert not shelf.has("C-1")


def test_removing_something_that_is_not_there():
    with pytest.raises(NotFoundError):
        a_registry().remove("C-9")


def test_where_keeps_the_arrival_order():
    shelf = a_registry()
    assert shelf.where(lambda item: item.startswith("m")) == ("mortise",)
    assert shelf.where(lambda item: "i" in item) == ("mortise", "rim")


def test_where_matching_nothing_is_empty():
    assert a_registry().where(lambda item: False) == ()


def test_first_stops_at_the_earliest_match():
    shelf = a_registry()
    assert shelf.first(lambda item: "i" in item) == "mortise"
    assert shelf.first(lambda item: False) is None


def test_the_registry_is_a_container():
    shelf = a_registry()
    assert "C-1" in shelf
    assert "C-9" not in shelf
    assert len(shelf) == 3
    assert list(shelf) == ["mortise", "rim", "cam"]


def test_an_empty_registry_holds_nothing():
    shelf: Registry[str] = Registry("blank")
    assert len(shelf) == 0
    assert shelf.values() == ()
    with pytest.raises(NotFoundError) as caught:
        shelf.get("anything")
    assert caught.value.facts == {"id": "anything"}

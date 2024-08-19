import pytest

from hasplib.errors import (
    BittingError,
    CapacityError,
    ConflictError,
    HaspError,
    HierarchyError,
    InputError,
    KeywayError,
    NotFoundError,
    PinningError,
    ProgressionError,
    SpecificationError,
    error_codes,
)


def test_message_alone_renders_as_itself():
    err = BittingError("cut four is deeper than the chart goes")
    assert str(err) == "cut four is deeper than the chart goes"


def test_facts_render_in_a_stable_order():
    err = BittingError("out of range", chamber=4, depth=9, deepest=7)
    assert str(err) == "out of range (chamber=4, deepest=7, depth=9)"


def test_as_dict_carries_the_code():
    err = PinningError("stack too tall", chamber=2, height=345)
    assert err.as_dict() == {
        "code": "pinning",
        "message": "stack too tall",
        "facts": {"chamber": 2, "height": 345},
    }


def test_facts_are_copied_not_shared():
    facts = {"chamber": 1}
    err = HaspError("something", **facts)
    err.facts["chamber"] = 99
    assert facts["chamber"] == 1


@pytest.mark.parametrize(
    ("cls", "code"),
    [
        (InputError, "input"),
        (SpecificationError, "specification"),
        (BittingError, "bitting"),
        (KeywayError, "keyway"),
        (HierarchyError, "hierarchy"),
        (ProgressionError, "progression"),
        (PinningError, "pinning"),
        (CapacityError, "capacity"),
        (NotFoundError, "not_found"),
        (ConflictError, "conflict"),
    ],
)
def test_each_class_keeps_its_code(cls, code):
    assert cls.code == code
    assert issubclass(cls, HaspError)


def test_every_code_is_unique_and_reachable():
    codes = error_codes()
    assert codes["bitting"] is BittingError
    assert codes["hasp_error"] is HaspError
    assert len(codes) == len({cls.__name__ for cls in codes.values()})


def test_catching_the_base_catches_all_of_them():
    for cls in error_codes().values():
        with pytest.raises(HaspError):
            raise cls("no")

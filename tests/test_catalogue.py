import hasplib
from hasplib.catalogue import (
    BY_CODE,
    CATALOGUE,
    catalogue_sheet,
    codes,
    refusal_for,
    status_for,
    worth_retrying,
)
from hasplib.errors import (
    BittingError,
    CapacityError,
    ConflictError,
    HaspError,
    InputError,
    NotFoundError,
)


def test_the_package_exports_the_names_it_promises():
    for name in hasplib.__all__:
        assert hasattr(hasplib, name), name


def test_the_export_list_is_sorted():
    assert hasplib.__all__ == sorted(hasplib.__all__)


def test_nothing_is_exported_twice():
    assert len(hasplib.__all__) == len(set(hasplib.__all__))


def test_a_caller_can_parse_a_bitting_off_the_package():
    assert hasplib.Bitting.parse("442446").render() == "442446"


def test_a_caller_can_parse_a_symbol_off_the_package():
    assert hasplib.KeySymbol.parse("AA1").level == 3


def test_a_caller_can_read_the_settings_off_the_package():
    assert hasplib.from_mapping({}).cutting.max_run >= 1


def test_the_version_is_a_string():
    assert hasplib.__version__.count(".") == 2


def test_no_two_refusals_share_a_code():
    found = [item.code for item in CATALOGUE]
    assert len(found) == len(set(found))


def test_no_two_refusals_share_a_class():
    found = [item.kind for item in CATALOGUE]
    assert len(found) == len(set(found))


def test_every_error_class_is_in_the_catalogue():
    def descendants(kind):
        for child in kind.__subclasses__():
            yield child
            yield from descendants(child)

    known = {item.kind for item in CATALOGUE}
    for kind in descendants(HaspError):
        if kind.__module__.startswith("test"):
            continue
        assert kind in known, kind.__name__


def test_the_codes_come_back_sorted():
    assert codes() == tuple(sorted(codes()))


def test_a_refusal_is_found_by_its_own_class():
    assert refusal_for(NotFoundError("gone")).kind is NotFoundError


class OddConflict(ConflictError):
    """A refusal the catalogue has never heard of, for the fallback test."""

    code = "odd_conflict"


def test_a_refusal_falls_back_to_its_parent():
    assert refusal_for(OddConflict("odd")).kind is ConflictError


def test_a_refusal_nobody_catalogued_still_gets_a_status():
    assert status_for(OddConflict("odd")) == 409


def test_a_bare_refusal_gets_the_general_entry():
    assert refusal_for(HaspError("something")).kind is HaspError


def test_the_statuses_are_the_ones_the_api_uses():
    assert status_for(NotFoundError("gone")) == 404
    assert status_for(ConflictError("no")) == 409
    assert status_for(InputError("no")) == 422
    assert status_for(CapacityError("no")) == 409
    assert status_for(BittingError("no")) == 400


def test_a_capacity_refusal_is_worth_another_go():
    assert worth_retrying(CapacityError("not enough"))


def test_a_conflict_is_not():
    assert not worth_retrying(ConflictError("wrong state"))


def test_the_catalogue_is_keyed_by_code():
    assert BY_CODE["not_found"].kind is NotFoundError


def test_a_refusal_reads_back_as_one_line():
    line = BY_CODE["not_found"].describe()
    assert line.startswith("not_found")
    assert "404" in line


def test_the_sheet_counts_the_codes():
    lines = catalogue_sheet()
    assert lines[0] == f"{len(CATALOGUE)} refusal codes"
    assert len(lines) == len(CATALOGUE) + 1

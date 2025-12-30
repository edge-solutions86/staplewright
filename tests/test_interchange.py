from datetime import date

import pytest

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.spec.interchange import (
    ControlKey,
    ControlRegister,
    Core,
    CoreFormat,
    HousingKind,
    control_sections,
    crossing_report,
    holder_sheet,
    swappable,
)

ISSUED = date(2025, 1, 6)
LATER = date(2025, 12, 1)
CUT = Bitting.parse("3572461")


def a_key(code: str = "CK-1", **overrides) -> ControlKey:
    settings = {
        "code": code,
        "section": "A",
        "format": CoreFormat.SFIC_A2,
        "bitting": CUT,
        "issued_on": ISSUED,
    }
    settings.update(overrides)
    return ControlKey(**settings)


def a_core(serial: str = "K-1001", **overrides) -> Core:
    settings = {
        "serial": serial,
        "format": CoreFormat.SFIC_A2,
        "control_code": "CK-1",
        "housing": HousingKind.MORTISE,
    }
    settings.update(overrides)
    return Core(**settings)


def a_register() -> ControlRegister:
    register = ControlRegister()
    register.add_key(a_key())
    register.add_core(a_core())
    return register


def test_a_control_key_needs_a_code():
    with pytest.raises(InputError):
        a_key(code=" ")


def test_a_control_key_is_cut_on_a_section():
    with pytest.raises(InputError):
        a_key(section="")


def test_a_fresh_control_key_is_live():
    assert a_key().is_live


def test_a_core_has_a_serial():
    with pytest.raises(InputError):
        a_core(serial="")


def test_a_core_is_pulled_by_something():
    with pytest.raises(InputError):
        a_core(control_code=" ")


def test_a_core_in_a_door_is_installed():
    assert a_core(opening="MOO-101").is_installed


def test_a_core_on_the_shelf_is_not():
    assert not a_core().is_installed


def test_a_control_key_pulls_its_own_cores():
    assert a_key().can_pull(a_core())


def test_a_control_key_does_not_pull_another_ones():
    assert not a_key().can_pull(a_core(control_code="CK-2"))


def test_a_control_key_does_not_pull_another_format():
    assert not a_key().can_pull(a_core(format=CoreFormat.SFIC_A4))


def test_a_withdrawn_control_key_pulls_nothing():
    key = a_key(withdrawn_on=LATER)
    assert not key.can_pull(a_core())


def test_a_control_key_reads_back_as_one_line():
    assert a_key().describe() == "CK-1 small format, A2 held by nobody named, live"


def test_a_core_nothing_pulls_is_refused():
    register = ControlRegister()
    with pytest.raises(ConflictError) as caught:
        register.add_core(a_core())
    assert caught.value.facts["control"] == "CK-1"


def test_a_core_of_the_wrong_format_is_refused():
    register = ControlRegister()
    register.add_key(a_key())
    with pytest.raises(ConflictError) as caught:
        register.add_core(a_core(format=CoreFormat.SFIC_A4))
    assert caught.value.facts["key_format"] == "small format, A2"


def test_a_core_goes_on_and_comes_back():
    assert a_register().core("K-1001").housing is HousingKind.MORTISE


def test_a_core_nobody_registered_is_a_404():
    with pytest.raises(NotFoundError):
        a_register().core("K-9999")


def test_a_control_key_nobody_registered_is_a_404():
    with pytest.raises(NotFoundError):
        a_register().key("CK-9")


def test_handing_a_control_key_over_records_who():
    register = a_register()
    assert register.hand_to("CK-1", "phamm").holder == "phamm"


def test_a_control_key_goes_to_somebody():
    register = a_register()
    with pytest.raises(InputError):
        register.hand_to("CK-1", " ")


def test_a_withdrawn_key_cannot_be_handed_over():
    register = ControlRegister()
    register.add_key(a_key())
    register.withdraw("CK-1", LATER)
    with pytest.raises(ConflictError):
        register.hand_to("CK-1", "phamm")


def test_a_key_with_cores_on_it_cannot_be_withdrawn():
    register = a_register()
    with pytest.raises(ConflictError) as caught:
        register.withdraw("CK-1", LATER)
    assert caught.value.facts["cores"] == 1


def test_a_key_with_no_cores_can_be_withdrawn():
    register = ControlRegister()
    register.add_key(a_key())
    assert not register.withdraw("CK-1", LATER).is_live


def test_a_key_cannot_be_withdrawn_twice():
    register = ControlRegister()
    register.add_key(a_key())
    register.withdraw("CK-1", LATER)
    with pytest.raises(ConflictError):
        register.withdraw("CK-1", LATER)


def test_the_cores_on_one_key_come_back():
    register = a_register()
    register.add_core(a_core("K-1002"))
    assert len(register.cores_pulled_by("CK-1")) == 2


def test_the_live_keys_leave_out_the_withdrawn():
    register = ControlRegister()
    register.add_key(a_key())
    register.add_key(a_key("CK-2", section="B"))
    register.withdraw("CK-2", LATER)
    assert [key.code for key in register.live_keys()] == ["CK-1"]


def test_the_sections_in_use_are_listed():
    register = a_register()
    register.add_key(a_key("CK-2", section="B"))
    assert register.sections() == ("A", "B")


def test_the_holders_are_counted_most_first():
    register = a_register()
    register.add_key(a_key("CK-2", section="A"))
    register.hand_to("CK-1", "phamm")
    register.hand_to("CK-2", "phamm")
    assert register.holders() == {"phamm": 2}


def test_a_key_nobody_signed_for_shows_as_nobody():
    assert a_register().holders() == {"nobody named": 1}


def test_a_core_whose_key_went_is_unaccounted_for():
    register = ControlRegister()
    register.add_key(a_key())
    register.add_key(a_key("CK-2", section="A"))
    register.add_core(a_core("K-1002", control_code="CK-2"))
    register.withdraw("CK-1", LATER)
    assert [core.serial for core in register.unaccounted_cores()] == []


def test_the_register_counts_and_contains():
    register = a_register()
    assert len(register) == 1
    assert "CK-1" in register
    assert [key.code for key in register] == ["CK-1"]


def test_two_cores_on_one_key_are_not_a_crossing():
    cores = [a_core("K-1001"), a_core("K-1002")]
    assert swappable(cores) == ()


def test_two_cores_on_different_keys_that_fit_are_a_crossing():
    cores = [a_core("K-1001"), a_core("K-1002", control_code="CK-2")]
    assert swappable(cores) == (("K-1001", "K-1002"),)


def test_cores_in_different_housings_cannot_swap():
    cores = [
        a_core("K-1001"),
        a_core("K-1002", control_code="CK-2", housing=HousingKind.RIM),
    ]
    assert swappable(cores) == ()


def test_cores_of_different_formats_cannot_swap():
    cores = [
        a_core("K-1001"),
        a_core("K-1002", control_code="CK-2", format=CoreFormat.SFIC_A4),
    ]
    assert swappable(cores) == ()


def test_a_pair_is_only_reported_once():
    cores = [
        a_core("K-1001"),
        a_core("K-1002", control_code="CK-2"),
        a_core("K-1003", control_code="CK-2"),
    ]
    assert len(swappable(cores)) == 2


def test_one_control_section_is_the_quiet_case():
    register = a_register()
    lines = crossing_report(register, [a_core()])
    assert lines[0] == "1 live control keys on 1 sections"
    assert lines[-1] == "no core can be moved across a control key"


def test_two_control_sections_are_called_out():
    register = a_register()
    register.add_key(a_key("CK-2", section="B"))
    lines = crossing_report(register, [a_core()])
    assert any("more than one control section" in line for line in lines)


def test_the_crossings_are_named():
    register = a_register()
    register.add_key(a_key("CK-2", section="A"))
    register.add_core(a_core("K-1002", control_code="CK-2"))
    cores = [a_core("K-1001"), a_core("K-1002", control_code="CK-2")]
    lines = crossing_report(register, cores)
    assert any(line.strip() == "K-1001 and K-1002" for line in lines)


def test_the_control_sections_come_off_the_register():
    assert control_sections(a_register()) == ("A",)


def test_no_control_key_out_says_so():
    assert holder_sheet(ControlRegister()) == ("no control key is out",)


def test_the_holder_sheet_counts_them():
    register = a_register()
    register.hand_to("CK-1", "phamm")
    lines = holder_sheet(register)
    assert lines[0] == "1 control keys are out"
    assert lines[1].strip().startswith("phamm")

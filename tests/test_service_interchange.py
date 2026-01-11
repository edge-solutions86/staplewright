from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.persistence.models.interchange import ControlKeyRow, CoreRow
from hasplib.services.interchange import (
    ControlKeyRepository,
    CoreRepository,
    InterchangeService,
    core_from_row,
    core_to_row,
    key_from_row,
    key_to_row,
)
from hasplib.spec.interchange import ControlKey, Core, CoreFormat, HousingKind

ISSUED = date(2025, 1, 6)
LATER = date(2025, 12, 1)
CUT = Bitting.parse("3572461")


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> InterchangeService:
    return InterchangeService(session)


def with_a_key(session, code: str = "CK-1", section: str = "A") -> InterchangeService:
    service = a_service(session)
    service.cut_key(code, section, CoreFormat.SFIC_A2, CUT, ISSUED)
    return service


def test_a_control_key_goes_to_a_row_and_back():
    key = ControlKey("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED, "phamm")
    assert key_from_row(key_to_row(key)) == key


def test_a_core_goes_to_a_row_and_back():
    core = Core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101")
    assert core_from_row(core_to_row(core)) == core


def test_a_core_on_the_shelf_keeps_an_empty_opening():
    core = Core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    assert core_to_row(core).installed_at is None
    assert core_from_row(core_to_row(core)).opening == ""


def test_a_control_key_is_cut_and_read_back(session):
    service = with_a_key(session)
    assert service.key("CK-1").section == "A"
    assert service.key("CK-1").is_live


def test_a_control_key_code_is_not_reused(session):
    service = with_a_key(session)
    with pytest.raises(ConflictError):
        service.cut_key("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED)


def test_a_control_key_nobody_cut_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).key("CK-9")


def test_a_core_needs_a_control_key_that_exists(session):
    with pytest.raises(NotFoundError):
        a_service(session).register_core("K-1001", CoreFormat.SFIC_A2, "CK-9", HousingKind.MORTISE)


def test_a_core_of_the_wrong_format_is_refused(session):
    service = with_a_key(session)
    with pytest.raises(ConflictError) as caught:
        service.register_core("K-1001", CoreFormat.SFIC_A4, "CK-1", HousingKind.MORTISE)
    assert caught.value.facts["key_format"] == "small format, A2"


def test_a_core_on_a_door_nobody_has_is_refused(session):
    service = with_a_key(session)
    with pytest.raises(NotFoundError):
        service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-999")


def test_a_core_goes_on_the_register(session):
    service = with_a_key(session)
    row = service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    assert row.serial == "K-1001"
    assert not row.is_installed


def test_handing_a_control_key_over_records_who(session):
    service = with_a_key(session)
    assert service.hand_to("CK-1", "phamm").holder == "phamm"


def test_a_control_key_goes_to_somebody(session):
    service = with_a_key(session)
    with pytest.raises(ConflictError):
        service.hand_to("CK-1", "  ")


def test_a_withdrawn_key_cannot_be_handed_over(session):
    service = with_a_key(session)
    service.withdraw("CK-1", LATER)
    with pytest.raises(ConflictError):
        service.hand_to("CK-1", "phamm")


def test_a_key_with_cores_cannot_be_withdrawn(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    with pytest.raises(ConflictError) as caught:
        service.withdraw("CK-1", LATER)
    assert caught.value.facts["cores"] == 1


def test_a_key_cannot_be_withdrawn_twice(session):
    service = with_a_key(session)
    service.withdraw("CK-1", LATER)
    with pytest.raises(ConflictError):
        service.withdraw("CK-1", LATER)


def test_a_core_goes_into_a_door(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    assert service.install("K-1001", "MOO-101").installed_at == "MOO-101"


def test_a_housing_only_takes_one_core(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    service.register_core("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    service.install("K-1001", "MOO-101")
    with pytest.raises(ConflictError) as caught:
        service.install("K-1002", "MOO-101")
    assert caught.value.facts["serial"] == "K-1001"


def test_another_housing_on_the_same_door_is_fine(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    service.register_core("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.RIM)
    service.install("K-1001", "MOO-101")
    assert service.install("K-1002", "MOO-101").installed_at == "MOO-101"


def test_installing_a_core_into_a_door_nobody_has(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    with pytest.raises(NotFoundError):
        service.install("K-1001", "MOO-999")


def test_pulling_a_core_takes_it_off_the_wall(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101")
    assert service.pull("K-1001").installed_at is None


def test_a_core_already_off_the_wall_cannot_be_pulled(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    with pytest.raises(ConflictError):
        service.pull("K-1001")


def test_a_core_nobody_registered_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).core("K-9999")


def test_one_control_key_means_no_crossings(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    service.register_core("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    assert service.crossings() == ()


def test_two_control_keys_on_one_housing_kind_cross(session):
    service = with_a_key(session)
    service.cut_key("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE)
    service.register_core("K-1002", CoreFormat.SFIC_A2, "CK-2", HousingKind.MORTISE)
    assert service.crossings() == (("K-1001", "K-1002"),)


def test_the_report_says_how_many_sections_are_in_use(session):
    service = with_a_key(session)
    service.cut_key("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED)
    lines = service.report()
    assert lines[0] == "2 live control keys on 2 sections"
    assert any("more than one control section" in line for line in lines)


def test_the_sections_come_off_the_register(session):
    assert with_a_key(session).sections() == ("A",)


def test_the_holder_sheet_counts_the_keys_out(session):
    service = with_a_key(session)
    service.hand_to("CK-1", "phamm")
    assert service.holders()[0] == "1 control keys are out"


def test_the_repository_lists_the_live_keys(session):
    service = with_a_key(session)
    service.cut_key("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED)
    service.withdraw("CK-2", LATER)
    assert [row.code for row in ControlKeyRepository(session).live()] == ["CK-1"]


def test_the_repository_finds_who_holds_one(session):
    service = with_a_key(session)
    service.hand_to("CK-1", "phamm")
    assert [row.code for row in ControlKeyRepository(session).held_by("phamm")] == ["CK-1"]


def test_the_repository_splits_installed_from_shelved(session):
    service = with_a_key(session)
    service.register_core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101")
    service.register_core("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.RIM)
    cores = CoreRepository(session)
    assert [row.serial for row in cores.installed()] == ["K-1001"]
    assert [row.serial for row in cores.on_the_shelf()] == ["K-1002"]


def test_a_control_key_without_a_section_is_refused(session):
    session.add(
        ControlKeyRow(
            code="CK-9", section="", format="small format, A2", bitting=CUT, issued_on=ISSUED
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_key_withdrawn_before_it_was_issued_is_refused(session):
    session.add(
        ControlKeyRow(
            code="CK-9",
            section="A",
            format="small format, A2",
            bitting=CUT,
            issued_on=ISSUED,
            withdrawn_on=date(2024, 1, 1),
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_core_pulled_by_nothing_is_refused(session):
    session.add(
        CoreRow(
            serial="K-1001",
            format="small format, A2",
            control_code="CK-9",
            housing="mortise housing",
        )
    )
    with pytest.raises(IntegrityError):
        session.flush()

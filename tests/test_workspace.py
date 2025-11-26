from datetime import date

import pytest

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import KeywayError, NotFoundError
from hasplib.persistence.workspace import Workspace, workspace_for
from hasplib.settings import ShopSettings

TODAY = date(2025, 6, 30)
SYSTEM = "chester park"


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def test_a_fresh_workspace_holds_nothing(session):
    assert Workspace(session).loaded == ()


def test_asking_for_the_openings_builds_them_once(session):
    space = Workspace(session)
    first = space.openings()
    assert space.openings() is first
    assert space.loaded == ("openings",)


def test_the_coverage_and_the_register_are_held_apart(session):
    space = Workspace(session)
    space.coverage()
    space.register()
    assert space.loaded == ("coverage", "register")


def test_the_ledger_comes_back_the_same_object(session):
    space = Workspace(session)
    assert space.ledger() is space.ledger()


def test_the_deposits_come_back_the_same_object(session):
    space = Workspace(session)
    assert space.deposits() is space.deposits()


def test_the_directory_comes_back_the_same_object(session):
    space = Workspace(session)
    assert space.directory() is space.directory()


def test_the_quarantine_takes_the_window_it_was_given(session):
    space = Workspace(session, quarantine_years=3)
    assert space.quarantine().release_after_years == 3


def test_a_family_is_held_under_its_own_name(session):
    space = Workspace(session)
    space.family("Sargent LA")
    assert space.loaded == ("family:Sargent LA",)


def test_two_families_are_held_separately(session):
    space = Workspace(session)
    space.family("Sargent LA")
    with pytest.raises(KeywayError):
        space.family("nothing")
    assert "family:Sargent LA" in space.loaded


def test_a_schedule_is_held_under_its_system(session):
    space = Workspace(session)
    space.schedule(SYSTEM)
    assert f"schedule:{SYSTEM}" in space.loaded


def test_a_schedule_comes_back_the_same_object(session):
    space = Workspace(session)
    assert space.schedule(SYSTEM) is space.schedule(SYSTEM)


def test_a_system_nobody_opened_is_a_404(session):
    with pytest.raises(NotFoundError):
        Workspace(session).system("nowhere")


def test_a_schedule_for_a_system_nobody_opened_is_a_404(session):
    with pytest.raises(NotFoundError):
        Workspace(session).schedule("nowhere")


def test_forgetting_drops_everything(session):
    space = Workspace(session)
    space.openings()
    space.ledger()
    space.forget()
    assert space.loaded == ()


def test_forgetting_means_the_next_answer_is_rebuilt(session):
    space = Workspace(session)
    first = space.openings()
    space.forget()
    assert space.openings() is not first


def test_the_openings_are_read_once_for_a_whole_page(session):
    space = Workspace(session)
    space.coverage()
    space.register()
    space.openings()
    assert len(space.loaded) == 3


def test_what_a_person_holds_comes_off_the_ledger(session):
    space = Workspace(session)
    assert "41207" in space.person_holdings("kmaki")


def test_a_person_nobody_has_a_record_of_is_a_404(session):
    with pytest.raises(NotFoundError):
        Workspace(session).person_holdings("nobody")


def test_the_overdue_keys_come_off_the_ledger(session):
    space = Workspace(session)
    assert isinstance(space.overdue(TODAY), tuple)


def test_a_workspace_can_be_built_from_a_shop(session):
    class Shop:
        settings = ShopSettings()

    space = workspace_for(Shop(), session)
    assert space.quarantine_years == 7
    assert space.rules.max_run == ShopSettings().cutting.max_run


def test_the_incident_service_holds_one_coverage_map(session):
    from hasplib.services.incidents import IncidentService

    service = IncidentService(session)
    assert service.coverage() is service.coverage()


def test_the_incident_service_does_not_hold_the_ledger(session):
    from hasplib.services.incidents import IncidentService

    service = IncidentService(session)
    assert service.ledger() is not service.ledger()


def test_a_closed_key_is_gone_from_the_next_ledger_read(session):
    from datetime import date

    from hasplib.rekey.compromise import Cause
    from hasplib.services.incidents import IncidentService

    service = IncidentService(session)
    service.open_series()
    before = len(service.ledger().out())
    service.report("41207", Cause.LOST, date(2025, 6, 30))
    assert len(service.ledger().out()) == before - 1


def test_the_export_service_takes_a_workspace(session):
    from hasplib.services.exports import ExportService

    space = Workspace(session)
    service = ExportService(session, space)
    service.rows_for("openings")
    assert "openings" in space.loaded


def test_two_exports_share_the_openings(session):
    from hasplib.services.exports import ExportService

    space = Workspace(session)
    service = ExportService(session, space)
    service.rows_for("openings")
    service.rows_for("cylinders")
    assert space.loaded.count("openings") == 1


def test_an_export_service_makes_its_own_workspace_when_it_has_to(session):
    from hasplib.services.exports import ExportService

    service = ExportService(session)
    assert service.rows_for("openings")[1]

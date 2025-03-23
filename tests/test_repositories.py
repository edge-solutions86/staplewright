from datetime import date

import pytest

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import NotFoundError
from hasplib.persistence.models import (
    BuildingRow,
    ChartRow,
    CylinderRow,
    IncidentRow,
    IssuedKeyRow,
    KeyholderRow,
    KeyRequestRow,
    OpeningRow,
    RetiredBittingRow,
    ScheduleEntryRow,
    SystemRow,
    WorkOrderRow,
)
from hasplib.persistence.repositories import (
    ChartRepository,
    CylinderRepository,
    IncidentRepository,
    KeyholderRepository,
    KeyRepository,
    OpeningRepository,
    QuarantineRepository,
    RequestRepository,
    SystemRepository,
    WorkOrderRepository,
)

WHEN = date(2025, 3, 4)
DUE = date(2025, 5, 1)
LATER = date(2025, 6, 2)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_chart(session, family: str = "LA six pin", manufacturer: str = "Sargent") -> ChartRow:
    row = ChartRow(
        manufacturer=manufacturer,
        family=family,
        chambers=6,
        depths=tuple(range(10)),
        root_mils=335,
        increment_mils=15,
        first_space_mils=231,
        space_increment_mils=156,
        macs=7,
    )
    session.add(row)
    session.flush()
    return row


def a_campus(session) -> None:
    session.add_all(
        [BuildingRow(code="MOO", name="Moore Hall"), BuildingRow(code="VOS", name="Voss Hall")]
    )
    session.flush()
    session.add_all(
        [
            OpeningRow(
                number="MOO-101",
                building_code="MOO",
                floor="1",
                room="101",
                function="entrance",
                exterior=True,
            ),
            OpeningRow(
                number="MOO-214", building_code="MOO", floor="2", room="214", function="office"
            ),
            OpeningRow(
                number="VOS-311", building_code="VOS", floor="3", room="311", function="office"
            ),
        ]
    )
    session.flush()


def a_person(session, identifier: str, **overrides) -> KeyholderRow:
    settings = {
        "identifier": identifier,
        "name": identifier.title(),
        "affiliation": "faculty",
        "department": "geography",
    }
    settings.update(overrides)
    row = KeyholderRow(**settings)
    session.add(row)
    session.flush()
    return row


def test_the_base_finds_adds_counts_and_deletes(session):
    charts = ChartRepository(session)
    assert charts.count() == 0
    row = a_chart(session)
    assert charts.count() == 1
    assert charts.exists(row.id)
    assert charts.get(row.id).family == "LA six pin"
    charts.delete(row)
    session.flush()
    assert charts.count() == 0


def test_asking_for_something_that_is_not_there(session):
    charts = ChartRepository(session)
    assert charts.find(999) is None
    with pytest.raises(NotFoundError) as caught:
        charts.get(999)
    assert "chart" in caught.value.message


def test_charts_come_back_by_family_and_manufacturer(session):
    a_chart(session)
    a_chart(session, "60 five pin", "Corbin")
    charts = ChartRepository(session)
    assert charts.by_family("Sargent", "LA six pin").chambers == 6
    assert charts.by_family("Sargent", "nothing") is None
    assert [row.family for row in charts.for_manufacturer("Corbin")] == ["60 five pin"]


def test_systems_come_back_live_and_by_name(session):
    chart = a_chart(session)
    session.add_all(
        [
            SystemRow(
                name="chester park",
                chart_id=chart.id,
                family_name="Sargent LA",
                top_section="L",
            ),
            SystemRow(
                name="old library",
                chart_id=chart.id,
                family_name="Sargent LA",
                top_section="L",
                archived=True,
            ),
        ]
    )
    session.flush()
    systems = SystemRepository(session)
    assert [row.name for row in systems.live()] == ["chester park"]
    assert systems.by_name("old library").archived
    assert systems.by_name("nothing at all") is None


def test_a_systems_entries_and_uncut_symbols(session):
    chart = a_chart(session)
    system = SystemRow(
        name="chester park", chart_id=chart.id, family_name="Sargent LA", top_section="L"
    )
    session.add(system)
    session.flush()
    session.add_all(
        [
            ScheduleEntryRow(system_id=system.id, symbol="AA1", section="LC", bitting="342156"),
            ScheduleEntryRow(system_id=system.id, symbol="AA2", section="LC"),
        ]
    )
    session.flush()
    systems = SystemRepository(session)
    assert len(systems.entries(system.id)) == 2
    assert [row.symbol.render() for row in systems.uncut_symbols(system.id)] == ["AA2"]


def test_openings_come_back_by_building_and_by_exposure(session):
    a_campus(session)
    openings = OpeningRepository(session)
    assert [row.number for row in openings.in_building("MOO")] == ["MOO-101", "MOO-214"]
    assert [row.number for row in openings.exterior()] == ["MOO-101"]
    assert [row.code for row in openings.buildings()] == ["MOO", "VOS"]


def test_cylinders_come_back_by_where_they_are(session):
    a_campus(session)
    session.add_all(
        [
            CylinderRow(
                serial="C-1001",
                format="mortise",
                section="LC",
                chambers=6,
                installed_at="MOO-214",
                position="lockset",
            ),
            CylinderRow(serial="C-1099", format="mortise", section="LC", chambers=6),
        ]
    )
    session.flush()
    cylinders = CylinderRepository(session)
    assert [row.serial for row in cylinders.on_opening("MOO-214")] == ["C-1001"]
    assert [row.serial for row in cylinders.on_the_shelf()] == ["C-1099"]
    assert [row.serial for row in cylinders.unkeyed()] == ["C-1001"]
    assert cylinders.in_group(1) == []


def test_keyholders_come_back_by_department_and_by_lapse(session):
    a_person(session, "kmaki")
    a_person(session, "phamm", department="facilities")
    a_person(session, "jwexler", affiliation="contractor", ends_on=date(2025, 1, 31))
    people = KeyholderRepository(session)
    assert [row.identifier for row in people.in_department("geography")] == ["jwexler", "kmaki"]
    assert [row.identifier for row in people.lapsed(WHEN)] == ["jwexler"]
    assert people.lapsed(date(2025, 1, 1)) == []


def test_keys_come_back_by_state_holder_symbol_and_lateness(session):
    a_person(session, "kmaki")
    a_person(session, "dsalo")
    session.add_all(
        [
            IssuedKeyRow(
                tag="41207",
                symbol="AA1",
                keyholder="kmaki",
                issued_on=WHEN,
                due_on=DUE,
                deposit_cents=2500,
            ),
            IssuedKeyRow(
                tag="41208",
                symbol="AA1",
                keyholder="dsalo",
                issued_on=WHEN,
                deposit_cents=2500,
            ),
            IssuedKeyRow(
                tag="41209",
                symbol="AA",
                keyholder="dsalo",
                issued_on=WHEN,
                state="returned",
                closed_on=LATER,
                deposit_cents=5000,
            ),
        ]
    )
    session.flush()
    keys = KeyRepository(session)
    assert [row.tag for row in keys.out()] == ["41207", "41208"]
    assert [row.tag for row in keys.held_by("dsalo")] == ["41208"]
    assert [row.tag for row in keys.cut_to("AA1")] == ["41207", "41208"]
    assert [row.tag for row in keys.overdue(LATER)] == ["41207"]
    assert keys.overdue(WHEN) == []
    assert keys.deposits_held() == 5000


def test_deposits_held_is_nothing_when_no_key_is_out(session):
    assert KeyRepository(session).deposits_held() == 0


def test_requests_come_back_by_state_and_by_person(session):
    a_person(session, "kmaki")
    session.add_all(
        [
            KeyRequestRow(
                number="R-001",
                keyholder="kmaki",
                symbol="AA1",
                building="MOO",
                reason="moved in",
                raised_on=WHEN,
                state="submitted",
            ),
            KeyRequestRow(
                number="R-002",
                keyholder="kmaki",
                symbol="AA",
                building="MOO",
                reason="needs the floor",
                raised_on=WHEN,
            ),
        ]
    )
    session.flush()
    requests = RequestRepository(session)
    assert [row.number for row in requests.in_state("submitted")] == ["R-001"]
    assert len(requests.for_keyholder("kmaki")) == 2


def test_incidents_come_back_by_date_and_severity(session):
    session.add_all(
        [
            IncidentRow(number="I-1", tag="41207", symbol="AA1", cause="lost", reported_on=WHEN),
            IncidentRow(
                number="I-2",
                tag="41209",
                symbol="AA",
                cause="lost",
                reported_on=LATER,
                severity="serious",
            ),
        ]
    )
    session.flush()
    incidents = IncidentRepository(session)
    assert [row.number for row in incidents.since(LATER)] == ["I-2"]
    assert [row.number for row in incidents.by_severity("serious")] == ["I-2"]


def test_work_orders_come_back_open_by_person_and_by_day(session):
    session.add_all(
        [
            WorkOrderRow(number="W-1", state="scheduled", technician="jkoivu", scheduled_for=LATER),
            WorkOrderRow(number="W-2", state="done", technician="jkoivu", closed_on=LATER),
        ]
    )
    session.flush()
    orders = WorkOrderRepository(session)
    assert [row.number for row in orders.open_orders()] == ["W-1"]
    assert len(orders.for_technician("jkoivu")) == 2
    assert [row.number for row in orders.due_on(LATER)] == ["W-1"]


def test_retired_cuts_come_back_by_cut_and_by_permanence(session):
    session.add_all(
        [
            RetiredBittingRow(bitting="342156", reason="compromised", retired_on=WHEN),
            RetiredBittingRow(bitting="362356", reason="superseded", retired_on=WHEN),
        ]
    )
    session.flush()
    quarantine = QuarantineRepository(session)
    assert quarantine.by_bitting("342156").reason == "compromised"
    assert quarantine.by_bitting("999999") is None
    assert [row.bitting.render() for row in quarantine.permanent()] == ["342156"]


def test_add_all_puts_a_batch_in_at_once(session):
    charts = ChartRepository(session)
    charts.add_all([])
    assert charts.count() == 0

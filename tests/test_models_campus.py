import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    AccessGroupRow,
    AccessRequirementRow,
    BuildingRow,
    CylinderRow,
    OpeningRow,
)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_campus(session) -> None:
    session.add(BuildingRow(code="MOO", name="Moore Hall", year_built=1953))
    session.add(BuildingRow(code="VOS", name="Voss Hall"))
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


def a_cylinder(**overrides) -> CylinderRow:
    settings = {"serial": "C-1001", "format": "mortise", "section": "LC", "chambers": 6}
    settings.update(overrides)
    return CylinderRow(**settings)


def test_the_campus_tables_are_on_the_metadata():
    names = table_names()
    for wanted in ("buildings", "openings", "cylinders", "access_groups"):
        assert wanted in names


def test_a_building_holds_its_openings(session):
    a_campus(session)
    moore = session.get(BuildingRow, "MOO")
    assert sorted(o.number for o in moore.openings) == ["MOO-101", "MOO-214"]
    assert "MOO" in repr(moore)


def test_a_year_before_the_town_existed_is_refused(session):
    session.add(BuildingRow(code="OLD", name="Impossible Hall", year_built=1700))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_building_with_no_year_is_fine(session):
    session.add(BuildingRow(code="NEW", name="Annex"))
    session.flush()
    assert session.get(BuildingRow, "NEW").year_built is None


def test_an_opening_needs_a_building_that_exists(session):
    row = OpeningRow(number="ZZZ-1", building_code="ZZZ", floor="1", room="1", function="office")
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_room_appears_once_on_a_floor(session):
    a_campus(session)
    row = OpeningRow(
        number="MOO-215", building_code="MOO", floor="2", room="214", function="office"
    )
    session.add(row)
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_same_room_number_on_another_floor_is_fine(session):
    a_campus(session)
    row = OpeningRow(
        number="MOO-314", building_code="MOO", floor="3", room="214", function="office"
    )
    session.add(row)
    session.flush()
    assert session.get(OpeningRow, "MOO-314").floor == "3"


def test_a_shelved_cylinder_has_neither_door_nor_position(session):
    a_campus(session)
    session.add(a_cylinder())
    session.flush()
    found = session.get(CylinderRow, "C-1001")
    assert not found.is_installed
    assert found.opening is None
    assert "C-1001" in repr(found)


def test_an_installed_cylinder_points_at_its_door(session):
    a_campus(session)
    session.add(a_cylinder(installed_at="MOO-214", position="lockset"))
    session.flush()
    found = session.get(CylinderRow, "C-1001")
    assert found.is_installed
    assert found.opening.room == "214"


def test_a_door_lists_what_is_in_it(session):
    a_campus(session)
    session.add(a_cylinder(installed_at="MOO-214", position="lockset"))
    session.add(a_cylinder(serial="C-1002", installed_at="MOO-214", position="deadbolt"))
    session.flush()
    opening = session.get(OpeningRow, "MOO-214")
    assert sorted(c.serial for c in opening.cylinders) == ["C-1001", "C-1002"]


def test_two_cylinders_cannot_share_a_position(session):
    a_campus(session)
    session.add(a_cylinder(installed_at="MOO-214", position="lockset"))
    session.flush()
    session.add(a_cylinder(serial="C-1002", installed_at="MOO-214", position="lockset"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_any_number_of_cylinders_can_be_on_the_shelf(session):
    a_campus(session)
    session.add_all([a_cylinder(), a_cylinder(serial="C-1002"), a_cylinder(serial="C-1003")])
    session.flush()
    assert len(session.execute(select(CylinderRow)).scalars().all()) == 3


def test_a_cylinder_cannot_be_half_installed(session):
    a_campus(session)
    session.add(a_cylinder(installed_at="MOO-214"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_position_without_a_door_is_refused(session):
    a_campus(session)
    session.add(a_cylinder(position="lockset"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_cylinder_with_no_chambers_is_refused(session):
    a_campus(session)
    session.add(a_cylinder(chambers=0))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_cylinder_in_a_door_nobody_has_heard_of(session):
    a_campus(session)
    session.add(a_cylinder(installed_at="ZZZ-1", position="lockset"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_an_access_group_holds_its_requirements(session):
    a_campus(session)
    group = AccessGroupRow(name="geography faculty", department="geography")
    session.add(group)
    session.flush()
    session.add_all(
        [
            AccessRequirementRow(group_id=group.id, opening_number="MOO-214"),
            AccessRequirementRow(group_id=group.id, opening_number="MOO-101"),
        ]
    )
    session.flush()
    session.refresh(group)
    assert len(group.requirements) == 2
    assert "geography faculty" in repr(group)


def test_a_group_asks_for_a_door_once(session):
    a_campus(session)
    group = AccessGroupRow(name="geography faculty")
    session.add(group)
    session.flush()
    session.add(AccessRequirementRow(group_id=group.id, opening_number="MOO-214"))
    session.flush()
    session.add(AccessRequirementRow(group_id=group.id, opening_number="MOO-214"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_group_name_is_unique(session):
    session.add(AccessGroupRow(name="geography faculty"))
    session.flush()
    session.add(AccessGroupRow(name="geography faculty"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_group_takes_its_requirements(session):
    a_campus(session)
    group = AccessGroupRow(name="geography faculty")
    session.add(group)
    session.flush()
    session.add(AccessRequirementRow(group_id=group.id, opening_number="MOO-214"))
    session.flush()
    session.delete(group)
    session.flush()
    assert session.execute(select(AccessRequirementRow)).scalars().all() == []

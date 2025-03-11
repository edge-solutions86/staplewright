import pytest
from sqlalchemy import select

from campus import a_campus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.campus.access import AccessGroup, AccessMatrix
from hasplib.campus.hardware import Cylinder, CylinderFormat, Position
from hasplib.campus.openings import Building, Function
from hasplib.errors import InputError
from hasplib.persistence.mappers.campus import (
    access_group_from_row,
    access_group_to_row,
    building_from_row,
    building_to_row,
    cylinder_from_row,
    cylinder_to_row,
    matrix_from_rows,
    matrix_to_rows,
    opening_from_row,
    opening_to_row,
    register_from_rows,
    schedule_from_rows,
)
from hasplib.persistence.models import (
    AccessGroupRow,
    AccessRequirementRow,
    BuildingRow,
    CylinderRow,
    OpeningRow,
)
from keyed import a_register


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def store_campus(session) -> None:
    schedule = a_campus()
    for building in schedule.buildings():
        session.add(building_to_row(building))
    session.flush()
    for opening in schedule:
        session.add(opening_to_row(opening))
    session.flush()


def test_a_building_survives_a_round_trip():
    building = Building("MOO", "Moore Hall", 1953)
    assert building_from_row(building_to_row(building)) == building


def test_a_building_with_no_year_survives():
    building = Building("NEW", "Annex")
    assert building_from_row(building_to_row(building)).year_built is None


def test_an_opening_survives_a_round_trip():
    opening = a_campus().opening("MOO-101")
    assert opening_from_row(opening_to_row(opening)) == opening


@pytest.mark.parametrize("number", ["MOO-101", "MOO-218", "MOO-260", "HEI-G12"])
def test_every_kind_of_opening_survives(number):
    opening = a_campus().opening(number)
    assert opening_from_row(opening_to_row(opening)) == opening


def test_a_whole_campus_comes_back(session):
    store_campus(session)
    buildings = session.execute(select(BuildingRow)).scalars().all()
    openings = session.execute(select(OpeningRow)).scalars().all()
    back = schedule_from_rows(buildings, openings)
    assert len(back) == len(a_campus())
    assert back.opening("MOO-101").exterior
    assert back.opening("MOO-260").function is Function.PRIVACY


def test_a_function_nobody_has_heard_of_is_refused():
    row = opening_to_row(a_campus().opening("MOO-214"))
    row.function = "hovercraft"
    with pytest.raises(InputError):
        opening_from_row(row)


def test_a_shelved_cylinder_survives_a_round_trip():
    cylinder = Cylinder("C-1001", CylinderFormat.MORTISE, "LC", 6, manufacturer="Sargent")
    assert cylinder_from_row(cylinder_to_row(cylinder)) == cylinder


def test_an_installed_cylinder_survives_a_round_trip():
    register = a_register()
    cylinder = register.cylinder("C-1001")
    assert cylinder_from_row(cylinder_to_row(cylinder)).installed_at == "MOO-214"
    assert cylinder_from_row(cylinder_to_row(cylinder)).position is Position.LOCKSET


def test_a_format_nobody_has_heard_of_is_refused():
    row = cylinder_to_row(Cylinder("C-1", CylinderFormat.MORTISE, "LC", 6))
    row.format = "wafer tumbler"
    with pytest.raises(InputError):
        cylinder_from_row(row)


def test_a_position_nobody_has_heard_of_is_refused():
    register = a_register()
    row = cylinder_to_row(register.cylinder("C-1001"))
    row.position = "somewhere"
    with pytest.raises(InputError):
        cylinder_from_row(row)


def test_a_whole_register_comes_back(session):
    store_campus(session)
    register = a_register()
    for cylinder in register:
        session.add(cylinder_to_row(cylinder))
    session.flush()
    rows = session.execute(select(CylinderRow)).scalars().all()
    buildings = session.execute(select(BuildingRow)).scalars().all()
    openings = session.execute(select(OpeningRow)).scalars().all()
    back = register_from_rows(schedule_from_rows(buildings, openings), rows)
    assert len(back) == len(register)
    assert back.at("MOO-214", Position.LOCKSET).serial == "C-1001"
    assert [c.serial for c in back.on_the_shelf()] == ["C-1099"]


def test_the_group_names_come_back_when_they_are_given():
    register = a_register()
    rows = [cylinder_to_row(register.cylinder("C-1001"), group_id=7)]
    back = register_from_rows(a_campus(), rows, {7: "moore offices"})
    assert back.cylinder("C-1001").group == "moore offices"


def test_a_group_nobody_named_leaves_the_cylinder_unkeyed():
    register = a_register()
    rows = [cylinder_to_row(register.cylinder("C-1001"), group_id=99)]
    back = register_from_rows(a_campus(), rows, {})
    assert back.cylinder("C-1001").group == ""


def test_an_access_group_survives_a_round_trip():
    group = AccessGroup("geography faculty", "everybody who teaches", "geography")
    assert access_group_from_row(access_group_to_row(group)) == group


def test_a_matrix_survives_the_database(session):
    store_campus(session)
    matrix = AccessMatrix(a_campus())
    matrix.add_group(AccessGroup("geography faculty", department="geography"))
    matrix.require_all("geography faculty", ["MOO-214", "MOO-216"])
    for row, wanted in matrix_to_rows(matrix):
        session.add(row)
        session.flush()
        for number in wanted:
            session.add(AccessRequirementRow(group_id=row.id, opening_number=number))
    session.flush()
    groups = session.execute(select(AccessGroupRow)).scalars().all()
    wants = session.execute(select(AccessRequirementRow)).scalars().all()
    back = matrix_from_rows(a_campus(), groups, wants)
    assert back.openings_for("geography faculty") == ("MOO-214", "MOO-216")


def test_a_group_with_no_doors_survives(session):
    store_campus(session)
    matrix = AccessMatrix(a_campus())
    matrix.add_group(AccessGroup("new hires"))
    rows = matrix_to_rows(matrix)
    assert rows[0][1] == []

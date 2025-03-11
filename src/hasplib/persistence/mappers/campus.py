"""Buildings, openings, cylinders and the access matrix, both directions.

The enums are the only thing worth watching here. A function or a cylinder
format is stored as its own text rather than as a database enum type, because
adding a value to a database enum is a migration and adding one to a Python
enum is a line of code, and this shop buys hardware faster than it deploys.

So the string in the column is the enum value, and reading it back is a
lookup that fails loudly rather than quietly producing something else.
"""

from __future__ import annotations

from collections.abc import Sequence

from hasplib.campus.access import AccessGroup, AccessMatrix
from hasplib.campus.hardware import Cylinder, CylinderFormat, HardwareRegister, Position
from hasplib.campus.openings import Building, Function, Opening, OpeningSchedule
from hasplib.errors import InputError
from hasplib.persistence.models.campus import (
    AccessGroupRow,
    AccessRequirementRow,
    BuildingRow,
    CylinderRow,
    OpeningRow,
)


def _function(value: str) -> Function:
    try:
        return Function(value)
    except ValueError:
        raise InputError("no such door function", function=value) from None


def _format(value: str) -> CylinderFormat:
    try:
        return CylinderFormat(value)
    except ValueError:
        raise InputError("no such cylinder format", format=value) from None


def _position(value: str | None) -> Position | None:
    if value is None:
        return None
    try:
        return Position(value)
    except ValueError:
        raise InputError("no such cylinder position", position=value) from None


def building_to_row(building: Building) -> BuildingRow:
    return BuildingRow(code=building.code, name=building.name, year_built=building.year_built)


def building_from_row(row: BuildingRow) -> Building:
    return Building(code=row.code, name=row.name, year_built=row.year_built)


def opening_to_row(opening: Opening) -> OpeningRow:
    return OpeningRow(
        number=opening.number,
        building_code=opening.building,
        floor=opening.floor,
        room=opening.room,
        function=opening.function.value,
        exterior=opening.exterior,
        description=opening.description,
    )


def opening_from_row(row: OpeningRow) -> Opening:
    return Opening(
        number=row.number,
        building=row.building_code,
        floor=row.floor,
        room=row.room,
        function=_function(row.function),
        description=row.description,
        exterior=row.exterior,
    )


def schedule_from_rows(
    buildings: Sequence[BuildingRow], openings: Sequence[OpeningRow]
) -> OpeningSchedule:
    schedule = OpeningSchedule()
    for building in buildings:
        schedule.add_building(building_from_row(building))
    for opening in openings:
        schedule.add(opening_from_row(opening))
    return schedule


def cylinder_to_row(cylinder: Cylinder, group_id: int | None = None) -> CylinderRow:
    return CylinderRow(
        serial=cylinder.serial,
        format=cylinder.format.value,
        section=cylinder.section,
        chambers=cylinder.chambers,
        manufacturer=cylinder.manufacturer,
        spooled=cylinder.spooled,
        installed_at=cylinder.installed_at,
        position=cylinder.position.value if cylinder.position else None,
        group_id=group_id,
    )


def cylinder_from_row(row: CylinderRow, group_name: str = "") -> Cylinder:
    return Cylinder(
        serial=row.serial,
        format=_format(row.format),
        section=row.section,
        chambers=row.chambers,
        manufacturer=row.manufacturer,
        spooled=row.spooled,
        installed_at=row.installed_at,
        position=_position(row.position),
        group=group_name,
    )


def register_from_rows(
    openings: OpeningSchedule,
    rows: Sequence[CylinderRow],
    group_names: dict[int, str] | None = None,
) -> HardwareRegister:
    """Rebuild the register, putting each cylinder back where it was.

    Cylinders join the register shelved and get installed afterwards, which
    is the same path a new one takes at the counter, so the register cannot
    end up holding a state the front door would have refused.
    """
    names = group_names or {}
    register = HardwareRegister(openings)
    for row in rows:
        register.add(
            Cylinder(
                serial=row.serial,
                format=_format(row.format),
                section=row.section,
                chambers=row.chambers,
                manufacturer=row.manufacturer,
                spooled=row.spooled,
            )
        )
        if row.group_id is not None and row.group_id in names:
            register.assign_group(row.serial, names[row.group_id])
        position = _position(row.position)
        if row.installed_at and position is not None:
            register.install(row.serial, row.installed_at, position)
    return register


def access_group_to_row(group: AccessGroup) -> AccessGroupRow:
    return AccessGroupRow(
        name=group.name, department=group.department, description=group.description
    )


def access_group_from_row(row: AccessGroupRow) -> AccessGroup:
    return AccessGroup(name=row.name, description=row.description, department=row.department)


def matrix_to_rows(matrix: AccessMatrix) -> list[tuple[AccessGroupRow, list[str]]]:
    """A group row and the openings it wants, ids left for the caller."""
    return [
        (access_group_to_row(group), list(matrix.openings_for(group.name)))
        for group in matrix.groups()
    ]


def matrix_from_rows(
    openings: OpeningSchedule,
    groups: Sequence[AccessGroupRow],
    requirements: Sequence[AccessRequirementRow],
) -> AccessMatrix:
    matrix = AccessMatrix(openings)
    by_id: dict[int, str] = {}
    for group in groups:
        matrix.add_group(access_group_from_row(group))
        by_id[group.id] = group.name
    for wanted in requirements:
        matrix.require(by_id[wanted.group_id], wanted.opening_number)
    return matrix

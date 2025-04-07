"""A whole campus written into a database, for the service tests.

Buildings, doors, cylinders, a keying system with its groups, people and the
keys they hold. Everything the incident and reporting services need to answer
a real question, built once here rather than four times over.
"""

from campus import a_campus
from charts import six_pin_chart
from hasplib.persistence.mappers.campus import (
    building_to_row,
    cylinder_to_row,
    opening_to_row,
)
from hasplib.persistence.mappers.control import authorizer_to_row, keyholder_to_row
from hasplib.persistence.mappers.reference import chart_to_row
from hasplib.persistence.models import (
    GroupMemberRow,
    GroupRow,
    IssuedKeyRow,
    ScheduleEntryRow,
    SystemRow,
)
from keyed import GROUPS, INSTALLED, a_register
from people import TODAY, a_directory

CUTS = {
    "GGM": "442446",
    "A": "642446",
    "B": "242446",
    "AA": "622446",
    "AA1": "620446",
    "AA2": "624446",
    "AB1": "662046",
    "BA1": "246446",
}

HELD = [
    ("41207", "AA1", "kmaki"),
    ("41208", "AA1", "dsalo"),
    ("41209", "AA", "rvirta"),
    ("41210", "A", "phamm"),
    ("41211", "GGM", "phamm"),
]


def build(session) -> SystemRow:
    """Write the whole campus down and hand back the keying system."""
    chart = chart_to_row(six_pin_chart())
    session.add(chart)
    session.flush()

    system = SystemRow(
        name="chester park", chart_id=chart.id, family_name="Sargent LA", top_section="L"
    )
    session.add(system)
    session.flush()

    for symbol, cut in CUTS.items():
        session.add(ScheduleEntryRow(system_id=system.id, symbol=symbol, section="LC", bitting=cut))
    session.flush()

    group_ids: dict[str, int] = {}
    for name, symbols in GROUPS.items():
        row = GroupRow(system_id=system.id, name=name)
        session.add(row)
        session.flush()
        group_ids[name] = row.id
        for symbol in symbols:
            session.add(GroupMemberRow(group_id=row.id, symbol=symbol))
    session.flush()

    schedule = a_campus()
    for building in schedule.buildings():
        session.add(building_to_row(building))
    session.flush()
    for opening in schedule:
        session.add(opening_to_row(opening))
    session.flush()

    register = a_register()
    by_serial = {serial: group for serial, _, group in INSTALLED}
    for cylinder in register:
        session.add(cylinder_to_row(cylinder, group_ids.get(by_serial.get(cylinder.serial, ""))))
    session.flush()

    directory = a_directory()
    for person in directory:
        session.add(keyholder_to_row(person))
    session.flush()
    for signer in directory.authorizers():
        session.add(authorizer_to_row(signer))
    session.flush()

    for tag, symbol, holder in HELD:
        session.add(IssuedKeyRow(tag=tag, symbol=symbol, keyholder=holder, issued_on=TODAY))
    session.flush()
    return system

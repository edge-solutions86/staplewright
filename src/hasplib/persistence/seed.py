"""A worked example campus, for the half hour after somebody installs this.

An empty database is the worst way to meet a library like this. Nothing has a
symbol, so nothing can be cut, so nothing can be pinned, and the first thing
anybody does is type a keying system in by hand to see whether any of it
works. This builds one instead.

It is a small campus and it is deliberately not a tidy one. There is a
building on a keyway whose patent has run out, a key out to somebody whose
appointment ended, a keyed door with nothing in it, and a stocktake that came
up twelve blanks short. Every one of those is something the self check finds,
and a demo where the self check finds nothing teaches nobody anything.
"""

from __future__ import annotations

from datetime import date, timedelta

from sqlalchemy.orm import Session

from hasplib.campus.hardware import Cylinder, CylinderFormat, Position
from hasplib.campus.openings import Building, Function, Opening
from hasplib.control.escrow import BoxPolicy
from hasplib.control.people import Affiliation, Keyholder, Standing
from hasplib.errors import ConflictError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.campus import (
    building_to_row,
    cylinder_to_row,
    opening_to_row,
)
from hasplib.persistence.mappers.control import keyholder_to_row
from hasplib.persistence.mappers.reference import chart_to_row
from hasplib.persistence.models import (
    BlankRow,
    GroupMemberRow,
    GroupRow,
    IssuedKeyRow,
    KeywaySectionRow,
    ScheduleEntryRow,
    SystemRow,
)
from hasplib.services.escrow import EscrowService
from hasplib.services.stock import StockService
from hasplib.spec.catalog import SARGENT_LA
from hasplib.spec.keyway import KeywayFamily, KeywaySection

FAMILY = "Sargent LA"
SYSTEM = "chester park"
OPENED = date(2024, 8, 19)

CUTS = {
    "GGM": "442446",
    "A": "642446",
    "B": "242446",
    "AA": "622446",
    "AA1": "620446",
    "AA2": "624446",
    "AB": "662446",
    "AB1": "662046",
    "BA": "246446",
    "BA1": "246046",
}

GROUPS = {
    "moore offices": ("GGM", "A", "AA", "AA1"),
    "moore entrance": ("GGM", "A"),
    "voss offices": ("GGM", "B", "BA", "BA1"),
    "shops": ("GGM",),
}

BUILDINGS = (
    ("MOO", "Moore Hall", 1953),
    ("VOS", "Voss Hall", 1971),
    ("HEI", "Heikkinen Shops", 1938),
)

OPENINGS = (
    ("MOO-101", "MOO", "1", "101", Function.ENTRANCE, "north door", True),
    ("MOO-110", "MOO", "1", "110", Function.CLASSROOM, "", False),
    ("MOO-214", "MOO", "2", "214", Function.OFFICE, "", False),
    ("MOO-216", "MOO", "2", "216", Function.OFFICE, "", False),
    ("MOO-218", "MOO", "2", "218B", Function.STOREROOM, "map room", False),
    ("MOO-260", "MOO", "2", "260", Function.PRIVACY, "staff washroom", False),
    ("VOS-100", "VOS", "1", "100", Function.ENTRANCE, "lake side", True),
    ("VOS-311", "VOS", "3", "311", Function.OFFICE, "", False),
    ("VOS-312", "VOS", "3", "312", Function.OFFICE, "", False),
    ("HEI-001", "HEI", "B", "001", Function.STOREROOM, "paint cage", False),
    ("HEI-G12", "HEI", "1", "G12", Function.PADLOCK, "salt shed gate", False),
)

CYLINDERS = (
    ("C-1001", "MOO-101", Position.LOCKSET, "moore entrance"),
    ("C-1002", "MOO-214", Position.LOCKSET, "moore offices"),
    ("C-1003", "MOO-216", Position.LOCKSET, "moore offices"),
    ("C-1004", "VOS-100", Position.LOCKSET, "voss offices"),
    ("C-1005", "VOS-311", Position.LOCKSET, "voss offices"),
    ("C-1006", "HEI-001", Position.LOCKSET, "shops"),
    ("C-1007", None, None, "shops"),
)

PEOPLE = (
    ("kmaki", "K Maki", Affiliation.STAFF, "facilities", Standing.ACTIVE, None),
    ("dsalo", "D Salo", Affiliation.STAFF, "facilities", Standing.ACTIVE, None),
    ("rvirta", "R Virta", Affiliation.STAFF, "registrar", Standing.ACTIVE, None),
    ("phamm", "P Hamm", Affiliation.STAFF, "facilities", Standing.ACTIVE, None),
    (
        "jkoivu",
        "J Koivu",
        Affiliation.CONTRACTOR,
        "facilities",
        Standing.ACTIVE,
        date(2026, 6, 30),
    ),
    ("ovirta", "O Virta", Affiliation.STUDENT, "registrar", Standing.ACTIVE, date(2025, 2, 1)),
)

HELD = (
    ("41207", "AA1", "kmaki"),
    ("41208", "AA1", "dsalo"),
    ("41209", "AA", "rvirta"),
    ("41210", "A", "phamm"),
    ("41211", "GGM", "phamm"),
    ("41212", "BA1", "ovirta"),
)

BLANKS = (
    ("1145A", "LC", True, 50, 200),
    ("RA4", "L", False, 20, 40),
)


def demo_family() -> KeywayFamily:
    """The family, with one section whose patent has already run out."""
    family = KeywayFamily(FAMILY, KeywaySection("L", "Sargent"))
    family.add(KeywaySection("LA", "Sargent"), under="L")
    family.add(KeywaySection("LB", "Sargent"), under="L")
    family.add(
        KeywaySection("LC", "Sargent", restricted=True, patent_expires="2031-04-01"),
        under="LA",
    )
    family.add(
        KeywaySection("LD", "Sargent", restricted=True, patent_expires="2023-11-30"),
        under="LA",
    )
    family.add(KeywaySection("LE", "Sargent"), under="LB")
    return family


def _sections(session: Session) -> dict[str, int]:
    family = demo_family()
    found: dict[str, int] = {}
    for code in sorted(family.codes(), key=family.depth_of):
        section = family.section(code)
        parent = family.parent_of(code)
        row = KeywaySectionRow(
            family_name=family.name,
            code=section.code,
            manufacturer=section.manufacturer,
            restricted=section.restricted,
            patent_expires=(
                date.fromisoformat(section.patent_expires) if section.patent_expires else None
            ),
            parent_id=found.get(parent.code) if parent else None,
        )
        session.add(row)
        session.flush()
        found[code] = row.id
    return found


def _system(session: Session) -> SystemRow:
    chart = chart_to_row(SARGENT_LA)
    session.add(chart)
    session.flush()
    row = SystemRow(
        name=SYSTEM,
        chart_id=chart.id,
        family_name=FAMILY,
        top_section="L",
        opened_on=OPENED,
    )
    session.add(row)
    session.flush()
    for symbol, cut in CUTS.items():
        session.add(
            ScheduleEntryRow(
                system_id=row.id,
                symbol=KeySymbol.parse(symbol),
                section="LC",
                bitting=cut,
            )
        )
    session.flush()
    return row


def _groups(session: Session, system: SystemRow) -> dict[str, int]:
    found: dict[str, int] = {}
    for name, symbols in GROUPS.items():
        row = GroupRow(system_id=system.id, name=name)
        session.add(row)
        session.flush()
        found[name] = row.id
        for symbol in symbols:
            session.add(GroupMemberRow(group_id=row.id, symbol=KeySymbol.parse(symbol)))
    session.flush()
    return found


def _campus(session: Session, groups: dict[str, int]) -> None:
    for code, name, built in BUILDINGS:
        session.add(building_to_row(Building(code, name, built)))
    session.flush()
    for number, building, floor, room, function, note, exterior in OPENINGS:
        session.add(
            opening_to_row(
                Opening(number, building, floor, room, function, note, exterior=exterior)
            )
        )
    session.flush()
    for serial, opening, position, group in CYLINDERS:
        cylinder = Cylinder(
            serial=serial,
            format=CylinderFormat.MORTISE,
            section="LC",
            chambers=6,
            installed_at=opening,
            position=position,
            group=group,
        )
        session.add(cylinder_to_row(cylinder, groups.get(group)))
    session.flush()


def _people(session: Session, today: date) -> None:
    for identifier, name, affiliation, department, standing, ends in PEOPLE:
        session.add(
            keyholder_to_row(
                Keyholder(
                    identifier=identifier,
                    name=name,
                    affiliation=affiliation,
                    department=department,
                    standing=standing,
                    ends_on=ends,
                )
            )
        )
    session.flush()
    for tag, symbol, holder in HELD:
        session.add(
            IssuedKeyRow(
                tag=tag,
                symbol=symbol,
                keyholder=holder,
                issued_on=today - timedelta(days=200),
                due_on=today + timedelta(days=165),
                state="out",
            )
        )
    session.flush()


def _stock(session: Session, sections: dict[str, int], today: date) -> None:
    for part, section, restricted, reorder, _held in BLANKS:
        session.add(
            BlankRow(
                part_number=part,
                section_id=sections[section],
                manufacturer="Sargent",
                chambers=6,
                restricted=restricted,
                reorder_at=reorder,
            )
        )
    session.flush()
    stock = StockService(session)
    for part, _, _, _, held in BLANKS:
        stock.receive(part, held, today - timedelta(days=120), reference="PO-4412")
    stock.cut("1145A", 6, today - timedelta(days=60), reference="41207")
    stock.count("1145A", 182, today - timedelta(days=10))


def _boxes(session: Session, today: date) -> None:
    boxes = EscrowService(session, BoxPolicy())
    boxes.install(
        "KB-01", "MOO", "MOO-101", KeySymbol.parse("AA1"), "44012", today - timedelta(days=300)
    )
    boxes.install(
        "KB-02", "VOS", "VOS-100", KeySymbol.parse("BA1"), "44013", today - timedelta(days=40)
    )


def seed(session: Session, today: date | None = None) -> SystemRow:
    """Write the whole worked example down and hand back the keying system.

    Refuses on a database that already has a system in it, because seeding
    over live data is the sort of thing that gets done once at three in the
    afternoon and found out about at nine the next morning.
    """
    day = today or date.today()
    if session.get(SystemRow, 1) is not None:
        raise ConflictError("there is already a keying system in this database")
    sections = _sections(session)
    system = _system(session)
    groups = _groups(session, system)
    _campus(session, groups)
    _people(session, day)
    _stock(session, sections, day)
    _boxes(session, day)
    return system


def summary() -> tuple[str, ...]:
    """What the demo campus contains, for the line printed after seeding."""
    return (
        f"{len(BUILDINGS)} buildings, {len(OPENINGS)} openings, {len(CYLINDERS)} cylinders",
        f"{len(CUTS)} symbols on {SYSTEM}, {len(GROUPS)} keying groups",
        f"{len(PEOPLE)} keyholders holding {len(HELD)} keys",
        f"{len(BLANKS)} blank part numbers and two sealed boxes",
        "the self check finds several things wrong on purpose",
    )

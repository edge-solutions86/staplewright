"""A campus with cylinders in the doors and keys in people's pockets.

Three records have to agree for any coverage question to mean anything, so
they are built together here rather than in every test that needs them.
"""

from campus import a_campus
from hasplib.campus.coverage import CoverageMap
from hasplib.campus.hardware import (
    Cylinder,
    CylinderFormat,
    HardwareRegister,
    Position,
)
from hasplib.control.issue import KeyLedger
from hasplib.keys.symbol import KeySymbol
from hasplib.progression.groups import KeyingGroup
from people import TODAY, a_directory

GROUPS = {
    "moore offices": ("GGM", "A", "AA", "AA1"),
    "moore storeroom": ("GGM", "A", "AA", "AA2"),
    "moore classroom": ("GGM", "A", "AB1"),
    "moore entrance": ("GGM", "A"),
    "shops": ("GGM", "B", "BA1"),
}

INSTALLED = [
    ("C-1001", "MOO-214", "moore offices"),
    ("C-1002", "MOO-216", "moore offices"),
    ("C-1003", "MOO-218", "moore storeroom"),
    ("C-1004", "MOO-110", "moore classroom"),
    ("C-1005", "MOO-101", "moore entrance"),
    ("C-1006", "HEI-001", "shops"),
]


def keying_groups() -> dict[str, KeyingGroup]:
    return {
        name: KeyingGroup(name, tuple(KeySymbol.parse(text) for text in symbols))
        for name, symbols in GROUPS.items()
    }


def a_register() -> HardwareRegister:
    register = HardwareRegister(a_campus())
    for serial, opening, group in INSTALLED:
        register.add(Cylinder(serial, CylinderFormat.MORTISE, "LC", 6, manufacturer="Sargent"))
        register.install(serial, opening, Position.LOCKSET)
        register.assign_group(serial, group)
    register.add(Cylinder("C-1099", CylinderFormat.MORTISE, "LC", 6))
    return register


def a_coverage() -> CoverageMap:
    return CoverageMap(a_register(), keying_groups())


def a_ledger() -> KeyLedger:
    ledger = KeyLedger(a_directory())
    ledger.issue("41207", "AA1", "kmaki", TODAY)
    ledger.issue("41208", "AA1", "dsalo", TODAY)
    ledger.issue("41209", "AA", "rvirta", TODAY)
    ledger.issue("41210", "A", "phamm", TODAY)
    ledger.issue("41211", "GGM", "phamm", TODAY)
    return ledger

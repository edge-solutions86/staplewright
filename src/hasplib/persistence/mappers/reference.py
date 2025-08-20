"""Charts and keyway families, between the tables and the domain objects.

Both directions are written out by hand. A generic mapper would be shorter
and would also mean that adding a column to a table silently adds a field to
a domain object, which is how a persistence concern ends up in the middle of
the pinning arithmetic.

The keyway family is the only awkward one, because the table is a flat list
of rows with a parent column and the domain object is a tree that refuses to
be built out of order. So the rows get sorted into depth order first and the
tree is grown from the root down.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from hasplib.errors import KeywayError
from hasplib.persistence.models.reference import BlankRow, ChartRow, KeywaySectionRow
from hasplib.spec.chart import DepthAndSpaceChart
from hasplib.spec.keyway import Blank, KeywayFamily, KeywaySection


def chart_to_row(chart: DepthAndSpaceChart) -> ChartRow:
    return ChartRow(
        manufacturer=chart.manufacturer,
        family=chart.family,
        chambers=chart.chambers,
        depths=chart.depths,
        root_mils=chart.root_mils,
        increment_mils=chart.increment_mils,
        first_space_mils=chart.first_space_mils,
        space_increment_mils=chart.space_increment_mils,
        macs=chart.macs,
        gauged_from_tip=chart.gauged_from_tip,
        depth_tolerance_mils=chart.depth_tolerance_mils,
        notes=chart.notes,
    )


def chart_from_row(row: ChartRow) -> DepthAndSpaceChart:
    return DepthAndSpaceChart(
        manufacturer=row.manufacturer,
        family=row.family,
        chambers=row.chambers,
        depths=tuple(row.depths),
        root_mils=row.root_mils,
        increment_mils=row.increment_mils,
        first_space_mils=row.first_space_mils,
        space_increment_mils=row.space_increment_mils,
        macs=row.macs,
        gauged_from_tip=row.gauged_from_tip,
        depth_tolerance_mils=row.depth_tolerance_mils,
        notes=row.notes,
    )


def section_to_row(
    family: KeywayFamily, section: KeywaySection, parent_id: int | None
) -> KeywaySectionRow:
    return KeywaySectionRow(
        family_name=family.name,
        code=section.code,
        manufacturer=section.manufacturer,
        restricted=section.restricted,
        patent_expires=_expiry(section),
        parent_id=parent_id,
    )


def _expiry(section: KeywaySection) -> date | None:
    """The patent date, which the section carries as the catalogue prints it.

    This used to be dropped on the way in, so a family written down and read
    back came out with every patent date gone and every section looking like
    it was restricted forever.
    """
    if section.patent_expires is None:
        return None
    try:
        return date.fromisoformat(section.patent_expires)
    except ValueError as bad:
        raise KeywayError(
            "that section has a patent date nobody can read",
            section=section.code,
            written=section.patent_expires,
        ) from bad


def family_to_rows(family: KeywayFamily) -> list[KeywaySectionRow]:
    """Rows in an order that lets the parent be flushed before the child.

    Ids are not set here, so the caller flushes each row and fills in the
    parent id of the next one. That is deliberate: the alternative is the
    mapper holding a session, and then every test of it needs a database.
    """
    ordered = sorted(family.codes(), key=family.depth_of)
    return [section_to_row(family, family.section(code), None) for code in ordered]


def parents_of(family: KeywayFamily) -> dict[str, str | None]:
    """Each section code against the code above it, in the same order."""
    ordered = sorted(family.codes(), key=family.depth_of)
    found: dict[str, str | None] = {}
    for code in ordered:
        parent = family.parent_of(code)
        found[code] = parent.code if parent is not None else None
    return found


def family_from_rows(name: str, rows: Sequence[KeywaySectionRow]) -> KeywayFamily:
    """Grow the tree from the root down, refusing a set with no root."""
    by_id = {row.id: row for row in rows}
    roots = [row for row in rows if row.parent_id is None]
    if len(roots) != 1:
        raise KeywayError("a family has exactly one root section", family=name, roots=len(roots))
    root = roots[0]
    family = KeywayFamily(name, _to_section(root))
    pending = [row for row in rows if row.parent_id is not None]
    while pending:
        added = False
        for row in list(pending):
            parent = by_id.get(row.parent_id or -1)
            if parent is None:
                raise KeywayError("a section hangs off nothing", code=row.code, family=name)
            if parent.code in family:
                family.add(_to_section(row), under=parent.code)
                pending.remove(row)
                added = True
        if not added:
            raise KeywayError("those sections do not make one tree", family=name)
    return family


def _to_section(row: KeywaySectionRow) -> KeywaySection:
    return KeywaySection(
        code=row.code,
        manufacturer=row.manufacturer,
        restricted=row.restricted,
        patent_expires=row.patent_expires.isoformat() if row.patent_expires else None,
    )


def blank_to_row(blank: Blank, section_id: int) -> BlankRow:
    return BlankRow(
        part_number=blank.part_number,
        section_id=section_id,
        manufacturer=blank.manufacturer,
        chambers=blank.chambers,
        stock=blank.stock,
    )


def blank_from_row(row: BlankRow) -> Blank:
    return Blank(
        part_number=row.part_number,
        section=row.section.code,
        manufacturer=row.manufacturer,
        chambers=row.chambers,
        stock=row.stock,
    )

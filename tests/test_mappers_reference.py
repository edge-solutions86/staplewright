import pytest
from sqlalchemy import select

from charts import five_pin_chart, seven_pin_core_chart, six_pin_chart
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import KeywayError
from hasplib.persistence.mappers.reference import (
    blank_from_row,
    blank_to_row,
    chart_from_row,
    chart_to_row,
    family_from_rows,
    family_to_rows,
)
from hasplib.persistence.models import ChartRow, KeywaySectionRow
from hasplib.spec.keyway import Blank, KeywayFamily, KeywaySection
from systems import campus_family


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def store_family(session, family: KeywayFamily) -> list[KeywaySectionRow]:
    """Write a family out the way a repository would, parents first."""
    ids: dict[str, int] = {}
    rows: list[KeywaySectionRow] = []
    for code in sorted(family.codes(), key=family.depth_of):
        section = family.section(code)
        parent = family.parent_of(code)
        row = KeywaySectionRow(
            family_name=family.name,
            code=section.code,
            manufacturer=section.manufacturer,
            restricted=section.restricted,
            parent_id=ids.get(parent.code) if parent else None,
        )
        session.add(row)
        session.flush()
        ids[code] = row.id
        rows.append(row)
    return rows


@pytest.mark.parametrize("chart", [six_pin_chart(), seven_pin_core_chart(), five_pin_chart()])
def test_a_chart_survives_a_round_trip(chart):
    assert chart_from_row(chart_to_row(chart)) == chart


def test_a_chart_round_trips_through_the_database(session):
    chart = six_pin_chart(notes="from the 2011 catalogue")
    session.add(chart_to_row(chart))
    session.flush()
    row = session.execute(select(ChartRow)).scalar_one()
    assert chart_from_row(row) == chart
    assert chart_from_row(row).notes == "from the 2011 catalogue"


def test_a_chart_with_a_withdrawn_depth_round_trips(session):
    chart = six_pin_chart(depths=(0, 1, 2, 3, 4, 6, 7, 8, 9))
    session.add(chart_to_row(chart))
    session.flush()
    row = session.execute(select(ChartRow)).scalar_one()
    assert chart_from_row(row).depths == (0, 1, 2, 3, 4, 6, 7, 8, 9)


def test_a_family_survives_a_round_trip(session):
    family = campus_family()
    rows = store_family(session, family)
    back = family_from_rows(family.name, rows)
    assert back.codes() == family.codes()
    assert back.ancestry("LC") == family.ancestry("LC")
    assert back.leaves() == family.leaves()


def test_the_restricted_flag_survives(session):
    rows = store_family(session, campus_family())
    back = family_from_rows("Sargent LA", rows)
    assert back.section("LC").restricted
    assert not back.section("LA").restricted


def test_the_rows_come_out_parents_first():
    rows = family_to_rows(campus_family())
    assert rows[0].code == "L"
    assert {row.code for row in rows[1:3]} == {"LA", "LB"}


def test_a_set_with_no_root_is_refused(session):
    rows = store_family(session, campus_family())
    with pytest.raises(KeywayError):
        family_from_rows("Sargent LA", [row for row in rows if row.parent_id is not None])


def test_a_set_with_two_roots_is_refused(session):
    rows = store_family(session, campus_family())
    extra = KeywaySectionRow(family_name="Sargent LA", code="M", manufacturer="Sargent")
    session.add(extra)
    session.flush()
    with pytest.raises(KeywayError):
        family_from_rows("Sargent LA", [*rows, extra])


def test_a_section_hanging_off_nothing_is_refused(session):
    rows = store_family(session, campus_family())
    orphan = rows[-1]
    orphan.parent_id = 9999
    with pytest.raises(KeywayError):
        family_from_rows("Sargent LA", rows)


def test_a_blank_survives_a_round_trip(session):
    rows = store_family(session, campus_family())
    section = next(row for row in rows if row.code == "LC")
    blank = Blank("LC-6", "LC", "Sargent", 6, stock=19)
    row = blank_to_row(blank, section.id)
    session.add(row)
    session.flush()
    session.refresh(row)
    assert blank_from_row(row) == blank


def test_a_blank_with_no_stock_round_trips(session):
    rows = store_family(session, campus_family())
    section = next(row for row in rows if row.code == "LC")
    row = blank_to_row(Blank("LC-7", "LC", "Sargent", 7), section.id)
    session.add(row)
    session.flush()
    session.refresh(row)
    assert blank_from_row(row).stock == 0


def test_a_one_section_family_round_trips(session):
    family = KeywayFamily("Corbin 60", KeywaySection("A", "Corbin"))
    rows = store_family(session, family)
    back = family_from_rows(family.name, rows)
    assert back.codes() == ("A",)
    assert back.leaves() == ("A",)

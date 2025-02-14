from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.persistence.base import table_names
from hasplib.persistence.models import BlankRow, ChartRow, KeywaySectionRow


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_chart(**overrides) -> ChartRow:
    settings = {
        "manufacturer": "Schlage",
        "family": "C keyway, six pin",
        "chambers": 6,
        "depths": tuple(range(10)),
        "root_mils": 335,
        "increment_mils": 15,
        "first_space_mils": 231,
        "space_increment_mils": 156,
        "macs": 7,
    }
    settings.update(overrides)
    return ChartRow(**settings)


def a_family(session) -> KeywaySectionRow:
    root = KeywaySectionRow(family_name="Sargent LA", code="L", manufacturer="Sargent")
    session.add(root)
    session.flush()
    branch = KeywaySectionRow(
        family_name="Sargent LA", code="LA", manufacturer="Sargent", parent=root
    )
    leaf = KeywaySectionRow(
        family_name="Sargent LA",
        code="LC",
        manufacturer="Sargent",
        restricted=True,
        parent=branch,
        patent_expires=date(2031, 4, 1),
    )
    session.add_all([branch, leaf])
    session.flush()
    return root


def test_the_reference_tables_are_on_the_metadata():
    names = table_names()
    assert "charts" in names
    assert "keyway_sections" in names
    assert "key_blanks" in names


def test_a_chart_round_trips_with_its_depth_list(session):
    session.add(a_chart())
    session.flush()
    found = session.execute(select(ChartRow)).scalar_one()
    assert found.depths == tuple(range(10))
    assert found.depth_tolerance_mils == 2
    assert found.notes == ""
    assert "Schlage" in repr(found)


def test_a_chart_with_a_hole_in_its_depths_round_trips(session):
    session.add(a_chart(depths=(0, 1, 2, 3, 4, 6, 7, 8, 9)))
    session.flush()
    found = session.execute(select(ChartRow)).scalar_one()
    assert 5 not in found.depths


def test_one_family_per_manufacturer(session):
    session.add(a_chart())
    session.flush()
    session.add(a_chart())
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_same_family_name_under_another_manufacturer_is_fine(session):
    session.add(a_chart())
    session.add(a_chart(manufacturer="Corbin"))
    session.flush()
    assert len(session.execute(select(ChartRow)).scalars().all()) == 2


@pytest.mark.parametrize("broken", [{"chambers": 0}, {"increment_mils": 0}, {"macs": 0}])
def test_a_chart_that_contradicts_itself_is_refused(session, broken):
    session.add(a_chart(**broken))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_section_tree_reads_downward(session):
    root = a_family(session)
    assert root.is_root
    assert [child.code for child in root.children] == ["LA"]
    assert root.children[0].children[0].code == "LC"


def test_a_section_tree_reads_upward(session):
    a_family(session)
    leaf = session.execute(
        select(KeywaySectionRow).where(KeywaySectionRow.code == "LC")
    ).scalar_one()
    assert leaf.parent.code == "LA"
    assert leaf.parent.parent.code == "L"
    assert not leaf.is_root


def test_a_patent_date_round_trips(session):
    a_family(session)
    leaf = session.execute(
        select(KeywaySectionRow).where(KeywaySectionRow.code == "LC")
    ).scalar_one()
    assert leaf.patent_expires == date(2031, 4, 1)
    assert leaf.restricted


def test_a_code_is_unique_inside_a_family(session):
    a_family(session)
    session.add(KeywaySectionRow(family_name="Sargent LA", code="LA", manufacturer="Sargent"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_same_code_in_another_family_is_fine(session):
    a_family(session)
    session.add(KeywaySectionRow(family_name="Best A2", code="LA", manufacturer="Best"))
    session.flush()
    assert len(session.execute(select(KeywaySectionRow)).scalars().all()) == 4


def test_a_section_cannot_be_its_own_parent(session):
    root = a_family(session)
    root.parent_id = root.id
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_blank_hangs_off_a_section(session):
    root = a_family(session)
    session.add(
        BlankRow(
            part_number="LA-6",
            section_id=root.id,
            manufacturer="Sargent",
            chambers=6,
            stock=40,
        )
    )
    session.flush()
    found = session.execute(select(BlankRow)).scalar_one()
    assert found.section.code == "L"
    assert found.reorder_at == 0
    assert not found.restricted
    assert "LA-6" in repr(found)


def test_a_blank_needs_a_section_that_exists(session):
    session.add(BlankRow(part_number="LA-6", section_id=999, manufacturer="Sargent", chambers=6))
    with pytest.raises(IntegrityError):
        session.flush()


@pytest.mark.parametrize("broken", [{"chambers": 0}, {"stock": -1}, {"reorder_at": -3}])
def test_a_blank_that_contradicts_itself_is_refused(session, broken):
    root = a_family(session)
    settings = {
        "part_number": "LA-6",
        "section_id": root.id,
        "manufacturer": "Sargent",
        "chambers": 6,
    }
    settings.update(broken)
    session.add(BlankRow(**settings))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_section_lists_the_blanks_cut_on_it(session):
    root = a_family(session)
    session.add_all(
        [
            BlankRow(part_number="LA-6", section_id=root.id, manufacturer="Sargent", chambers=6),
            BlankRow(part_number="LA-7", section_id=root.id, manufacturer="Sargent", chambers=7),
        ]
    )
    session.flush()
    session.refresh(root)
    assert sorted(blank.part_number for blank in root.blanks) == ["LA-6", "LA-7"]

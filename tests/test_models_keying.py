import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.base import table_names
from hasplib.persistence.models import (
    ChartRow,
    GroupMemberRow,
    GroupRow,
    ScheduleEntryRow,
    SystemRow,
)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_system(session, name: str = "chester park") -> SystemRow:
    chart = ChartRow(
        manufacturer="Sargent",
        family=f"LA six pin {name}",
        chambers=6,
        depths=tuple(range(10)),
        root_mils=335,
        increment_mils=15,
        first_space_mils=231,
        space_increment_mils=156,
        macs=7,
    )
    session.add(chart)
    session.flush()
    system = SystemRow(name=name, chart_id=chart.id, family_name="Sargent LA", top_section="L")
    session.add(system)
    session.flush()
    return system


def test_the_keying_tables_are_on_the_metadata():
    names = table_names()
    assert "keying_systems" in names
    assert "schedule_entries" in names
    assert "keying_groups" in names
    assert "keying_group_members" in names


def test_a_system_carries_its_chart(session):
    system = a_system(session)
    assert system.chart.chambers == 6
    assert system.progression_step == 2
    assert not system.archived
    assert "chester park" in repr(system)


def test_a_system_name_is_unique(session):
    a_system(session)
    session.add(
        SystemRow(name="chester park", chart_id=1, family_name="Sargent LA", top_section="L")
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_progression_step_of_nothing_is_refused(session):
    system = a_system(session)
    system.progression_step = 0
    with pytest.raises(IntegrityError):
        session.flush()


def test_an_entry_round_trips_its_symbol_and_cut(session):
    system = a_system(session)
    session.add(
        ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LC", bitting=b("342156"))
    )
    session.flush()
    found = session.execute(select(ScheduleEntryRow)).scalar_one()
    assert found.symbol == sym("AA1")
    assert found.bitting == b("342156")
    assert found.is_cut
    assert "AA1" in repr(found)


def test_an_uncut_entry_is_allowed(session):
    system = a_system(session)
    session.add(ScheduleEntryRow(system_id=system.id, symbol=sym("AA2"), section="LC"))
    session.flush()
    found = session.execute(select(ScheduleEntryRow)).scalar_one()
    assert found.bitting is None
    assert not found.is_cut
    assert "open" in repr(found)


def test_a_symbol_appears_once_in_a_system(session):
    system = a_system(session)
    session.add(ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LC"))
    session.flush()
    session.add(ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LD"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_the_same_symbol_in_another_system_is_fine(session):
    first = a_system(session)
    second = a_system(session, "voss hall")
    session.add(ScheduleEntryRow(system_id=first.id, symbol=sym("AA1"), section="LC"))
    session.add(ScheduleEntryRow(system_id=second.id, symbol=sym("AA1"), section="LC"))
    session.flush()
    assert len(session.execute(select(ScheduleEntryRow)).scalars().all()) == 2


def test_a_cut_appears_once_in_a_system(session):
    system = a_system(session)
    session.add(
        ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LC", bitting=b("342156"))
    )
    session.flush()
    session.add(
        ScheduleEntryRow(system_id=system.id, symbol=sym("AA2"), section="LC", bitting=b("342156"))
    )
    with pytest.raises(IntegrityError):
        session.flush()


def test_any_number_of_symbols_can_be_uncut(session):
    system = a_system(session)
    for number in range(1, 5):
        session.add(ScheduleEntryRow(system_id=system.id, symbol=sym(f"AA{number}"), section="LC"))
    session.flush()
    assert len(session.execute(select(ScheduleEntryRow)).scalars().all()) == 4


def test_a_system_lists_its_own_entries(session):
    system = a_system(session)
    session.add(ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LC"))
    session.flush()
    session.refresh(system)
    assert [entry.symbol.render() for entry in system.entries] == ["AA1"]


def test_deleting_a_system_takes_its_entries_with_it(session):
    system = a_system(session)
    session.add(ScheduleEntryRow(system_id=system.id, symbol=sym("AA1"), section="LC"))
    session.flush()
    session.delete(system)
    session.flush()
    assert session.execute(select(ScheduleEntryRow)).scalars().all() == []


def test_a_group_holds_its_symbols(session):
    system = a_system(session)
    group = GroupRow(system_id=system.id, name="moore offices")
    session.add(group)
    session.flush()
    session.add_all(
        [
            GroupMemberRow(group_id=group.id, symbol=sym("AA1")),
            GroupMemberRow(group_id=group.id, symbol=sym("AA")),
        ]
    )
    session.flush()
    session.refresh(group)
    assert sorted(symbol.render() for symbol in group.symbols) == ["AA", "AA1"]
    assert "moore offices" in repr(group)


def test_a_group_name_is_unique_in_a_system(session):
    system = a_system(session)
    session.add(GroupRow(system_id=system.id, name="moore offices"))
    session.flush()
    session.add(GroupRow(system_id=system.id, name="moore offices"))
    with pytest.raises(IntegrityError):
        session.flush()


def test_a_symbol_is_in_a_group_once(session):
    system = a_system(session)
    group = GroupRow(system_id=system.id, name="moore offices")
    session.add(group)
    session.flush()
    session.add(GroupMemberRow(group_id=group.id, symbol=sym("AA1")))
    session.flush()
    session.add(GroupMemberRow(group_id=group.id, symbol=sym("AA1")))
    with pytest.raises(IntegrityError):
        session.flush()


def test_deleting_a_group_takes_its_members(session):
    system = a_system(session)
    group = GroupRow(system_id=system.id, name="moore offices")
    session.add(group)
    session.flush()
    session.add(GroupMemberRow(group_id=group.id, symbol=sym("AA1")))
    session.flush()
    session.delete(group)
    session.flush()
    assert session.execute(select(GroupMemberRow)).scalars().all() == []


def test_a_group_needs_a_system_that_exists(session):
    session.add(GroupRow(system_id=999, name="nowhere"))
    with pytest.raises(IntegrityError):
        session.flush()

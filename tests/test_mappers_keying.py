import pytest
from sqlalchemy import select

from charts import six_pin_chart
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.keying import (
    entry_from_row,
    entry_to_row,
    group_from_row,
    group_to_rows,
    groups_from_rows,
    schedule_from_rows,
    schedule_to_rows,
    symbols_in,
)
from hasplib.persistence.models import ChartRow, GroupRow, ScheduleEntryRow, SystemRow
from hasplib.progression.groups import KeyingGroup
from hasplib.spec.rules import CuttingRules, RuleSet
from systems import campus_family, small_schedule

CUTS = {
    "GGM": "442446",
    "A": "642446",
    "AA": "622446",
    "AA1": "620446",
    "AA2": "624446",
}


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def a_cut_schedule():
    schedule = small_schedule()
    for symbol, bitting in CUTS.items():
        schedule.assign(sym(symbol), Bitting.parse(bitting))
    return schedule


def a_system(session) -> SystemRow:
    chart = ChartRow(
        manufacturer="Sargent",
        family="LA six pin",
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
    system = SystemRow(
        name="chester park", chart_id=chart.id, family_name="Sargent LA", top_section="L"
    )
    session.add(system)
    session.flush()
    return system


def a_ruleset() -> RuleSet:
    return RuleSet(six_pin_chart(), CuttingRules())


def test_one_entry_survives_a_round_trip():
    schedule = a_cut_schedule()
    entry = schedule.entry(sym("AA1"))
    assert entry_from_row(entry_to_row(entry, 1)) == entry


def test_an_uncut_entry_survives_a_round_trip():
    schedule = small_schedule()
    entry = schedule.entry(sym("AB1"))
    assert entry_from_row(entry_to_row(entry, 1)).bitting is None


def test_a_whole_schedule_survives_the_database(session):
    system = a_system(session)
    schedule = a_cut_schedule()
    session.add_all(schedule_to_rows(schedule, system.id))
    session.flush()
    rows = session.execute(select(ScheduleEntryRow)).scalars().all()
    back = schedule_from_rows("chester park", a_ruleset(), campus_family(), "L", rows)
    assert back.symbols() == schedule.symbols()
    assert back.bitting_of(sym("AA1")) == Bitting.parse("620446")


def test_the_sections_survive(session):
    system = a_system(session)
    session.add_all(schedule_to_rows(a_cut_schedule(), system.id))
    session.flush()
    rows = session.execute(select(ScheduleEntryRow)).scalars().all()
    back = schedule_from_rows("chester park", a_ruleset(), campus_family(), "L", rows)
    assert back.entry(sym("AA1")).section == "LC"
    assert back.entry(sym("AB1")).section == "LD"


def test_a_loaded_schedule_keeps_lines_that_policy_would_now_refuse(session):
    system = a_system(session)
    schedule = a_cut_schedule()
    session.add_all(schedule_to_rows(schedule, system.id))
    session.flush()
    rows = session.execute(select(ScheduleEntryRow)).scalars().all()
    strict = RuleSet(six_pin_chart(), CuttingRules(shallow_tip_limit=2))
    back = schedule_from_rows("chester park", strict, campus_family(), "L", rows)
    assert len(back) == len(schedule)
    assert back.problems()


def test_the_rows_come_out_shallow_first():
    rows = schedule_to_rows(a_cut_schedule(), 1)
    levels = [row.symbol.level for row in rows]
    assert levels == sorted(levels)


def test_the_symbols_can_be_read_without_building_a_schedule():
    rows = schedule_to_rows(a_cut_schedule(), 1)
    assert [s.render() for s in symbols_in(rows)][:3] == ["GGM", "A", "B"]


def test_a_group_survives_a_round_trip(session):
    system = a_system(session)
    group = KeyingGroup("moore offices", (sym("AA1"), sym("AA"), sym("GGM")))
    row, members = group_to_rows(group, system.id)
    session.add(row)
    session.flush()
    for member in members:
        member.group_id = row.id
        session.add(member)
    session.flush()
    session.refresh(row)
    assert group_from_row(row).ordered() == group.ordered()


def test_the_members_come_out_in_schedule_order(session):
    system = a_system(session)
    group = KeyingGroup("moore offices", (sym("AA1"), sym("GGM"), sym("AA")))
    row, members = group_to_rows(group, system.id)
    session.add(row)
    session.flush()
    for member in members:
        member.group_id = row.id
        session.add(member)
    session.flush()
    session.refresh(row)
    back = group_from_row(row)
    assert [s.render() for s in back.symbols] == ["GGM", "AA", "AA1"]


def test_several_groups_come_back_keyed_by_name(session):
    system = a_system(session)
    for name, symbols in [("offices", ("AA1",)), ("storeroom", ("AA2", "AA"))]:
        group = KeyingGroup(name, tuple(sym(text) for text in symbols))
        row, members = group_to_rows(group, system.id)
        session.add(row)
        session.flush()
        for member in members:
            member.group_id = row.id
            session.add(member)
    session.flush()
    rows = session.execute(select(GroupRow)).scalars().all()
    found = groups_from_rows(rows)
    assert sorted(found) == ["offices", "storeroom"]
    assert found["storeroom"].size == 2

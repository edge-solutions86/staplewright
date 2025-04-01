from datetime import date

import pytest

from charts import six_pin_chart
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, HierarchyError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.reference import chart_to_row
from hasplib.persistence.models import KeywaySectionRow, RetiredBittingRow
from hasplib.services.systems import SystemService
from systems import campus_family

WHEN = date(2025, 4, 1)


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


def a_service(session) -> SystemService:
    chart = chart_to_row(six_pin_chart())
    session.add(chart)
    session.flush()
    family = campus_family()
    ids: dict[str, int] = {}
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
    service = SystemService(session)
    service.open_system("chester park", chart.id, "Sargent LA", "L", opened_on=WHEN)
    return service


def a_drawn_system(session) -> SystemService:
    service = a_service(session)
    service.draw("chester park", sym("A"), "LA")
    service.draw("chester park", sym("AA"), "LC")
    service.draw("chester park", sym("AA1"))
    service.draw("chester park", sym("AA2"))
    return service


def test_a_new_system_holds_only_the_top_key(session):
    service = a_service(session)
    schedule = service.schedule("chester park")
    assert len(schedule) == 1
    assert schedule.entry(sym("GGM")).section == "L"


def test_a_system_cannot_be_opened_twice(session):
    service = a_service(session)
    system = service.system("chester park")
    with pytest.raises(ConflictError):
        service.open_system("chester park", system.chart_id, "Sargent LA", "L")


def test_asking_for_a_system_that_is_not_there(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.system("nothing at all")


def test_drawing_a_symbol_puts_it_on_the_schedule(session):
    service = a_drawn_system(session)
    schedule = service.schedule("chester park")
    assert [s.render() for s in schedule.symbols()] == ["GGM", "A", "AA", "AA1", "AA2"]
    assert schedule.entry(sym("AA1")).section == "LC"


def test_a_symbol_with_no_parent_is_refused(session):
    service = a_service(session)
    with pytest.raises(HierarchyError):
        service.draw("chester park", sym("AA1"))


def test_a_section_the_key_above_cannot_enter_is_refused(session):
    service = a_service(session)
    service.draw("chester park", sym("A"), "LA")
    with pytest.raises(HierarchyError):
        service.draw("chester park", sym("AA"), "LE")


def test_cutting_a_symbol_writes_the_cut(session):
    service = a_drawn_system(session)
    service.cut("chester park", sym("AA1"), b("620446"))
    assert service.schedule("chester park").bitting_of(sym("AA1")) == b("620446")


def test_a_cut_the_rules_refuse_never_lands(session):
    service = a_drawn_system(session)
    with pytest.raises(ConflictError):
        service.cut("chester park", sym("AA1"), b("192156"))
    assert service.schedule("chester park").bitting_of(sym("AA1")) is None


def test_a_cut_another_symbol_already_has_is_refused(session):
    service = a_drawn_system(session)
    service.cut("chester park", sym("AA1"), b("620446"))
    with pytest.raises(ConflictError):
        service.cut("chester park", sym("AA2"), b("620446"))


def test_a_quarantined_cut_is_refused_however_good_it_looks(session):
    service = a_drawn_system(session)
    session.add(RetiredBittingRow(bitting=b("620446"), reason="compromised", retired_on=WHEN))
    session.flush()
    with pytest.raises(ConflictError) as caught:
        service.cut("chester park", sym("AA1"), b("620446"))
    assert caught.value.facts["reason"] == "compromised"


def test_a_cut_retired_for_a_softer_reason_is_still_refused(session):
    service = a_drawn_system(session)
    session.add(RetiredBittingRow(bitting=b("620446"), reason="superseded", retired_on=WHEN))
    session.flush()
    with pytest.raises(ConflictError):
        service.cut("chester park", sym("AA1"), b("620446"))


def test_uncutting_puts_the_symbol_back_on_the_open_list(session):
    service = a_drawn_system(session)
    service.cut("chester park", sym("AA1"), b("620446"))
    service.uncut("chester park", sym("AA1"))
    assert service.schedule("chester park").bitting_of(sym("AA1")) is None


def test_cutting_a_symbol_that_was_never_drawn(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.cut("chester park", sym("AA1"), b("620446"))


def test_the_review_finds_nothing_wrong_with_a_clean_system(session):
    service = a_drawn_system(session)
    service.cut("chester park", sym("A"), b("642446"))
    service.cut("chester park", sym("AA"), b("622446"))
    service.cut("chester park", sym("AA1"), b("620446"))
    service.cut("chester park", sym("AA2"), b("624446"))
    service.cut("chester park", sym("GGM"), b("442446"))
    assert service.review("chester park") == ()


def test_the_review_reports_the_uncut_symbols(session):
    service = a_drawn_system(session)
    codes = {finding.code for finding in service.review("chester park")}
    assert codes == {"uncut"}


def test_the_family_comes_back_as_a_tree(session):
    service = a_service(session)
    family = service.family("Sargent LA")
    assert family.enters("L", "LC")
    assert family.leaves() == ("LC", "LD", "LE")


def test_asking_for_a_family_nobody_stored(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.family("Best A2")


def test_a_system_can_be_archived_once(session):
    service = a_service(session)
    assert service.archive("chester park").archived
    with pytest.raises(ConflictError):
        service.archive("chester park")


def test_a_system_with_no_groups_reports_none(session):
    assert a_service(session).groups("chester park") == {}

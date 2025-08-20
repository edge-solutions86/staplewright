from datetime import date

import pytest

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.models.keying import SystemRow
from hasplib.persistence.models.reference import ChartRow
from hasplib.services.keyways import KeywayService, SectionRepository
from hasplib.spec.keyway import KeywayFamily, KeywaySection

TODAY = date(2025, 6, 30)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    yield made
    made.close()
    teardown(engine)


def a_family(name: str = "everest 29") -> KeywayFamily:
    family = KeywayFamily(
        name,
        KeywaySection("C", "Schlage", restricted=True, patent_expires="2031-04-01"),
    )
    family.add(KeywaySection("CE", "Schlage", restricted=True, patent_expires="2027-02-14"), "C")
    family.add(KeywaySection("CF", "Schlage", restricted=True, patent_expires="2023-11-30"), "C")
    family.add(KeywaySection("CEA", "Schlage"), "CE")
    return family


def a_chart(session) -> ChartRow:
    held = session.query(ChartRow).first()
    if held is not None:
        return held
    row = ChartRow(
        manufacturer="Schlage",
        family="C keyway six pin",
        chambers=6,
        depths=tuple(range(10)),
        root_mils=335,
        increment_mils=15,
        first_space_mils=231,
        space_increment_mils=156,
        macs=7,
    )
    session.add(row)
    session.flush()
    return row


def a_system(session, name: str, section: str, family: str = "everest 29") -> SystemRow:
    row = SystemRow(
        name=name,
        chart_id=a_chart(session).id,
        family_name=family,
        top_section=section,
    )
    session.add(row)
    session.flush()
    return row


def stored(session, name: str = "everest 29") -> KeywayService:
    service = KeywayService(session)
    service.store(a_family(name))
    return service


def test_a_family_goes_in_and_comes_back_as_a_tree(session):
    service = stored(session)
    family = service.family("everest 29")
    assert set(family.codes()) == {"C", "CE", "CF", "CEA"}
    assert family.parent_of("CEA").code == "CE"


def test_the_patent_dates_survive_the_round_trip(session):
    family = stored(session).family("everest 29")
    assert family.section("CE").patent_expires == "2027-02-14"


def test_a_section_with_no_date_comes_back_without_one(session):
    family = stored(session).family("everest 29")
    assert family.section("CEA").patent_expires is None


def test_storing_a_family_twice_is_refused(session):
    service = stored(session)
    with pytest.raises(ConflictError):
        service.store(a_family())


def test_a_family_nobody_stored_is_a_404(session):
    with pytest.raises(NotFoundError):
        KeywayService(session).family("nothing")


def test_the_families_come_back_in_name_order(session):
    service = stored(session)
    service.store(a_family("primus"))
    assert service.families() == ("everest 29", "primus")


def test_the_exposures_come_back_soonest_first(session):
    found = stored(session).exposures("everest 29")
    assert [item.section for item in found][:3] == ["CF", "CE", "C"]


def test_the_open_sections_are_the_expired_and_the_unrestricted(session):
    assert stored(session).open_today("everest 29", TODAY) == ("CF", "CEA")


def test_the_closing_sections_come_off_the_horizon(session):
    service = stored(session)
    assert service.closing("everest 29", TODAY) == ("CE",)
    assert service.closing("everest 29", TODAY, within_years=10) == ("CE", "C")


def test_the_next_one_to_go_is_named(session):
    assert stored(session).next_to_go("everest 29", TODAY).section == "CE"


def test_the_family_report_reads_top_to_bottom(session):
    lines = stored(session).report("everest 29", TODAY)
    assert lines[0].startswith("everest 29 patent position")


def test_the_systems_on_a_section_come_back_in_name_order(session):
    service = stored(session)
    a_system(session, "voss hall", "CF")
    a_system(session, "atrium", "CF")
    assert service.systems_on("everest 29", "CF") == ("atrium", "voss hall")


def test_an_archived_system_is_not_on_anything(session):
    service = stored(session)
    row = a_system(session, "voss hall", "CF")
    row.archived = True
    session.flush()
    assert service.systems_on("everest 29", "CF") == ()


def test_the_sections_in_use_are_the_ones_something_sits_on(session):
    service = stored(session)
    a_system(session, "voss hall", "CF")
    a_system(session, "atrium", "C")
    assert service.sections_in_use("everest 29") == ("C", "CF")


def test_a_section_that_went_open_with_nothing_on_it_is_not_at_risk(session):
    assert stored(session).at_risk("everest 29", TODAY) == ()


def test_a_section_that_went_open_with_a_system_on_it_is_at_risk(session):
    service = stored(session)
    a_system(session, "voss hall", "CF")
    found = service.at_risk("everest 29", TODAY)
    assert [line.section for line in found] == ["CF"]
    assert found[0].systems == ("voss hall",)


def test_a_risk_line_says_when_the_patent_went(session):
    service = stored(session)
    a_system(session, "voss hall", "CF")
    line = service.at_risk("everest 29", TODAY)[0]
    assert line.describe(TODAY) == "CF patent ran out 2023-11-30 carrying voss hall"


def test_a_section_with_years_left_passes_the_check(session):
    stored(session).check_section("everest 29", "C", TODAY)


def test_a_section_that_goes_inside_the_horizon_is_refused(session):
    service = stored(session)
    with pytest.raises(ConflictError) as caught:
        service.check_section("everest 29", "CE", TODAY)
    assert caught.value.facts["expires"] == "2027-02-14"


def test_somebody_who_knows_can_key_on_it_anyway(session):
    stored(session).check_section("everest 29", "CE", TODAY, accepted=True)


def test_a_section_nobody_has_is_still_a_missing_section(session):
    service = stored(session)
    from hasplib.errors import KeywayError

    with pytest.raises(KeywayError):
        service.check_section("everest 29", "ZZ", TODAY)


def test_the_shop_wide_report_says_when_nothing_is_wrong(session):
    lines = stored(session).risk_report(TODAY)
    assert lines[1] == "nothing the shop keys on has gone open"


def test_the_shop_wide_report_counts_across_families(session):
    service = stored(session)
    service.store(a_family("primus"))
    a_system(session, "voss hall", "CF")
    a_system(session, "shops", "CF", family="primus")
    lines = service.risk_report(TODAY)
    assert lines[1] == "2 sections in use have gone open"


def test_the_section_repository_lists_a_family_in_code_order(session):
    stored(session)
    found = SectionRepository(session).in_family("everest 29")
    assert [row.code for row in found] == ["C", "CE", "CEA", "CF"]

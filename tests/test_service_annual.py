from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

import dbcampus
from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.models.annual import AnnualReturnRow
from hasplib.reports.annual import Return
from hasplib.services.annual import (
    AnnualService,
    ReturnRepository,
    to_return,
    to_row,
)

TODAY = date(2025, 6, 30)
LAST_YEAR = date(2024, 6, 30)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    dbcampus.build(made)
    yield made
    made.close()
    teardown(engine)


def a_service(session) -> AnnualService:
    return AnnualService(session)


def filed(session, year: int, **overrides) -> AnnualReturnRow:
    settings = {
        "year": year,
        "measured_on": date(year, 6, 30),
        "keys_out": 4,
        "masters_out": 2,
        "unaccounted": 0,
        "keyholders": 3,
    }
    settings.update(overrides)
    row = AnnualReturnRow(**settings)
    session.add(row)
    session.flush()
    return row


def test_a_row_and_a_return_convert_both_ways():
    found = Return(2025, 5, 3, 0, 4)
    assert to_return(to_row(found, TODAY)) == found


def test_a_row_carries_who_filed_it():
    row = to_row(Return(2025, 5, 3, 0, 4), TODAY, "rvirta", "after the audit")
    assert row.filed_by == "rvirta"
    assert row.note == "after the audit"


def test_a_row_averages_its_own_keys():
    assert to_row(Return(2025, 6, 3, 0, 3), TODAY).keys_per_holder == 2.0


def test_a_row_with_nobody_holding_anything_averages_nothing():
    assert to_row(Return(2025, 0, 0, 0, 0), TODAY).keys_per_holder == 0.0


def test_measuring_does_not_write_anything(session):
    service = a_service(session)
    service.measure(TODAY)
    assert service.returns.all() == []


def test_the_measurement_counts_what_is_out(session):
    found = a_service(session).measure(TODAY)
    assert found.keys_out == 5
    assert found.masters_out == 3


def test_filing_writes_the_numbers_down(session):
    service = a_service(session)
    row = service.file_return(TODAY, "rvirta")
    assert row.year == 2025
    assert row.keys_out == 5
    assert row.filed_by == "rvirta"


def test_a_year_cannot_be_filed_twice(session):
    service = a_service(session)
    service.file_return(TODAY)
    with pytest.raises(ConflictError) as caught:
        service.file_return(TODAY)
    assert caught.value.facts["year"] == 2025


def test_a_year_nobody_filed_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).filed(2019)


def test_a_filed_year_comes_back(session):
    service = a_service(session)
    service.file_return(TODAY)
    assert service.filed(2025).keys_out == 5


def test_withdrawing_needs_a_reason(session):
    service = a_service(session)
    service.file_return(TODAY)
    with pytest.raises(ConflictError):
        service.withdraw(2025, "")


def test_a_withdrawn_return_keeps_its_row(session):
    service = a_service(session)
    service.file_return(TODAY)
    row = service.withdraw(2025, "counted the shops twice")
    assert row.note.startswith("withdrawn:")
    assert service.filed(2025) is row


def test_the_first_year_has_nothing_before_it(session):
    assert a_service(session).previous(2025) is None


def test_the_previous_year_is_the_one_before(session):
    filed(session, 2023)
    filed(session, 2024)
    found = a_service(session).previous(2025)
    assert found.year == 2024


def test_a_later_year_is_not_the_previous_one(session):
    filed(session, 2026)
    assert a_service(session).previous(2025) is None


def test_the_history_comes_back_oldest_first(session):
    filed(session, 2024)
    filed(session, 2023)
    assert [item.year for item in a_service(session).history()] == [2023, 2024]


def test_the_sheet_reads_top_to_bottom(session):
    lines = a_service(session).sheet(TODAY)
    assert lines[0] == "key control return for 2025"
    assert any(line.startswith("3 master keys are out") for line in lines)


def test_the_sheet_compares_against_a_filed_year(session):
    filed(session, 2024, keys_out=4)
    lines = a_service(session).sheet(TODAY)
    assert any(line == "against 2024" for line in lines)


def test_the_masters_table_comes_off_the_service(session):
    lines = a_service(session).masters()
    assert lines[0] == "3 master keys are out"


def test_the_unaccounted_table_comes_off_the_service(session):
    assert a_service(session).unaccounted(TODAY)[0].startswith("every key out")


def test_the_concentration_comes_off_the_service(session):
    assert a_service(session).concentration()[0] == ("phamm", 2)


def test_the_trend_says_when_nothing_is_filed(session):
    assert a_service(session).trend() == ("no returns have been filed",)


def test_the_trend_has_a_row_per_filed_year(session):
    filed(session, 2023)
    filed(session, 2024)
    assert len(a_service(session).trend()) == 3


def test_the_movement_is_available_without_filing(session):
    filed(session, 2024, keys_out=4)
    lines = a_service(session).against_last_year(TODAY)
    assert "up 1" in lines[1]


def test_the_repository_lists_from_a_year(session):
    filed(session, 2022)
    filed(session, 2024)
    found = ReturnRepository(session).since(2023)
    assert [row.year for row in found] == [2024]


def test_the_latest_filed_year_comes_back(session):
    filed(session, 2022)
    filed(session, 2024)
    assert ReturnRepository(session).latest().year == 2024


def test_no_returns_means_no_latest(session):
    assert ReturnRepository(session).latest() is None


def test_a_year_before_anybody_was_alive_is_refused(session):
    with pytest.raises(IntegrityError):
        filed(session, 1066)


def test_more_masters_than_keys_is_refused(session):
    with pytest.raises(IntegrityError):
        filed(session, 2025, keys_out=2, masters_out=3)


def test_more_unaccounted_than_keys_is_refused(session):
    with pytest.raises(IntegrityError):
        filed(session, 2025, keys_out=2, unaccounted=3)


def test_a_negative_count_is_refused(session):
    with pytest.raises(IntegrityError):
        filed(session, 2025, keyholders=-1)


def test_one_year_cannot_be_filed_twice_in_the_table(session):
    filed(session, 2025)
    with pytest.raises(IntegrityError):
        filed(session, 2025)

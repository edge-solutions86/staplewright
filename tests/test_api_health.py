import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

import dbcampus
from dbfixture import fresh_engine, sessions_for
from hasplib import __version__
from hasplib.api.app import build_app
from hasplib.api.health import check_by_name, check_names
from hasplib.errors import NotFoundError
from hasplib.persistence.models.campus import CylinderRow

TODAY = "2025-06-30"


@pytest.fixture()
def client():
    engine = fresh_engine()
    session = sessions_for(engine)()
    dbcampus.build(session)
    session.commit()
    session.close()
    with TestClient(build_app(engine)) as made:
        yield made
    engine.dispose()


def unmap_a_cylinder(client):
    with client.app.state.shop.sessions() as session:
        row = session.execute(
            select(CylinderRow).where(CylinderRow.serial == "C-1001")
        ).scalar_one()
        row.group_id = None
        session.commit()


def test_the_liveness_probe_says_up(client):
    body = client.get("/health").json()
    assert body == {"status": "up", "version": __version__}


def test_the_check_names_are_listed(client):
    found = client.get("/health/checks").json()["checks"]
    assert "unkeyed_openings" in found
    assert found == sorted(found)


def test_a_check_can_be_looked_up_by_name():
    assert check_by_name("unkeyed_openings").__name__ == "unkeyed_openings"


def test_a_check_nobody_wrote_is_a_404():
    with pytest.raises(NotFoundError):
        check_by_name("payroll_reconciliation")


def test_the_names_match_the_checks():
    assert len(check_names()) == len(set(check_names()))


def test_the_report_comes_back_as_lines(client):
    body = client.get("/health/report", params={"on": TODAY}).json()
    assert body["lines"][0] == "self check at 2025-06-30"


def test_the_report_comes_back_as_paper_when_asked(client):
    found = client.get("/health/report", params={"on": TODAY, "plain": True})
    assert found.headers["content-type"].startswith("text/plain")


def test_the_report_defaults_to_today(client):
    assert client.get("/health/report").status_code == 200


def test_the_findings_carry_the_counts(client):
    body = client.get("/health/findings", params={"on": TODAY}).json()
    assert set(body["counts"]) == {"note", "warning", "fault"}
    assert body["on"] == TODAY


def test_a_clean_campus_has_no_worst(client):
    body = client.get("/health/findings", params={"on": TODAY}).json()
    assert body["exit_code"] == 0


def test_a_fault_shows_up_in_the_findings(client):
    unmap_a_cylinder(client)
    body = client.get("/health/findings", params={"on": TODAY}).json()
    assert body["worst"] == "fault"
    assert body["exit_code"] == 1
    assert any(item["subject"] == "C-1001" for item in body["findings"])


def test_the_findings_can_be_narrowed_to_one_severity(client):
    unmap_a_cylinder(client)
    body = client.get("/health/findings", params={"on": TODAY, "severity": "fault"}).json()
    assert all(item["severity"] == "fault" for item in body["findings"])


def test_a_severity_nobody_uses_is_a_422(client):
    found = client.get("/health/findings", params={"on": TODAY, "severity": "urgent"})
    assert found.status_code == 422


def test_one_check_can_be_run_on_its_own(client):
    unmap_a_cylinder(client)
    body = client.get("/health/findings/unkeyed_openings", params={"on": TODAY}).json()
    assert body["check"] == "unkeyed_openings"
    assert [item["subject"] for item in body["findings"]] == ["C-1001"]


def test_one_check_that_finds_nothing_comes_back_empty(client):
    body = client.get("/health/findings/uninstalled_cylinders", params={"on": TODAY}).json()
    assert body["findings"] == []


def test_a_check_nobody_wrote_is_a_404_over_http(client):
    assert client.get("/health/findings/payroll").status_code == 404


def test_the_report_is_always_a_two_hundred_even_with_faults(client):
    unmap_a_cylinder(client)
    assert client.get("/health/report", params={"on": TODAY}).status_code == 200

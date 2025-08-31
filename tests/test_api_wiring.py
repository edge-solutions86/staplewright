import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from dbfixture import fresh_engine
from hasplib.api.app import Shop
from hasplib.api.wiring import PLAIN, page, service_dependency, session_dependency


class Counter:
    """A stand in service, so the wiring can be tested without a domain."""

    seen: list[int] = []

    def __init__(self, session: Session, label: str = "plain") -> None:
        self.session = session
        self.label = label
        Counter.seen.append(id(session))


@pytest.fixture()
def shop():
    engine = fresh_engine()
    made = Shop(engine)
    yield made
    engine.dispose()


@pytest.fixture()
def client(shop):
    Counter.seen = []
    app = FastAPI()

    opener = session_dependency(shop)
    service = service_dependency(Counter, shop)
    labelled = service_dependency(Counter, shop, "wide")

    @app.get("/session")
    def one(db: Session = Depends(opener)) -> dict[str, bool]:
        return {"live": db.is_active}

    @app.get("/service")
    def two(work: Counter = Depends(service)) -> dict[str, str]:
        return {"label": work.label}

    @app.get("/labelled")
    def three(work: Counter = Depends(labelled)) -> dict[str, str]:
        return {"label": work.label}

    @app.get("/both")
    def four(
        first: Counter = Depends(service), second: Counter = Depends(labelled)
    ) -> dict[str, bool]:
        return {"same": first.session is second.session}

    @app.get("/page")
    def five(plain: bool = PLAIN):
        return page(("first line", "second line"), plain)

    with TestClient(app) as made:
        yield made


def test_the_session_dependency_hands_over_a_live_session(client):
    assert client.get("/session").json() == {"live": True}


def test_a_service_is_built_with_its_defaults(client):
    assert client.get("/service").json() == {"label": "plain"}


def test_extra_arguments_reach_the_service(client):
    assert client.get("/labelled").json() == {"label": "wide"}


def test_two_services_in_one_request_share_a_session(client):
    assert client.get("/both").json() == {"same": True}


def test_two_requests_do_not_share_a_session(client):
    client.get("/service")
    client.get("/service")
    assert len(set(Counter.seen)) == 2


def test_a_report_comes_back_as_lines_by_default(client):
    assert client.get("/page").json() == {"lines": ["first line", "second line"]}


def test_a_report_comes_back_as_text_when_asked(client):
    found = client.get("/page", params={"plain": True})
    assert found.headers["content-type"].startswith("text/plain")
    assert found.text.splitlines() == ["first line", "second line"]


def test_the_plain_flag_is_a_query_and_never_a_path(client):
    assert client.get("/page/true").status_code == 404

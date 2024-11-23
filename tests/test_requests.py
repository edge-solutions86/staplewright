from datetime import date

import pytest

from hasplib.control.requests import KeyRequest, RequestBook, RequestState
from hasplib.errors import ConflictError, InputError, NotFoundError
from people import TODAY, a_directory


def a_request(number: str = "R-001", **overrides) -> KeyRequest:
    settings = {
        "number": number,
        "keyholder": "kmaki",
        "symbol": "AA1",
        "building": "MOO",
        "reason": "moved into 214",
        "raised_on": TODAY,
    }
    settings.update(overrides)
    return KeyRequest(**settings)


def a_book() -> RequestBook:
    return RequestBook(a_directory())


def submitted(book: RequestBook, request: KeyRequest) -> KeyRequest:
    book.raise_request(request)
    return book.submit(request.number)


def test_a_request_needs_a_number_and_a_reason():
    with pytest.raises(InputError):
        a_request("  ")
    with pytest.raises(InputError):
        a_request(reason="  ")


def test_a_new_request_starts_raised_and_open():
    book = a_book()
    request = book.raise_request(a_request())
    assert request.state is RequestState.RAISED
    assert request.is_open
    assert not request.is_decided


def test_a_request_describes_itself_for_the_counter():
    assert a_request().describe() == "R-001 kmaki wants AA1 in MOO"


def test_a_request_for_somebody_who_is_not_in_the_directory():
    book = a_book()
    with pytest.raises(NotFoundError):
        book.raise_request(a_request(keyholder="ghost"))


def test_a_request_number_cannot_be_reused():
    book = a_book()
    book.raise_request(a_request())
    with pytest.raises(ConflictError):
        book.raise_request(a_request())


def test_a_request_cannot_be_filed_already_approved():
    book = a_book()
    with pytest.raises(ConflictError):
        book.raise_request(a_request(state=RequestState.APPROVED))


def test_submitting_moves_it_along():
    book = a_book()
    book.raise_request(a_request())
    assert book.submit("R-001").state is RequestState.SUBMITTED


def test_submitting_twice_is_refused():
    book = a_book()
    submitted(book, a_request())
    with pytest.raises(ConflictError):
        book.submit("R-001")


def test_a_signature_that_reaches_approves_it():
    book = a_book()
    submitted(book, a_request())
    approved = book.approve("R-001", "rvirta", TODAY)
    assert approved.state is RequestState.APPROVED
    assert approved.decided_by == "rvirta"
    assert approved.decided_on == TODAY


def test_a_signature_from_the_wrong_department_does_not_reach():
    book = a_book()
    submitted(book, a_request())
    with pytest.raises(ConflictError) as caught:
        book.approve("R-001", "phamm", TODAY)
    assert caught.value.facts["department"] == "geography"


def test_a_signature_for_the_wrong_building_does_not_reach():
    book = a_book()
    submitted(book, a_request(building="VOS"))
    with pytest.raises(ConflictError):
        book.approve("R-001", "rvirta", TODAY)


def test_somebody_who_does_not_sign_cannot_approve():
    book = a_book()
    submitted(book, a_request())
    with pytest.raises(ConflictError):
        book.approve("R-001", "dsalo", TODAY)


def test_a_master_needs_a_signature_allowed_to_approve_masters():
    book = a_book()
    submitted(book, a_request(symbol="AA", is_master=True))
    with pytest.raises(ConflictError) as caught:
        book.approve("R-001", "rvirta", TODAY)
    assert caught.value.facts["symbol"] == "AA"


def test_the_right_signature_can_approve_a_master():
    book = a_book()
    submitted(book, a_request(keyholder="phamm", building="HEI", symbol="B", is_master=True))
    assert book.approve("R-001", "phamm", TODAY).state is RequestState.APPROVED


def test_a_keyholder_who_may_not_hold_keys_cannot_be_approved():
    directory = a_directory()
    book = RequestBook(directory)
    submitted(book, a_request())
    directory.separate("kmaki", TODAY)
    with pytest.raises(ConflictError) as caught:
        book.approve("R-001", "rvirta", TODAY)
    assert caught.value.facts["standing"] == "separated"


def test_a_lapsed_contractor_cannot_be_approved():
    book = a_book()
    submitted(book, a_request(keyholder="jwexler", building="HEI"))
    with pytest.raises(ConflictError):
        book.approve("R-001", "phamm", TODAY)


def test_a_denial_says_why():
    book = a_book()
    submitted(book, a_request())
    denied = book.deny("R-001", "rvirta", TODAY, "the room is shared, use the master")
    assert denied.state is RequestState.DENIED
    assert denied.note.startswith("the room is shared")


def test_a_denial_without_a_reason_is_refused():
    book = a_book()
    submitted(book, a_request())
    with pytest.raises(InputError):
        book.deny("R-001", "rvirta", TODAY, "  ")


def test_a_decided_request_cannot_be_decided_again():
    book = a_book()
    submitted(book, a_request())
    book.approve("R-001", "rvirta", TODAY)
    with pytest.raises(ConflictError):
        book.deny("R-001", "rvirta", TODAY, "changed my mind")


def test_a_raised_request_cannot_be_approved_before_it_is_submitted():
    book = a_book()
    book.raise_request(a_request())
    with pytest.raises(ConflictError):
        book.approve("R-001", "rvirta", TODAY)


def test_withdrawing_is_not_a_denial():
    book = a_book()
    submitted(book, a_request())
    withdrawn = book.withdraw("R-001", TODAY, "she took a different office")
    assert withdrawn.state is RequestState.WITHDRAWN
    assert not withdrawn.is_decided


def test_a_closed_request_cannot_be_withdrawn():
    book = a_book()
    submitted(book, a_request())
    book.approve("R-001", "rvirta", TODAY)
    with pytest.raises(ConflictError):
        book.withdraw("R-001", TODAY)


def test_only_an_approved_request_gets_filled():
    book = a_book()
    submitted(book, a_request())
    with pytest.raises(ConflictError):
        book.mark_filled("R-001")
    book.approve("R-001", "rvirta", TODAY)
    assert book.mark_filled("R-001").state is RequestState.FILLED


def test_requests_come_back_in_the_order_they_were_raised():
    book = a_book()
    for number in ["R-003", "R-001", "R-002"]:
        book.raise_request(a_request(number))
    assert [r.number for r in book] == ["R-003", "R-001", "R-002"]


def test_requests_can_be_pulled_out_by_state():
    book = a_book()
    submitted(book, a_request("R-001"))
    book.raise_request(a_request("R-002"))
    assert [r.number for r in book.in_state(RequestState.SUBMITTED)] == ["R-001"]
    assert [r.number for r in book.open_requests()] == ["R-001", "R-002"]


def test_a_keyholders_own_requests_come_back_together():
    book = a_book()
    book.raise_request(a_request("R-001"))
    book.raise_request(a_request("R-002", keyholder="dsalo"))
    assert [r.number for r in book.for_keyholder("kmaki")] == ["R-001"]


def test_asking_about_a_keyholder_who_is_not_there():
    with pytest.raises(NotFoundError):
        a_book().for_keyholder("ghost")


def test_what_a_signer_has_decided_comes_back_together():
    book = a_book()
    submitted(book, a_request("R-001"))
    book.approve("R-001", "rvirta", TODAY)
    assert [r.number for r in book.signed_by("rvirta")] == ["R-001"]


def test_a_department_can_see_what_it_owes_a_decision_on():
    book = a_book()
    submitted(book, a_request("R-001"))
    submitted(book, a_request("R-002", keyholder="phamm", building="HEI"))
    assert [r.number for r in book.waiting_on("geography")] == ["R-001"]
    assert [r.number for r in book.waiting_on("facilities")] == ["R-002"]


def test_a_decided_request_is_no_longer_waiting():
    book = a_book()
    submitted(book, a_request("R-001"))
    book.approve("R-001", "rvirta", date(2025, 3, 5))
    assert book.waiting_on("geography") == ()


def test_the_book_is_a_container():
    book = a_book()
    book.raise_request(a_request())
    assert "R-001" in book
    assert "R-999" not in book
    assert len(book) == 1


def test_asking_for_a_request_that_is_not_there():
    with pytest.raises(NotFoundError):
        a_book().request("R-999")

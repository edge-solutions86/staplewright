from datetime import date

import pytest

from dbfixture import fresh_engine, sessions_for, teardown
from hasplib.control.transfer import TransferPolicy, TransferState
from hasplib.errors import ConflictError, NotFoundError
from hasplib.persistence.models.control import IssuedKeyRow, KeyholderRow
from hasplib.services.transfer import TransferService, keys_out_to

ISSUED = date(2025, 1, 6)
CLAIMED = date(2025, 3, 4)
SOON = date(2025, 3, 10)
LATE = date(2025, 5, 1)


@pytest.fixture()
def session():
    engine = fresh_engine()
    made = sessions_for(engine)()
    for name in ("kmaki", "rlaakso", "jkoivu"):
        made.add(
            KeyholderRow(
                identifier=name,
                name=name,
                affiliation="staff",
                department="grounds",
                standing="active",
            )
        )
    made.add(
        KeyholderRow(
            identifier="ovirta",
            name="o virta",
            affiliation="staff",
            department="grounds",
            standing="separated",
            separated_on=date(2025, 2, 1),
        )
    )
    made.add(
        KeyholderRow(
            identifier="pnurmi",
            name="p nurmi",
            affiliation="student",
            department="grounds",
            standing="active",
            ends_on=date(2025, 2, 1),
        )
    )
    made.flush()
    made.add_all(
        [
            IssuedKeyRow(
                tag="41207",
                symbol="AA1",
                keyholder="kmaki",
                issued_on=ISSUED,
                due_on=date(2026, 1, 6),
                state="out",
            ),
            IssuedKeyRow(
                tag="41211",
                symbol="A",
                keyholder="kmaki",
                issued_on=ISSUED,
                due_on=date(2026, 1, 6),
                state="out",
            ),
            IssuedKeyRow(
                tag="41299",
                symbol="AA2",
                keyholder="kmaki",
                issued_on=ISSUED,
                due_on=date(2026, 1, 6),
                state="returned",
            ),
        ]
    )
    made.flush()
    yield made
    made.close()
    teardown(engine)


def a_service(session, policy: TransferPolicy | None = None) -> TransferService:
    service = TransferService(session, policy)
    service.open_series()
    return service


def claimed(session, policy: TransferPolicy | None = None) -> TransferService:
    service = a_service(session, policy)
    service.claim("41207", "rlaakso", CLAIMED)
    return service


def test_a_claim_gets_a_number(session):
    service = a_service(session)
    row = service.claim("41207", "rlaakso", CLAIMED)
    assert row.number == "T-00001"
    assert row.state == "claimed"


def test_a_claim_takes_the_symbol_off_the_key(session):
    service = a_service(session)
    assert service.claim("41207", "rlaakso", CLAIMED).symbol == "AA1"


def test_a_claim_names_whoever_the_ledger_says_had_it(session):
    service = a_service(session)
    assert service.claim("41207", "rlaakso", CLAIMED).passed_by == "kmaki"


def test_a_key_nobody_issued_is_a_404(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.claim("99999", "rlaakso", CLAIMED)


def test_a_key_that_came_back_cannot_be_handed_on(session):
    service = a_service(session)
    with pytest.raises(ConflictError) as caught:
        service.claim("41299", "rlaakso", CLAIMED)
    assert caught.value.facts["state"] == "returned"


def test_a_master_does_not_move_between_people(session):
    service = a_service(session)
    with pytest.raises(ConflictError):
        service.claim("41211", "rlaakso", CLAIMED)


def test_a_shop_that_allows_it_can_hand_on_a_master(session):
    service = a_service(session, TransferPolicy(allow_master_transfer=True))
    assert service.claim("41211", "rlaakso", CLAIMED).symbol == "A"


def test_a_handover_to_somebody_nobody_has_is_a_404(session):
    service = a_service(session)
    with pytest.raises(NotFoundError):
        service.claim("41207", "nobody", CLAIMED)


def test_a_handover_to_somebody_who_left_is_refused(session):
    service = a_service(session)
    with pytest.raises(ConflictError) as caught:
        service.claim("41207", "ovirta", CLAIMED)
    assert caught.value.facts["standing"] == "separated"


def test_a_handover_to_a_lapsed_appointment_is_refused(session):
    service = a_service(session)
    with pytest.raises(ConflictError) as caught:
        service.claim("41207", "pnurmi", CLAIMED)
    assert caught.value.facts["ended"] == "2025-02-01"


def test_one_key_cannot_be_claimed_twice(session):
    service = claimed(session)
    with pytest.raises(ConflictError):
        service.claim("41207", "jkoivu", CLAIMED)


def test_a_claim_does_not_move_the_key(session):
    service = claimed(session)
    assert service.holder_of("41207", LATE) == "kmaki"


def test_confirming_moves_the_key(session):
    service = claimed(session)
    service.confirm("T-00001", SOON, "picked it up")
    assert service.holder_of("41207", LATE) == "rlaakso"


def test_the_key_row_is_never_touched(session):
    service = claimed(session)
    service.confirm("T-00001", SOON)
    assert session.get(IssuedKeyRow, "41207").keyholder == "kmaki"


def test_refusing_leaves_the_key_where_it_was(session):
    service = claimed(session)
    service.refuse("T-00001", SOON, "never got it")
    assert service.holder_of("41207", LATE) == "kmaki"


def test_cancelling_leaves_the_key_where_it_was(session):
    service = claimed(session)
    service.cancel("T-00001", SOON)
    assert service.holder_of("41207", LATE) == "kmaki"


def test_a_handover_nobody_claimed_is_a_404(session):
    with pytest.raises(NotFoundError):
        a_service(session).confirm("T-99999", SOON)


def test_a_claim_inside_its_window_cannot_lapse(session):
    service = claimed(session)
    with pytest.raises(ConflictError):
        service.lapse("T-00001", SOON)


def test_a_claim_past_its_window_lapses(session):
    service = claimed(session)
    assert service.lapse("T-00001", LATE).state == "lapsed"


def test_the_sweep_closes_every_late_claim(session):
    service = claimed(session)
    found = service.lapse_overdue(LATE)
    assert [row.number for row in found] == ["T-00001"]
    assert service.row("T-00001").state == "lapsed"


def test_the_sweep_leaves_the_recent_claims_alone(session):
    service = claimed(session)
    assert service.lapse_overdue(SOON) == ()


def test_confirming_to_somebody_who_has_since_left_is_refused(session):
    service = claimed(session)
    person = session.get(KeyholderRow, "rlaakso")
    person.standing = "separated"
    person.separated_on = date(2025, 3, 5)
    session.flush()
    with pytest.raises(ConflictError):
        service.confirm("T-00001", SOON)


def test_what_a_person_actually_holds(session):
    service = claimed(session)
    service.confirm("T-00001", SOON)
    assert service.holdings("rlaakso", LATE) == ("41207",)
    assert service.holdings("kmaki", LATE) == ("41211",)


def test_the_strays_name_the_ledger_and_the_truth(session):
    service = claimed(session)
    service.confirm("T-00001", SOON)
    assert service.strays(LATE) == (("41207", "kmaki", "rlaakso"),)


def test_a_shop_with_no_handovers_has_no_strays(session):
    assert a_service(session).strays(LATE) == ()


def test_the_exposure_counts_who_is_sitting_on_keys(session):
    service = claimed(session)
    assert service.exposure(LATE) == {"rlaakso": 1}


def test_nothing_late_is_no_exposure(session):
    assert claimed(session).exposure(SOON) == {}


def test_the_late_claims_are_named(session):
    service = claimed(session)
    assert [item.number for item in service.late(LATE)] == ["T-00001"]


def test_the_chain_follows_a_key_through_hands(session):
    service = claimed(session)
    service.confirm("T-00001", SOON)
    service.claim("41207", "jkoivu", SOON)
    service.confirm("T-00002", LATE)
    assert [item.passed_to for item in service.chain_for("41207")] == ["rlaakso", "jkoivu"]


def test_what_a_person_still_owes_a_signature_on(session):
    service = claimed(session)
    assert [row.number for row in service.waiting_on("rlaakso")] == ["T-00001"]


def test_the_transfers_in_a_state_come_back(session):
    service = claimed(session)
    found = service.in_state(TransferState.CLAIMED)
    assert [row.number for row in found] == ["T-00001"]


def test_the_ledger_view_ignores_the_handovers(session):
    service = claimed(session)
    service.confirm("T-00001", SOON)
    assert keys_out_to(session, "kmaki") == ("41207", "41211")


def test_a_person_with_nothing_out_has_nothing(session):
    assert keys_out_to(session, "jkoivu") == ()

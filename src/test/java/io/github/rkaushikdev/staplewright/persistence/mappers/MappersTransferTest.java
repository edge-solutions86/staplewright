package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.applyToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.bookFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.confirmedMoves;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.holderNow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.holdingsOf;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.strays;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.transferFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.transferToRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.Transfer;
import io.github.rkaushikdev.staplewright.control.TransferState;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.Stray;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappersTransferTest {

    private static final LocalDate CLAIMED = LocalDate.of(2025, 3, 4);
    private static final LocalDate SOON = LocalDate.of(2025, 3, 10);
    private static final LocalDate LATER = LocalDate.of(2025, 5, 1);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        for (String name : List.of("kmaki", "rlaakso", "jkoivu")) {
            KeyholderRow person = new KeyholderRow(name, name, "staff", "grounds");
            person.setStanding("active");
            session.add(person);
        }
        session.flush();
        session.add(aKey());
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static Transfer aTransfer() {
        return aTransfer("T-0001", b -> b);
    }

    private static Transfer aTransfer(String number) {
        return aTransfer(number, b -> b);
    }

    private static Transfer aTransfer(UnaryOperator<Transfer.Builder> overrides) {
        return aTransfer("T-0001", overrides);
    }

    private static Transfer aTransfer(String number, UnaryOperator<Transfer.Builder> overrides) {
        Transfer transfer = new Transfer(number, "41207", "AA1", "kmaki", "rlaakso", CLAIMED);
        return overrides.apply(transfer.toBuilder()).build();
    }

    private static Transfer confirmedOn(LocalDate day) {
        return aTransfer(b -> b.state(TransferState.CONFIRMED).settledOn(day));
    }

    private static IssuedKeyRow aKey() {
        return aKey("41207", "kmaki");
    }

    private static IssuedKeyRow aKey(String tag) {
        return aKey(tag, "kmaki");
    }

    private static IssuedKeyRow aKey(String tag, String holder) {
        IssuedKeyRow key = new IssuedKeyRow(tag, "AA1", holder, LocalDate.of(2025, 1, 6));
        key.setDueOn(LocalDate.of(2026, 1, 6));
        key.setState("out");
        return key;
    }

    @Test
    void aTransferGoesToARowAndBack() {
        assertThat(transferFromRow(transferToRow(aTransfer()))).isEqualTo(aTransfer());
    }

    @Test
    void aSettledTransferKeepsItsDay() {
        Transfer held = confirmedOn(SOON);
        assertThat(transferFromRow(transferToRow(held)).settledOn()).contains(SOON);
    }

    @Test
    void aStateNobodyRecognisesIsRefused() {
        TransferRow row = transferToRow(aTransfer());
        row.setState("posted");
        assertThatThrownBy(() -> transferFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aRowMovesToMatchASettledTransfer() {
        TransferRow row = transferToRow(aTransfer());
        Transfer settled =
                aTransfer(b -> b.state(TransferState.CONFIRMED).settledOn(SOON).note("picked up"));
        applyToRow(row, settled);
        assertThat(row.getState()).isEqualTo("confirmed");
        assertThat(row.getSettledOn()).contains(SOON);
        assertThat(row.getNote()).isEqualTo("picked up");
    }

    @Test
    void theBookComesBackOutOfRows() {
        List<TransferRow> rows =
                List.of(transferToRow(aTransfer()), transferToRow(aTransfer("T-0002", b -> b.tag("41208"))));
        assertThat(bookFromRows(rows).size()).isEqualTo(2);
    }

    @Test
    void theBookKeepsATransferInTheStateItReached() {
        TransferRow row =
                transferToRow(aTransfer(b -> b.state(TransferState.LAPSED).settledOn(LATER)));
        assertThat(bookFromRows(List.of(row)).transfer("T-0001").state()).isSameAs(TransferState.LAPSED);
    }

    @Test
    void onlyTheConfirmedMovesCount() {
        List<TransferRow> rows = List.of(
                transferToRow(aTransfer()), transferToRow(aTransfer("T-0002", b -> b.state(TransferState.CONFIRMED)
                        .settledOn(SOON))));
        assertThat(confirmedMoves(rows, "41207").stream()
                        .map(TransferRow::getNumber)
                        .toList())
                .containsExactly("T-0002");
    }

    @Test
    void theMovesComeBackInTheOrderTheySettled() {
        List<TransferRow> rows = List.of(
                transferToRow(aTransfer(
                        "T-0002", b -> b.state(TransferState.CONFIRMED).settledOn(LATER))),
                transferToRow(aTransfer(
                        "T-0001", b -> b.state(TransferState.CONFIRMED).settledOn(SOON))));
        assertThat(confirmedMoves(rows, "41207").stream()
                        .map(TransferRow::getNumber)
                        .toList())
                .containsExactly("T-0001", "T-0002");
    }

    @Test
    void aKeyNobodyHandedOnIsStillWithItsHolder() {
        assertThat(holderNow(aKey(), List.of(), LATER)).isEqualTo("kmaki");
    }

    @Test
    void aConfirmedHandoverMovesTheHolder() {
        List<TransferRow> rows = List.of(transferToRow(confirmedOn(SOON)));
        assertThat(holderNow(aKey(), rows, LATER)).isEqualTo("rlaakso");
    }

    @Test
    void aDayBeforeTheHandoverSettledKeepsTheOldHolder() {
        List<TransferRow> rows = List.of(transferToRow(confirmedOn(SOON)));
        assertThat(holderNow(aKey(), rows, CLAIMED)).isEqualTo("kmaki");
    }

    @Test
    void aClaimNobodyConfirmedMovesNothing() {
        List<TransferRow> rows = List.of(transferToRow(aTransfer()));
        assertThat(holderNow(aKey(), rows, LATER)).isEqualTo("kmaki");
    }

    @Test
    void theLastSettledHandoverWinsEvenWhenItWasClaimedFirst() {
        List<TransferRow> rows = List.of(
                transferToRow(aTransfer("T-0001", b -> b.claimedOn(CLAIMED)
                        .state(TransferState.CONFIRMED)
                        .settledOn(LATER)
                        .passedTo("jkoivu"))),
                transferToRow(aTransfer("T-0002", b -> b.claimedOn(LocalDate.of(2025, 4, 1))
                        .state(TransferState.CONFIRMED)
                        .settledOn(SOON)
                        .passedTo("rlaakso"))));
        assertThat(holderNow(aKey(), rows, LocalDate.of(2025, 6, 1))).isEqualTo("jkoivu");
    }

    @Test
    void whatAPersonActuallyHolds() {
        List<IssuedKeyRow> keys = List.of(aKey(), aKey("41208"));
        List<TransferRow> rows = List.of(transferToRow(confirmedOn(SOON)));
        assertThat(holdingsOf(keys, rows, "kmaki", LATER)).containsExactly("41208");
        assertThat(holdingsOf(keys, rows, "rlaakso", LATER)).containsExactly("41207");
    }

    @Test
    void aPersonWithNothingHoldsNothing() {
        assertThat(holdingsOf(List.of(aKey()), List.of(), "jkoivu", LATER)).isEmpty();
    }

    @Test
    void aKeyThatMovedShowsUpAsAStray() {
        List<TransferRow> rows = List.of(transferToRow(confirmedOn(SOON)));
        assertThat(strays(List.of(aKey()), rows, LATER)).containsExactly(new Stray("41207", "kmaki", "rlaakso"));
    }

    @Test
    void aKeyThatNeverMovedIsNotAStray() {
        assertThat(strays(List.of(aKey()), List.of(), LATER)).isEmpty();
    }

    @Test
    void aTransferRowGoesInAndComesOut() {
        session.add(transferToRow(aTransfer()));
        session.flush();
        TransferRow row = session.get(TransferRow.class, "T-0001").orElseThrow();
        assertThat(row.isOpen()).isTrue();
        assertThat(row.moved()).isFalse();
    }

    @Test
    void aStateTheTableDoesNotKnowIsRefused() {
        TransferRow row = transferToRow(aTransfer());
        row.setState("posted");
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aHandoverToTheSamePersonIsRefused() {
        // No handover from a person to themselves can be built, so the row is written out by hand.
        TransferRow row = new TransferRow("T-0001", "41207", "AA1", "kmaki", "kmaki", CLAIMED);
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSettledTransferWithoutADayIsRefused() {
        TransferRow row = transferToRow(aTransfer());
        row.setState("confirmed");
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSettlementBeforeTheClaimIsRefused() {
        TransferRow row = transferToRow(aTransfer());
        row.setState("confirmed");
        row.setSettledOn(LocalDate.of(2024, 1, 1));
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRefusalWithoutAReasonIsRefused() {
        TransferRow row = transferToRow(aTransfer());
        row.setState("refused");
        row.setSettledOn(SOON);
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void oneKeyCannotHaveTwoOpenClaims() {
        session.add(transferToRow(aTransfer()));
        session.flush();
        session.add(transferToRow(aTransfer("T-0002", b -> b.passedBy("rlaakso").passedTo("jkoivu"))));
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSettledClaimLetsAnotherOneOpen() {
        TransferRow first = transferToRow(aTransfer());
        session.add(first);
        session.flush();
        first.setState("confirmed");
        first.setSettledOn(SOON);
        session.flush();
        session.add(transferToRow(aTransfer("T-0002", b -> b.passedBy("rlaakso").passedTo("jkoivu"))));
        session.flush();
        assertThat(session.all(TransferRow.class)).hasSize(2);
    }

    @Test
    void aHandoverOfAKeyNobodyIssuedIsRefused() {
        session.add(transferToRow(aTransfer(b -> b.tag("99999"))));
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aHandoverToSomebodyNobodyHasIsRefused() {
        session.add(transferToRow(aTransfer(b -> b.passedTo("nobody"))));
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aTransferRowKnowsItsKey() {
        session.add(transferToRow(aTransfer()));
        session.expireAll();
        assertThat(session.get(TransferRow.class, "T-0001")
                        .orElseThrow()
                        .getKey()
                        .getSymbol())
                .isEqualTo("AA1");
    }
}

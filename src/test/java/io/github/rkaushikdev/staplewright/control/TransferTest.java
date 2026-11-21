package io.github.rkaushikdev.staplewright.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class TransferTest {

    private static final LocalDate CLAIMED = LocalDate.of(2025, 3, 4);
    private static final LocalDate SOON = LocalDate.of(2025, 3, 10);
    private static final LocalDate LATE = LocalDate.of(2025, 5, 1);

    private static Transfer aTransfer() {
        return aTransfer("T-0001");
    }

    private static Transfer aTransfer(String number) {
        return new Transfer(number, "41207", "AA1", "kmaki", "rlaakso", CLAIMED);
    }

    private static Transfer aTransfer(UnaryOperator<Transfer.Builder> overrides) {
        return aTransfer("T-0001", overrides);
    }

    private static Transfer aTransfer(String number, UnaryOperator<Transfer.Builder> overrides) {
        return overrides.apply(aTransfer(number).toBuilder()).build();
    }

    private static TransferBook aBook() {
        return aBook(new TransferPolicy());
    }

    private static TransferBook aBook(TransferPolicy policy) {
        TransferBook book = new TransferBook(policy);
        book.claim(aTransfer());
        return book;
    }

    /** The second handover of the same key, from the person who received it the first time. */
    private static Transfer onwards() {
        return aTransfer("T-0002", item -> item.passedBy("rlaakso").passedTo("jkoivu"));
    }

    private static List<String> numbers(Iterable<Transfer> transfers) {
        return StreamSupport.stream(transfers.spliterator(), false)
                .map(Transfer::number)
                .toList();
    }

    @Test
    void aTransferNeedsANumber() {
        assertThatThrownBy(() -> aTransfer(" ")).isInstanceOf(InputError.class);
    }

    @Test
    void aTransferNeedsATag() {
        assertThatThrownBy(() -> aTransfer(item -> item.tag(""))).isInstanceOf(InputError.class);
    }

    @Test
    void aKeyCannotBeHandedToThePersonHoldingIt() {
        assertThatThrownBy(() -> aTransfer(item -> item.passedTo("kmaki"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aPolicyWindowIsAtLeastADay() {
        assertThatThrownBy(() -> new TransferPolicy().withConfirmDays(0)).isInstanceOf(InputError.class);
    }

    @Test
    void aClaimGoesInAndComesBack() {
        TransferBook book = aBook();
        assertThat(book.transfer("T-0001").state()).isSameAs(TransferState.CLAIMED);
    }

    @Test
    void aTransferThatDidNotStartClaimedIsRefused() {
        TransferBook book = new TransferBook();
        assertThatThrownBy(() -> book.claim(aTransfer(item -> item.state(TransferState.CONFIRMED))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aMasterDoesNotMoveBetweenPeople() {
        TransferBook book = new TransferBook();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> book.claim(aTransfer(item -> item.symbol("A")), true));
        assertThat(caught.facts()).containsEntry("symbol", "A");
    }

    @Test
    void aShopThatAllowsItCanMoveAMaster() {
        TransferBook book = new TransferBook(new TransferPolicy().withAllowMasterTransfer(true));
        assertThat(book.claim(aTransfer(item -> item.symbol("A")), true).symbol())
                .isEqualTo("A");
    }

    @Test
    void oneKeyCannotBeClaimedTwiceAtOnce() {
        TransferBook book = aBook();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.claim(onwards()));
        assertThat(caught.facts()).containsEntry("transfer", "T-0001");
    }

    @Test
    void aSettledKeyCanBeClaimedAgain() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        Transfer found = book.claim(onwards());
        assertThat(found.state()).isSameAs(TransferState.CLAIMED);
    }

    @Test
    void aTransferNobodyClaimedIsA404() {
        assertThatThrownBy(() -> new TransferBook().transfer("T-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aClaimDoesNotMoveTheKey() {
        TransferBook book = aBook();
        assertThat(book.transfer("T-0001").moved()).isFalse();
        assertThat(book.transfer("T-0001").holderOn(SOON)).isEqualTo("kmaki");
    }

    @Test
    void confirmingMovesTheKey() {
        TransferBook book = aBook();
        Transfer found = book.confirm("T-0001", SOON, "picked it up on the monday");
        assertThat(found.moved()).isTrue();
        assertThat(found.holderOn(SOON)).isEqualTo("rlaakso");
    }

    @Test
    void theKeyWasStillWithTheOldHolderBeforeTheConfirmation() {
        TransferBook book = aBook();
        Transfer found = book.confirm("T-0001", SOON);
        assertThat(found.holderOn(CLAIMED)).isEqualTo("kmaki");
    }

    @Test
    void aConfirmationBeforeTheClaimIsRefused() {
        TransferBook book = aBook();
        assertThatThrownBy(() -> book.confirm("T-0001", LocalDate.of(2025, 1, 1)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aTransferCannotBeSettledTwice() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThatThrownBy(() -> book.confirm("T-0001", LATE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aRefusalSaysWhy() {
        TransferBook book = aBook();
        assertThatThrownBy(() -> book.refuse("T-0001", SOON, "")).isInstanceOf(InputError.class);
    }

    @Test
    void aRefusedTransferLeavesTheKeyWhereItWas() {
        TransferBook book = aBook();
        Transfer found = book.refuse("T-0001", SOON, "never got it");
        assertThat(found.state()).isSameAs(TransferState.REFUSED);
        assertThat(found.holderOn(LATE)).isEqualTo("kmaki");
    }

    @Test
    void aClaimCanBeTakenBack() {
        TransferBook book = aBook();
        assertThat(book.cancel("T-0001", SOON).state()).isSameAs(TransferState.CANCELLED);
    }

    @Test
    void aClaimInsideItsWindowCannotLapse() {
        TransferBook book = aBook();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.lapse("T-0001", SOON));
        assertThat(caught.facts()).containsEntry("due", "2025-03-18");
    }

    @Test
    void aClaimPastItsWindowLapses() {
        TransferBook book = aBook();
        Transfer found = book.lapse("T-0001", LATE);
        assertThat(found.state()).isSameAs(TransferState.LAPSED);
        assertThat(found.note()).isEqualTo("nobody confirmed inside the window");
    }

    @Test
    void aShorterWindowLapsesSooner() {
        TransferBook book = aBook(new TransferPolicy().withConfirmDays(1));
        assertThat(book.lapse("T-0001", SOON).state()).isSameAs(TransferState.LAPSED);
    }

    @Test
    void aLateClaimIsNamed() {
        TransferBook book = aBook();
        assertThat(numbers(book.late(LATE))).containsExactly("T-0001");
    }

    @Test
    void aSettledClaimIsNeverLate() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(book.late(LATE)).isEmpty();
    }

    @Test
    void theTransfersForATagComeBack() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        book.claim(onwards());
        assertThat(book.forTag("41207")).hasSize(2);
    }

    @Test
    void theTransfersInAStateComeBack() {
        TransferBook book = aBook();
        assertThat(book.inState(TransferState.CLAIMED)).hasSize(1);
    }

    @Test
    void whatAPersonClaimedToHaveHandedOver() {
        assertThat(numbers(aBook().claimedBy("kmaki"))).containsExactly("T-0001");
    }

    @Test
    void whatAPersonStillOwesASignatureOn() {
        assertThat(numbers(aBook().waitingOn("rlaakso"))).containsExactly("T-0001");
    }

    @Test
    void aSettledTransferIsNotWaitingOnAnybody() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(book.waitingOn("rlaakso")).isEmpty();
    }

    @Test
    void whoTheShopBelievesHasAKey() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(book.holderOf("41207", LATE)).contains("rlaakso");
    }

    @Test
    void aKeyNobodyMovedHasNoNewHolder() {
        assertThat(aBook().holderOf("41207", LATE)).isEmpty();
    }

    @Test
    void aDayBeforeTheMoveHasNoNewHolder() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(book.holderOf("41207", CLAIMED)).isEmpty();
    }

    @Test
    void theChainFollowsAKeyThroughHands() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        book.claim(onwards());
        book.confirm("T-0002", LATE);
        assertThat(book.chainFor("41207").stream().map(Transfer::passedTo)).containsExactly("rlaakso", "jkoivu");
    }

    @Test
    void anUnconfirmedClaimIsNotInTheChain() {
        assertThat(aBook().chainFor("41207")).isEmpty();
    }

    @Test
    void theExposureCountsWhoIsSittingOnKeys() {
        TransferBook book = aBook();
        book.claim(aTransfer("T-0002", item -> item.tag("41208").passedTo("rlaakso")));
        assertThat(TransferBook.unconfirmedExposure(book, LATE)).isEqualTo(Map.of("rlaakso", 2));
    }

    @Test
    void nothingLateIsNothingToReport() {
        assertThat(TransferBook.unconfirmedExposure(aBook(), SOON)).isEmpty();
    }

    @Test
    void aKeyThatEndedUpOutsideTheDepartmentIsNamed() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(TransferBook.keysThatMoved(book, List.of("kmaki", "jkoivu"))).containsExactly("41207");
    }

    @Test
    void aKeyThatStayedInsideIsNot() {
        TransferBook book = aBook();
        book.confirm("T-0001", SOON);
        assertThat(TransferBook.keysThatMoved(book, List.of("kmaki", "rlaakso")))
                .isEmpty();
    }

    @Test
    void theBookCountsAndIterates() {
        TransferBook book = aBook();
        assertThat(book.size()).isEqualTo(1);
        assertThat(book.contains("T-0001")).isTrue();
        assertThat(numbers(book)).containsExactly("T-0001");
    }

    @Test
    void aTransferReadsBackAsOneLine() {
        assertThat(aTransfer().describe()).isEqualTo("T-0001 41207 AA1 kmaki to rlaakso claimed");
    }

    @Test
    void aLoadedTransferKeepsTheStateItWasIn() {
        TransferBook book = new TransferBook();
        book.load(aTransfer(item -> item.state(TransferState.LAPSED)));
        assertThat(book.transfer("T-0001").state()).isSameAs(TransferState.LAPSED);
    }
}

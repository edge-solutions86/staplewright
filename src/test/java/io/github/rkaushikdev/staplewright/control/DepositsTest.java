package io.github.rkaushikdev.staplewright.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DepositsTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 3, 4);
    private static final LocalDate LATER = LocalDate.of(2025, 6, 2);

    private static DepositBook aBook() {
        DepositBook book = new DepositBook();
        book.charge("41207", "kmaki", 2500, WHEN);
        book.charge("41208", "dsalo", 2500, WHEN);
        book.charge("41209", "rvirta", 5000, WHEN);
        return book;
    }

    @Test
    void aChargePutsMoneyOnTheTag() {
        DepositBook book = aBook();
        assertThat(book.heldOn("41207")).isEqualTo(2500);
        assertThat(book.totalHeld()).isEqualTo(10000);
    }

    @Test
    void aLineHasToMoveMoney() {
        assertThatThrownBy(() -> new DepositEntry("41207", "kmaki", EntryKind.CHARGE, 0, WHEN))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aForfeitSaysWhy() {
        assertThatThrownBy(() -> new DepositEntry("41207", "kmaki", EntryKind.FORFEIT, 2500, WHEN))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aTagTakesOneDeposit() {
        DepositBook book = aBook();
        assertThatThrownBy(() -> book.charge("41207", "kmaki", 2500, WHEN)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aRefundGivesTheWholeThingBackByDefault() {
        DepositBook book = aBook();
        DepositEntry entry = book.refund("41207", LATER);
        assertThat(entry.cents()).isEqualTo(2500);
        assertThat(book.heldOn("41207")).isZero();
        assertThat(book.totalHeld()).isEqualTo(7500);
    }

    @Test
    void aPartialRefundLeavesTheRestWithTheShop() {
        DepositBook book = aBook();
        book.refund("41207", LATER, 1500);
        assertThat(book.heldOn("41207")).isEqualTo(1000);
    }

    @Test
    void aRefundCannotBeMoreThanTheDeposit() {
        DepositBook book = aBook();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.refund("41207", LATER, 4000));
        assertThat(caught.facts()).containsEntry("charged", 2500);
    }

    @Test
    void aDepositSettlesOnce() {
        DepositBook book = aBook();
        book.refund("41207", LATER);
        assertThatThrownBy(() -> book.refund("41207", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aForfeitKeepsTheMoneyAndClosesTheTag() {
        DepositBook book = aBook();
        book.forfeit("41207", LATER, "key never came back");
        assertThat(book.heldOn("41207")).isZero();
        assertThat(book.forfeited()).isEqualTo(2500);
        assertThat(book.isClosed("41207")).isTrue();
    }

    @Test
    void aForfeitedTagCannotThenBeRefunded() {
        DepositBook book = aBook();
        book.forfeit("41207", LATER, "key never came back");
        assertThatThrownBy(() -> book.refund("41207", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aWaiverClearsTheTagWithoutCountingAsAForfeit() {
        DepositBook book = aBook();
        book.waive("41209", LATER, "department pays centrally");
        assertThat(book.heldOn("41209")).isZero();
        assertThat(book.forfeited()).isZero();
    }

    @Test
    void settlingATagNobodyCharged() {
        DepositBook book = aBook();
        assertThatThrownBy(() -> book.refund("99999", LATER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aPersonsBalanceCoversEveryTagTheyHold() {
        DepositBook book = aBook();
        book.charge("41210", "kmaki", 2500, WHEN);
        assertThat(book.balanceOf("kmaki")).isEqualTo(5000);
        book.refund("41207", LATER);
        assertThat(book.balanceOf("kmaki")).isEqualTo(2500);
    }

    @Test
    void somebodyWithNoDepositsHasNoBalance() {
        assertThat(aBook().balanceOf("phamm")).isZero();
    }

    @Test
    void theOpenTagsAreTheOnesStillHoldingMoney() {
        DepositBook book = aBook();
        book.refund("41207", LATER);
        assertThat(book.openTags()).containsExactly("41208", "41209");
    }

    @Test
    void theLinesOnATagComeBackInOrder() {
        DepositBook book = aBook();
        book.refund("41207", LATER, 1000);
        List<EntryKind> kinds =
                book.entriesFor("41207").stream().map(DepositEntry::kind).toList();
        assertThat(kinds).containsExactly(EntryKind.CHARGE, EntryKind.REFUND);
    }

    @Test
    void theLinesForAPersonComeBackTogether() {
        DepositBook book = aBook();
        assertThat(book.entriesOf("kmaki").stream().map(DepositEntry::tag)).containsExactly("41207");
    }

    @Test
    void theTotalsSplitByWhatTheLineDid() {
        DepositBook book = aBook();
        book.refund("41207", LATER);
        book.forfeit("41208", LATER, "lost on a fishing boat");
        Map<String, Long> totals = book.byKind();
        assertThat(totals).containsEntry("charge", 10000L);
        assertThat(totals).containsEntry("refund", 2500L);
        assertThat(totals).containsEntry("forfeit", 2500L);
    }

    @Test
    void theBookCountsItsOwnLines() {
        DepositBook book = aBook();
        assertThat(book.size()).isEqualTo(3);
        book.refund("41207", LATER);
        assertThat(book.size()).isEqualTo(4);
        List<DepositEntry> walked = new ArrayList<>();
        book.forEach(walked::add);
        assertThat(walked).hasSize(4);
    }

    @ParameterizedTest
    @CsvSource({"0, $0.00", "5, $0.05", "2500, $25.00", "123456, $1234.56", "-2500, -$25.00"})
    void moneyPrintsTheWayAReceiptDoes(long cents, String printed) {
        assertThat(DepositBook.dollars(cents)).isEqualTo(printed);
    }
}

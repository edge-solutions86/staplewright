package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class IssueTest {

    private static final LocalDate LATER = LocalDate.of(2025, 4, 1);

    private static KeyLedger aLedger() {
        return new KeyLedger(aDirectory());
    }

    private static KeyLedger stocked() {
        KeyLedger ledger = aLedger();
        ledger.issue("41207", "AA1", "kmaki", TODAY, 2500);
        ledger.issue("41208", "AA1", "dsalo", TODAY, 2500);
        ledger.issue("41209", "AA", "rvirta", TODAY, 5000);
        ledger.issue("41210", "AB2", "tkoski", TODAY, LocalDate.of(2025, 5, 16));
        return ledger;
    }

    private static List<String> tags(Iterable<IssuedKey> keys) {
        return StreamSupport.stream(keys.spliterator(), false)
                .map(IssuedKey::tag)
                .toList();
    }

    @Test
    void aKeyGoesOutAgainstAPerson() {
        KeyLedger ledger = aLedger();
        IssuedKey key = ledger.issue("41207", "AA1", "kmaki", TODAY);
        assertThat(key.isOut()).isTrue();
        assertThat(key.keyholder()).isEqualTo("kmaki");
        assertThat(key.describe()).isEqualTo("tag 41207 cut AA1, kmaki, out");
    }

    @Test
    void aKeyNeedsATag() {
        assertThatThrownBy(() -> new IssuedKey("  ", "AA1", "kmaki", TODAY)).isInstanceOf(InputError.class);
    }

    @Test
    void aDepositIsNotNegative() {
        assertThatThrownBy(() -> new IssuedKey("41207", "AA1", "kmaki", TODAY, Optional.empty(), -1))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aKeyIsNotDueBackBeforeItGoesOut() {
        assertThatThrownBy(
                        () -> new IssuedKey("41207", "AA1", "kmaki", TODAY, Optional.of(LocalDate.of(2025, 1, 1)), 0))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aTagCannotBeIssuedTwice() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.issue("41207", "AA1", "dsalo", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSeparatedKeyholderGetsNothing() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        KeyLedger ledger = new KeyLedger(directory);
        assertThatThrownBy(() -> ledger.issue("41207", "AA1", "kmaki", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aLapsedContractorGetsNothing() {
        KeyLedger ledger = aLedger();
        assertThatThrownBy(() -> ledger.issue("41207", "AA1", "jwexler", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyCannotBeDueAfterTheContractEnds() {
        KeyLedger ledger = aLedger();
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> ledger.issue("41210", "AB2", "tkoski", TODAY, LocalDate.of(2025, 8, 1)));
        assertThat(caught.facts()).containsEntry("ends_on", "2025-05-16");
    }

    @Test
    void aKeyForSomebodyWhoIsNotInTheDirectory() {
        KeyLedger ledger = aLedger();
        assertThatThrownBy(() -> ledger.issue("41207", "AA1", "ghost", TODAY)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aReturnedKeyIsNoLongerOut() {
        KeyLedger ledger = stocked();
        IssuedKey returned = ledger.takeBack("41207", LATER, "left the department");
        assertThat(returned.state()).isSameAs(KeyState.RETURNED);
        assertThat(returned.closedOn()).contains(LATER);
        assertThat(tags(ledger.out())).doesNotContain("41207");
    }

    @Test
    void aKeyCannotComeBackTwice() {
        KeyLedger ledger = stocked();
        ledger.takeBack("41207", LATER);
        assertThatThrownBy(() -> ledger.takeBack("41207", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aLostKeyAndAStolenKeyAreToldApart() {
        KeyLedger ledger = stocked();
        ledger.reportLost("41207", LATER);
        ledger.reportStolen("41208", LATER, "taken from a desk drawer overnight");
        assertThat(ledger.key("41207").state()).isSameAs(KeyState.LOST);
        assertThat(ledger.key("41208").state()).isSameAs(KeyState.STOLEN);
        assertThat(tags(ledger.gone())).containsExactly("41207", "41208");
    }

    @Test
    void aTheftHasToBeWrittenUp() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.reportStolen("41207", LATER, "   ")).isInstanceOf(InputError.class);
    }

    @Test
    void aKeyThatTurnsUpComesBackRatherThanGoingOut() {
        KeyLedger ledger = stocked();
        ledger.reportLost("41207", LATER);
        IssuedKey found = ledger.found("41207", LATER);
        assertThat(found.state()).isSameAs(KeyState.RETURNED);
        assertThat(found.isOut()).isFalse();
    }

    @Test
    void aKeyThatWasNeverLostCannotBeFound() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.found("41207", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aTransferMovesTheKeyWithoutTheCounter() {
        KeyLedger ledger = stocked();
        IssuedKey moved = ledger.transfer("41207", "dsalo", LATER);
        assertThat(moved.keyholder()).isEqualTo("dsalo");
        assertThat(tags(ledger.heldBy("kmaki"))).isEmpty();
        assertThat(tags(ledger.heldBy("dsalo"))).containsExactly("41207", "41208");
    }

    @Test
    void aTransferToSomebodyWhoMayNotHoldKeysIsRefused() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.transfer("41207", "jwexler", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aTransferToTheCurrentHolderIsRefused() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.transfer("41207", "kmaki", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aReturnedKeyCannotBeTransferred() {
        KeyLedger ledger = stocked();
        ledger.takeBack("41207", LATER);
        assertThatThrownBy(() -> ledger.transfer("41207", "dsalo", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aDueDateCanBePushedOut() {
        KeyLedger ledger = stocked();
        assertThat(ledger.extend("41210", LocalDate.of(2025, 6, 30)).dueOn()).contains(LocalDate.of(2025, 6, 30));
    }

    @Test
    void aDueDateCannotBePulledBack() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.extend("41210", LocalDate.of(2025, 3, 1)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyWithNoDueDateHasNothingToExtend() {
        KeyLedger ledger = stocked();
        assertThatThrownBy(() -> ledger.extend("41207", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anOverdueKeyIsReportedWithItsDays() {
        KeyLedger ledger = stocked();
        LocalDate late = LocalDate.of(2025, 6, 1);
        List<IssuedKey> overdue = ledger.overdue(late);
        assertThat(tags(overdue)).containsExactly("41210");
        assertThat(overdue.get(0).daysLate(late)).isEqualTo(16);
    }

    @Test
    void nothingIsOverdueBeforeTheDueDate() {
        assertThat(stocked().overdue(TODAY)).isEmpty();
    }

    @Test
    void aKeyWithNoDueDateIsNeverOverdue() {
        KeyLedger ledger = stocked();
        assertThat(ledger.key("41207").overdueOn(LocalDate.of(2030, 1, 1))).isFalse();
    }

    @Test
    void holdingsAndHistoryAreDifferentQuestions() {
        KeyLedger ledger = stocked();
        ledger.takeBack("41207", LATER);
        assertThat(ledger.heldBy("kmaki")).isEmpty();
        assertThat(tags(ledger.historyOf("kmaki"))).containsExactly("41207");
    }

    @Test
    void keysCutToOneSymbolComeBackTogether() {
        KeyLedger ledger = stocked();
        assertThat(tags(ledger.cutTo("AA1"))).containsExactly("41207", "41208");
        assertThat(ledger.cutTo("ZZ9")).isEmpty();
    }

    @Test
    void aReturnedKeyDropsOutOfTheSymbolList() {
        KeyLedger ledger = stocked();
        ledger.takeBack("41207", LATER);
        assertThat(tags(ledger.cutTo("AA1"))).containsExactly("41208");
    }

    @Test
    void depositsAddUpOverWhatIsOut() {
        KeyLedger ledger = stocked();
        assertThat(ledger.depositsHeld()).isEqualTo(10000);
        ledger.takeBack("41209", LATER);
        assertThat(ledger.depositsHeld()).isEqualTo(5000);
    }

    @Test
    void theCountBySymbolPutsTheCommonKeyFirst() {
        Map<String, Integer> counts = stocked().countBySymbol();
        assertThat(counts.keySet()).containsExactly("AA1", "AA", "AB2");
        assertThat(counts).containsEntry("AA1", 2);
    }

    @Test
    void theLedgerIsAContainerInIssueOrder() {
        KeyLedger ledger = stocked();
        assertThat(ledger.contains("41207")).isTrue();
        assertThat(ledger.contains("99999")).isFalse();
        assertThat(tags(ledger)).containsExactly("41207", "41208", "41209", "41210");
        assertThat(ledger.size()).isEqualTo(4);
    }

    @Test
    void askingAboutATagThatWasNeverIssued() {
        assertThatThrownBy(() -> aLedger().key("99999")).isInstanceOf(NotFoundError.class);
    }
}

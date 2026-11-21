package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnnualTest {

    /** The same keys, but with a day they were due back. */
    private static KeyLedger aDatedLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY, LocalDate.of(2025, 6, 1));
        ledger.issue("41210", "A", "phamm", TODAY, LocalDate.of(2025, 6, 1));
        return ledger;
    }

    private static KeyLedger aLostLedger() {
        KeyLedger ledger = aLedger();
        ledger.reportLost("41208", TODAY, "left it in a taxi");
        return ledger;
    }

    private static List<String> tags(List<IssuedKey> keys) {
        return keys.stream().map(IssuedKey::tag).toList();
    }

    @Test
    void aReturnIsForARealYear() {
        assertThatThrownBy(() -> new AnnualReturn(1066, 1, 1, 1, 1)).isInstanceOf(InputError.class);
    }

    @Test
    void aChangeKeyIsNotAMaster() {
        assertThat(AnnualPages.isMaster("AA1")).isFalse();
    }

    @Test
    void aMasterIs() {
        assertThat(AnnualPages.isMaster("AA")).isTrue();
        assertThat(AnnualPages.isMaster("GGM")).isTrue();
    }

    @Test
    void theMastersOutComeBackShallowestFirst() {
        List<IssuedKey> found = AnnualPages.mastersOut(aLedger());
        assertThat(found.stream().map(IssuedKey::symbol).toList()).containsExactly("GGM", "A", "AA");
    }

    @Test
    void aLedgerWithNoMastersHasNoneOut() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY);
        assertThat(AnnualPages.mastersOut(ledger)).isEmpty();
    }

    @Test
    void aLostKeyIsUnaccountedFor() {
        assertThat(tags(AnnualPages.unaccountedKeys(aLostLedger(), TODAY))).containsExactly("41208");
    }

    @Test
    void aKeyStillInDateIsAccountedFor() {
        assertThat(AnnualPages.unaccountedKeys(aLedger(), TODAY)).isEmpty();
    }

    @Test
    void aKeyOutPastItsDayIsUnaccountedFor() {
        List<IssuedKey> found = AnnualPages.unaccountedKeys(aDatedLedger(), LocalDate.of(2030, 1, 1));
        assertThat(tags(found)).containsExactly("41207", "41210");
    }

    @Test
    void aKeyWithNoDayOnItIsNeverOverdue() {
        assertThat(AnnualPages.unaccountedKeys(aLedger(), LocalDate.of(2030, 1, 1)))
                .isEmpty();
    }

    @Test
    void aLostKeyIsNotCountedTwice() {
        KeyLedger ledger = aDatedLedger();
        ledger.reportLost("41207", TODAY, "left it in a taxi");
        List<IssuedKey> found = AnnualPages.unaccountedKeys(ledger, LocalDate.of(2030, 1, 1));
        assertThat(found.stream().filter(key -> key.tag().equals("41207")).count())
                .isEqualTo(1);
    }

    @Test
    void theReturnCountsWhatIsOut() {
        AnnualReturn found = AnnualPages.take(aLedger(), aDirectory(), TODAY);
        assertThat(found.keysOut()).isEqualTo(5);
        assertThat(found.mastersOut()).isEqualTo(3);
        assertThat(found.unaccounted()).isEqualTo(0);
    }

    @Test
    void theReturnCountsThePeopleHoldingSomething() {
        assertThat(AnnualPages.take(aLedger(), aDirectory(), TODAY).keyholders())
                .isEqualTo(4);
    }

    @Test
    void theKeysPerHolderIsTheAverage() {
        AnnualReturn found = AnnualPages.take(aLedger(), aDirectory(), TODAY);
        assertThat(found.keysPerHolder()).isEqualTo(1.25);
    }

    @Test
    void aShopWithNobodyHoldingAnythingAveragesNothing() {
        assertThat(new AnnualReturn(2025, 0, 0, 0, 0).keysPerHolder()).isEqualTo(0.0);
    }

    @Test
    void aReturnReadsBackAsOneLine() {
        AnnualReturn found = AnnualPages.take(aLedger(), aDirectory(), TODAY);
        assertThat(found.describe()).isEqualTo("2025: 5 keys out, 3 masters, 0 unaccounted");
    }

    @Test
    void theMasterTableNamesEverybodyHoldingOne() {
        List<String> lines = AnnualPages.masterTable(aLedger());
        assertThat(lines.get(0)).isEqualTo("3 master keys are out");
        assertThat(lines).anyMatch(line -> line.contains("phamm"));
    }

    @Test
    void theMasterTableCanSayHowFarAKeyReaches() {
        List<String> lines = AnnualPages.masterTable(aLedger(), aCoverage());
        assertThat(lines.get(1)).endsWith("doors");
        assertThat(lines.subList(2, lines.size())).anyMatch(line -> {
            List<String> words = Text.words(Layout.stripEnd(line));
            return Text.isDigits(words.get(words.size() - 1));
        });
    }

    @Test
    void aShopWithNoMastersOutSaysSo() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY);
        assertThat(AnnualPages.masterTable(ledger)).containsExactly("no master keys are out");
    }

    @Test
    void theUnaccountedTableSaysWhyEachOneIsOnIt() {
        List<String> lines = AnnualPages.unaccountedTable(aLostLedger(), TODAY);
        assertThat(lines.get(0)).isEqualTo("1 keys cannot be accounted for");
        assertThat(lines.get(1)).contains("lost");
    }

    @Test
    void anOverdueKeySaysOverdue() {
        List<String> lines = AnnualPages.unaccountedTable(aDatedLedger(), LocalDate.of(2030, 1, 1));
        assertThat(lines.subList(1, lines.size())).allMatch(line -> line.contains("overdue"));
    }

    @Test
    void aCleanYearSaysEverythingIsAccountedFor() {
        assertThat(AnnualPages.unaccountedTable(aLedger(), TODAY)).containsExactly("every key out is accounted for");
    }

    @Test
    void theConcentrationIsMostKeysFirst() {
        assertThat(AnnualPages.concentration(aLedger()).get(0)).isEqualTo(new Tally("phamm", 2));
    }

    @Test
    void theConcentrationCanBeCutShort() {
        assertThat(AnnualPages.concentration(aLedger(), 1)).hasSize(1);
    }

    @Test
    void aFirstYearHasNothingToCompareAgainst() {
        AnnualReturn found = AnnualPages.take(aLedger(), aDirectory(), TODAY);
        assertThat(AnnualPages.movement(found, Optional.empty()).get(0)).startsWith("no return was filed");
    }

    @Test
    void theMovementSaysWhichWayEachNumberWent() {
        AnnualReturn thisYear = new AnnualReturn(2025, 200, 12, 4, 150);
        AnnualReturn lastYear = new AnnualReturn(2024, 180, 12, 1, 150);
        List<String> lines = AnnualPages.movement(thisYear, lastYear);
        assertThat(lines.get(0)).isEqualTo("against 2024");
        assertThat(lines.get(1)).contains("up 20");
        assertThat(lines.get(2)).contains("level 0");
        assertThat(lines.get(3)).contains("up 3");
    }

    @Test
    void aNumberThatFellSaysDown() {
        List<String> lines =
                AnnualPages.movement(new AnnualReturn(2025, 100, 1, 1, 10), new AnnualReturn(2024, 120, 1, 1, 10));
        assertThat(lines.get(1)).contains("down 20");
    }

    @Test
    void theReturnReadsTopToBottom() {
        List<String> lines = AnnualPages.annualReturn(aLedger(), aDirectory(), TODAY, aCoverage());
        assertThat(lines.get(0)).isEqualTo("key control return for 2025");
        assertThat(lines).anyMatch(line -> line.startsWith("3 master keys are out"));
        assertThat(lines).anyMatch(line -> line.equals("most keys held"));
    }

    @Test
    void theReturnCarriesLastYearWhenThereIsOne() {
        AnnualReturn last = new AnnualReturn(2024, 4, 2, 0, 3);
        List<String> lines =
                AnnualPages.annualReturn(aLedger(), aDirectory(), TODAY, Optional.empty(), Optional.of(last));
        assertThat(lines.get(lines.size() - 5)).isEqualTo("against 2024");
    }

    @Test
    void noReturnsFiledSaysSo() {
        assertThat(AnnualPages.returnsTable(List.of())).containsExactly("no returns have been filed");
    }

    @Test
    void theReturnsTableIsOldestFirst() {
        List<String> lines = AnnualPages.returnsTable(
                List.of(new AnnualReturn(2025, 1, 1, 1, 1), new AnnualReturn(2023, 1, 1, 1, 1)));
        assertThat(lines.get(0)).startsWith("year");
        assertThat(lines.get(1)).startsWith("2023");
    }

    @Test
    void theReturnsTableHasARowPerYear() {
        List<String> lines = AnnualPages.returnsTable(
                List.of(new AnnualReturn(2025, 1, 1, 1, 1), new AnnualReturn(2024, 1, 1, 1, 1)));
        assertThat(lines).hasSize(3);
    }
}

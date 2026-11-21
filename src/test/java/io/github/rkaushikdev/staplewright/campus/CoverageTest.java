package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.campus.CoverageMap.keysThatOpen;
import static io.github.rkaushikdev.staplewright.campus.CoverageMap.openingsATagTurns;
import static io.github.rkaushikdev.staplewright.campus.CoverageMap.peopleWhoCanOpen;
import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static io.github.rkaushikdev.staplewright.support.Keyed.aRegister;
import static io.github.rkaushikdev.staplewright.support.Keyed.keyingGroups;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CoverageTest {

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static List<String> serials(List<Cylinder> cylinders) {
        return cylinders.stream().map(Cylinder::serial).toList();
    }

    private static List<String> tags(List<IssuedKey> keys) {
        return keys.stream().map(IssuedKey::tag).toList();
    }

    @Test
    void aChangeKeyOpensTheOneDoorItWasCutFor() {
        assertThat(aCoverage().openingsFor(sym("AA1"))).containsExactly("MOO-214", "MOO-216");
    }

    @Test
    void aMasterOpensEverythingUnderItThatSaysSo() {
        assertThat(aCoverage().openingsFor(sym("AA"))).containsExactly("MOO-214", "MOO-216", "MOO-218");
    }

    @Test
    void theTopKeyOpensEveryMappedCylinder() {
        assertThat(aCoverage().openingsFor(sym("GGM"))).hasSize(6);
    }

    @Test
    void aSymbolNobodyKeyedOpensNothing() {
        assertThat(aCoverage().openingsFor(sym("ZZ9"))).isEmpty();
    }

    @Test
    void aDoorReportsEverySymbolThatTurnsIt() {
        List<KeySymbol> symbols = aCoverage().symbolsOpening("MOO-214");
        assertThat(symbols).extracting(KeySymbol::render).containsExactly("GGM", "A", "AA", "AA1");
    }

    @Test
    void symbolsComeBackShallowFirst() {
        List<Integer> levels = aCoverage().symbolsOpening("MOO-218").stream()
                .map(KeySymbol::level)
                .toList();
        assertThat(levels).isEqualTo(levels.stream().sorted().toList());
    }

    @Test
    void aDoorWithNoCylinderReportsNothing() {
        assertThat(aCoverage().symbolsOpening("MOO-260")).isEmpty();
    }

    @Test
    void severalSymbolsAtOnceComeBackAsOneList() {
        List<String> both = aCoverage().openingsForAny(List.of(sym("AA1"), sym("BA1")));
        assertThat(both).containsExactly("HEI-001", "MOO-214", "MOO-216");
    }

    @Test
    void theBuildingsAKeyReaches() {
        CoverageMap coverage = aCoverage();
        assertThat(coverage.buildingsFor(sym("AA1"))).containsExactly("MOO");
        assertThat(coverage.buildingsFor(sym("GGM"))).containsExactly("HEI", "MOO");
    }

    @Test
    void theCylindersThatWouldHaveToComeApart() {
        assertThat(serials(aCoverage().cylindersFor(sym("AA")))).containsExactly("C-1001", "C-1002", "C-1003");
    }

    @Test
    void aShelvedCylinderIsNotInAnybodysReach() {
        assertThat(serials(aCoverage().cylindersFor(sym("GGM")))).doesNotContain("C-1099");
    }

    @Test
    void aCylinderWithNoGroupIsReportedAsUnmapped() {
        HardwareRegister register = aRegister();
        register.install("C-1099", "VOS-311", Position.LOCKSET);
        CoverageMap coverage = new CoverageMap(register, keyingGroups());
        assertThat(serials(coverage.unmappedCylinders())).containsExactly("C-1099");
    }

    @Test
    void aCylinderWithAGroupNobodyDefinedIsAlsoUnmapped() {
        HardwareRegister register = aRegister();
        register.assignGroup("C-1001", "a group that does not exist");
        CoverageMap coverage = new CoverageMap(register, keyingGroups());
        assertThat(serials(coverage.unmappedCylinders())).containsExactly("C-1001");
    }

    @Test
    void aDoorWithNothingInTheLocksetIsReported() {
        Map<String, Position> uncovered = aCoverage().uncoveredPositions().stream()
                .collect(Collectors.toMap(OpeningPosition::opening, OpeningPosition::position));
        assertThat(uncovered).containsKey("VOS-311");
        assertThat(uncovered.get("VOS-311")).isSameAs(Position.LOCKSET);
        assertThat(uncovered).doesNotContainKey("MOO-214");
    }

    @Test
    void askingForAGroupThatWasNeverDefined() {
        assertThatThrownBy(() -> aCoverage().group("nothing like this")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theReachOfAKeyIsTheCountOfItsDoors() {
        CoverageMap coverage = aCoverage();
        assertThat(coverage.reachOf(sym("AA1"))).isEqualTo(2);
        assertThat(coverage.reachOf(sym("GGM"))).isEqualTo(6);
    }

    @Test
    void theWidestKeysComeFirst() {
        List<SymbolReach> widest = aCoverage().widestSymbols(3);
        assertThat(widest.get(0).symbol()).isEqualTo(sym("GGM"));
        assertThat(widest.get(0).cylinders()).isEqualTo(6);
        List<Integer> counts = widest.stream().map(SymbolReach::cylinders).toList();
        assertThat(counts)
                .isEqualTo(counts.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Test
    void aTagTurnsWhateverItsCutTurns() {
        CoverageMap coverage = aCoverage();
        KeyLedger ledger = aLedger();
        assertThat(openingsATagTurns(coverage, ledger, "41207")).containsExactly("MOO-214", "MOO-216");
        assertThat(openingsATagTurns(coverage, ledger, "41211")).hasSize(6);
    }

    @Test
    void aTagThatWasNeverIssued() {
        assertThatThrownBy(() -> openingsATagTurns(aCoverage(), aLedger(), "99999"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theKeysOutOnASymbolComeBackByTag() {
        assertThat(tags(keysThatOpen(aLedger(), List.of(sym("AA1"))))).containsExactly("41207", "41208");
    }

    @Test
    void aReturnedKeyIsNotOutOnAnything() {
        KeyLedger ledger = aLedger();
        ledger.takeBack("41207", ledger.key("41207").issuedOn());
        assertThat(tags(keysThatOpen(ledger, List.of(sym("AA1"))))).containsExactly("41208");
    }

    @Test
    void everybodyWhoCanOpenOneDoor() {
        assertThat(peopleWhoCanOpen(aCoverage(), aLedger(), "MOO-214"))
                .containsExactly("dsalo", "kmaki", "phamm", "rvirta");
    }

    @Test
    void aDoorOnlyTheTopKeyReaches() {
        assertThat(peopleWhoCanOpen(aCoverage(), aLedger(), "HEI-001")).containsExactly("phamm");
    }

    @Test
    void aDoorNobodyCanOpen() {
        assertThat(peopleWhoCanOpen(aCoverage(), aLedger(), "MOO-260")).isEmpty();
    }
}

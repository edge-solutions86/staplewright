package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompromiseTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    private static Incident anIncident() {
        return anIncident("I-2025-004", "41207", "AA1");
    }

    private static Incident anIncident(String number, String tag, String symbol) {
        return anIncident(number, tag, symbol, Cause.LOST, "");
    }

    private static Incident anIncident(String number, String tag, String symbol, Cause cause, String note) {
        return new Incident(number, tag, symbol, cause, WHEN, "kmaki", note);
    }

    private static Assessment assessed(Incident incident) {
        return Assessment.assess(incident, aCoverage(), aLedger());
    }

    @Test
    void anIncidentNeedsANumber() {
        assertThatThrownBy(() -> anIncident("  ", "41207", "AA1")).isInstanceOf(InputError.class);
    }

    @Test
    void aTheftHasToBeWrittenUp() {
        assertThatThrownBy(() -> anIncident("I-2025-004", "41207", "AA1", Cause.STOLEN, ""))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aTheftWithANoteIsFine() {
        assertThat(anIncident("I-2025-004", "41207", "AA1", Cause.STOLEN, "taken from a truck")
                        .cause())
                .isSameAs(Cause.STOLEN);
    }

    @Test
    void aChangeKeyToTwoOfficesIsRoutine() {
        Assessment found = assessed(anIncident());
        assertThat(found.openings()).containsExactly("MOO-214", "MOO-216");
        assertThat(found.severity()).isSameAs(Severity.ROUTINE);
        assertThat(found.isMaster()).isFalse();
    }

    @Test
    void theAssessmentListsTheCylindersToTakeApart() {
        Assessment found = assessed(anIncident());
        assertThat(found.cylinders()).containsExactly("C-1001", "C-1002");
    }

    @Test
    void theOtherKeysOnThatCutHaveToBeRecut() {
        Assessment found = assessed(anIncident());
        assertThat(found.keysToRecut()).containsExactly("41208");
        assertThat(found.holdersAffected()).containsExactly("dsalo");
    }

    @Test
    void theMissingKeyIsNotInItsOwnRecutList() {
        Assessment found = assessed(anIncident());
        assertThat(found.keysToRecut()).doesNotContain("41207");
    }

    @Test
    void aMasterIsGradedHigherThanAChangeKey() {
        Assessment found = assessed(anIncident("I-2025-004", "41209", "AA"));
        assertThat(found.isMaster()).isTrue();
        assertThat(found.severity()).isSameAs(Severity.SERIOUS);
    }

    @Test
    void aKeyThatReachesTwoBuildingsIsCritical() {
        Assessment found = assessed(anIncident("I-2025-004", "41211", "GGM"));
        assertThat(found.buildings()).containsExactly("HEI", "MOO");
        assertThat(found.severity()).isSameAs(Severity.CRITICAL);
    }

    @Test
    void anExteriorDoorShowsUpOnItsOwnLine() {
        Assessment found = assessed(anIncident("I-2025-004", "41210", "A"));
        assertThat(found.exteriorOpenings()).containsExactly("MOO-101");
        assertThat(found.touchesOutside()).isTrue();
    }

    @Test
    void aKeyThatReachesNothingIsStillAssessable() {
        Assessment found = assessed(anIncident("I-2025-004", "41207", "ZZ9"));
        assertThat(found.openings()).isEmpty();
        assertThat(found.severity()).isSameAs(Severity.ROUTINE);
    }

    @Test
    void theSummaryNamesTheIncidentAndTheGrade() {
        Assessment found = assessed(anIncident());
        assertThat(found.summary()).startsWith("I-2025-004 AA1 lost");
        assertThat(found.summary()).contains("routine");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, false, false, LOST, ROUTINE",
        "5, 1, false, false, LOST, ROUTINE",
        "6, 1, false, false, LOST, ELEVATED",
        "1, 1, true, false, LOST, ELEVATED",
        "1, 1, false, false, STOLEN, ELEVATED",
        "21, 1, false, false, LOST, SERIOUS",
        "3, 1, false, true, LOST, SERIOUS",
        "3, 2, false, true, LOST, CRITICAL",
        "1, 1, true, false, STOLEN, CRITICAL",
    })
    void theLadderReadsTheSameWayEveryTime(
            int openings, int buildings, boolean exterior, boolean master, Cause cause, Severity expected) {
        assertThat(Severity.grade(openings, buildings, exterior, master, cause)).isSameAs(expected);
    }

    @Test
    void aMasterInsideOneBuildingIsNotAutomaticallyCritical() {
        assertThat(Severity.grade(4, 1, false, true, Cause.LOST)).isSameAs(Severity.SERIOUS);
    }

    @Test
    void theMorningMeetingGetsTheWorstOneFirst() {
        CoverageMap coverage = aCoverage();
        KeyLedger ledger = aLedger();
        List<Assessment> found = Assessment.worstFirst(List.of(
                Assessment.assess(anIncident("I-1", "41207", "AA1"), coverage, ledger),
                Assessment.assess(anIncident("I-2", "41211", "GGM"), coverage, ledger),
                Assessment.assess(anIncident("I-3", "41209", "AA"), coverage, ledger)));
        assertThat(found).extracting(item -> item.incident().number()).containsExactly("I-2", "I-3", "I-1");
    }

    @Test
    void twoIncidentsAtTheSameGradeSortByReach() {
        CoverageMap coverage = aCoverage();
        KeyLedger ledger = aLedger();
        Assessment first = Assessment.assess(anIncident("I-1", "41207", "AA1"), coverage, ledger);
        Assessment second = Assessment.assess(anIncident("I-2", "41207", "ZZ9"), coverage, ledger);
        assertThat(Assessment.worstFirst(List.of(second, first))
                        .get(0)
                        .incident()
                        .number())
                .isEqualTo("I-1");
    }

    @Test
    void anAssessmentIsAPlainRecord() {
        Assessment found = assessed(anIncident());
        assertThat(found).isInstanceOf(Assessment.class);
        assertThat(found.getClass().isRecord()).isTrue();
        assertThat(found.openingCount()).isEqualTo(2);
    }
}

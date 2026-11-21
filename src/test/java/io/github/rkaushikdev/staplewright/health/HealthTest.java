package io.github.rkaushikdev.staplewright.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private CylinderRow aCylinder(String serial) {
        return session.get(CylinderRow.class, serial).orElseThrow();
    }

    private static List<String> subjects(List<Finding> found) {
        return found.stream().map(Finding::subject).toList();
    }

    @Test
    void aHealthyCampusAnswersForNothingSerious() {
        assertThat(Health.worst(Health.runChecks(session, TODAY))).isNotEqualTo(java.util.Optional.of(Severity.FAULT));
    }

    @Test
    void aCylinderInstalledWithNoGroupIsAFault() {
        aCylinder("C-1001").setGroup(null);
        session.flush();
        List<Finding> found = Health.unkeyedOpenings(session, TODAY);
        assertThat(subjects(found)).containsExactly("C-1001");
        assertThat(found.get(0).severity()).isEqualTo(Severity.FAULT);
    }

    @Test
    void theFaultSaysWhichDoorItIsOn() {
        aCylinder("C-1001").setGroup(null);
        session.flush();
        assertThat(Health.unkeyedOpenings(session, TODAY).get(0).detail()).contains("installed at");
    }

    @Test
    void aKeyedCoreOnTheShelfIsOnlyANote() {
        CylinderRow row = aCylinder("C-1001");
        row.setInstalledAt(null);
        row.setPosition(null);
        session.flush();
        assertThat(Health.uninstalledCylinders(session, TODAY).get(0).severity())
                .isEqualTo(Severity.NOTE);
    }

    @Test
    void nothingOnTheShelfIsNothingToReport() {
        assertThat(Health.uninstalledCylinders(session, TODAY)).isEmpty();
    }

    @Test
    void aKeyOutToSomebodyWhoHasLeftIsAFault() {
        KeyholderRow person = session.all(KeyholderRow.class).get(0);
        person.setEndsOn(LocalDate.of(2024, 1, 1));
        IssuedKeyRow key = session.first(
                        "select k from IssuedKeyRow k where k.keyholder = ?1",
                        IssuedKeyRow.class,
                        person.getIdentifier())
                .orElseThrow();
        key.setState("out");
        session.flush();
        List<Finding> found = Health.keysHeldByLapsedPeople(session, TODAY);
        assertThat(found.get(0).severity()).isEqualTo(Severity.FAULT);
        assertThat(found.get(0).detail()).contains(person.getIdentifier());
    }

    @Test
    void aReturnedKeyFromSomebodyWhoHasLeftIsFine() {
        session.all(KeyholderRow.class).get(0).setEndsOn(LocalDate.of(2024, 1, 1));
        for (IssuedKeyRow key : session.all(IssuedKeyRow.class)) {
            key.setState("returned");
        }
        session.flush();
        assertThat(Health.keysHeldByLapsedPeople(session, TODAY)).isEmpty();
    }

    @Test
    void aQuarantineWithNothingInItChecksNothing() {
        assertThat(Health.quarantinedCutsInUse(session, TODAY)).isEmpty();
    }

    @Test
    void aCutInUseThatSomebodyRetiredIsAFault() {
        session.add(new RetiredBittingRow(Bitting.parse("442446"), "compromised", LocalDate.of(2025, 1, 4)));
        session.flush();
        List<Finding> found = Health.quarantinedCutsInUse(session, TODAY);
        assertThat(subjects(found)).containsExactly("GGM");
        assertThat(found.get(0).detail()).contains("compromised");
    }

    @Test
    void aRetiredCutNobodyUsesIsQuiet() {
        session.add(new RetiredBittingRow(Bitting.parse("909090"), "superseded", LocalDate.of(2025, 1, 4)));
        session.flush();
        assertThat(Health.quarantinedCutsInUse(session, TODAY)).isEmpty();
    }

    @Test
    void aGroupWithNothingKeyedToItIsAWarning() {
        GroupRow existing = session.all(GroupRow.class).get(0);
        session.add(GroupRow.of(existing.getSystem(), "the old wing"));
        session.flush();
        List<Finding> found = Health.groupsWithNoCylinders(session, TODAY);
        assertThat(subjects(found)).contains("the old wing");
        assertThat(found.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void aKeyedDoorWithNothingInItIsAWarning() {
        List<Finding> found = Health.openingsWithNoCylinder(session, TODAY);
        assertThat(found).isNotEmpty().allMatch(item -> item.severity() == Severity.WARNING);
    }

    @Test
    void aDoorThatTakesNoCylinderIsNeverReported() {
        assertThat(subjects(Health.openingsWithNoCylinder(session, TODAY))).doesNotContain("MOO-100");
    }

    @Test
    void anUncutSymbolIsANoteNamingItsSystem() {
        ScheduleEntryRow row = session.first(
                        "select e from ScheduleEntryRow e where e.symbol = ?1",
                        ScheduleEntryRow.class,
                        KeySymbol.parse("AA1"))
                .orElseThrow();
        row.setBitting(null);
        session.flush();
        List<Finding> found = Health.uncutSymbols(session, TODAY);
        assertThat(found.get(0).subject()).isEqualTo("AA1");
        assertThat(found.get(0).detail()).isEqualTo("drawn on chester park");
    }

    @Test
    void theFindingsComeBackWorstFirst() {
        aCylinder("C-1001").setGroup(null);
        session.flush();
        assertThat(Health.runChecks(session, TODAY).get(0).severity()).isEqualTo(Severity.FAULT);
    }

    @Test
    void aCallerCanRunOneCheckOnItsOwn() {
        assertThat(Health.runChecks(session, TODAY, List.of(Health::uninstalledCylinders)))
                .isEmpty();
    }

    @Test
    void theWorstOfNothingIsNothing() {
        assertThat(Health.worst(List.of())).isEmpty();
    }

    @Test
    void theCountsCoverEverySeverity() {
        assertThat(Health.countsBySeverity(Health.runChecks(session, TODAY)))
                .containsOnlyKeys("note", "warning", "fault");
    }

    @Test
    void aCleanReportSaysSo() {
        assertThat(Health.healthReport(session, TODAY).get(0)).isEqualTo("self check at 2025-06-30");
    }

    @Test
    void theReportGroupsTheFindingsUnderTheCheck() {
        aCylinder("C-1001").setGroup(null);
        session.flush();
        List<String> lines = Health.healthReport(session, TODAY);
        assertThat(lines).contains("unmapped cylinders");
        assertThat(lines).anyMatch(line -> line.strip().startsWith("fault"));
    }

    @Test
    void aReportWithNothingWrongAtAll() {
        List<String> lines = Health.healthReport(session, TODAY);
        assertThat(lines.get(1).endsWith("notes") || lines.get(1).equals("nothing to answer for"))
                .isTrue();
    }

    @Test
    void theExitCodeIsNoughtUnlessSomethingIsReallyWrong() {
        assertThat(Health.exitCode(Health.runChecks(session, TODAY))).isZero();
    }

    @Test
    void aFaultMakesTheExitCodeOne() {
        aCylinder("C-1001").setGroup(null);
        session.flush();
        assertThat(Health.exitCode(Health.runChecks(session, TODAY))).isEqualTo(1);
    }
}

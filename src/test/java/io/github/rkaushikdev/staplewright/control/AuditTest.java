package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditTest {

    private static final LocalDate WALK = LocalDate.of(2025, 9, 30);

    private static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY, 2500);
        ledger.issue("41208", "AA1", "dsalo", TODAY, 2500);
        ledger.issue("41209", "AA", "rvirta", TODAY, 5000);
        ledger.issue("41301", "B1", "phamm", TODAY, 2500);
        return ledger;
    }

    private static DepositBook aBook(KeyLedger ledger) {
        DepositBook book = new DepositBook();
        for (IssuedKey key : ledger.out()) {
            if (key.depositCents() != 0) {
                book.charge(key.tag(), key.keyholder(), key.depositCents(), key.issuedOn());
            }
        }
        return book;
    }

    private static List<Sighting> everythingSeen() {
        return List.of(new Sighting("41207", "kmaki"), new Sighting("41208", "dsalo"), new Sighting("41209", "rvirta"));
    }

    private static List<Sighting> everythingSeenBut(String tag) {
        return everythingSeen().stream()
                .filter(sighting -> !sighting.tag().equals(tag))
                .toList();
    }

    private static List<Sighting> everythingSeenAnd(Sighting extra) {
        List<Sighting> seen = new ArrayList<>(everythingSeen());
        seen.add(extra);
        return seen;
    }

    private static List<String> tags(List<AuditFinding> findings) {
        return findings.stream().map(AuditFinding::tag).toList();
    }

    @Test
    void aDepartmentThatProducesEveryKeyIsClean() {
        AuditReport report = Audit.reconcile(aLedger(), "geography", everythingSeen(), WALK);
        assertThat(report.isClean()).isTrue();
        assertThat(report.counted()).isEqualTo(3);
        assertThat(report.expected()).isEqualTo(3);
        assertThat(report.summary()).contains("3 counted");
    }

    @Test
    void aKeyNobodyProducedIsUnaccountedFor() {
        AuditReport report = Audit.reconcile(aLedger(), "geography", everythingSeenBut("41208"), WALK);
        assertThat(tags(report.unaccounted())).containsExactly("41208");
        assertThat(report.unaccounted().get(0).expected()).isEqualTo("dsalo");
        assertThat(report.unaccounted().get(0).describe()).contains("nobody produced it");
    }

    @Test
    void aTagThatIsNotInTheLedgerIsAStray() {
        List<Sighting> seen = everythingSeenAnd(new Sighting("99999", "kmaki"));
        AuditReport report = Audit.reconcile(aLedger(), "geography", seen, WALK);
        List<AuditFinding> findings = report.ofKind(FindingKind.STRAY);
        assertThat(tags(findings)).containsExactly("99999");
        assertThat(findings.get(0).describe()).contains("not in the ledger");
    }

    @Test
    void aKeyTheLedgerClosedIsItsOwnFinding() {
        KeyLedger ledger = aLedger();
        ledger.takeBack("41208", WALK);
        AuditReport report = Audit.reconcile(ledger, "geography", everythingSeen(), WALK);
        List<AuditFinding> findings = report.ofKind(FindingKind.CLOSED);
        assertThat(tags(findings)).containsExactly("41208");
        assertThat(findings.get(0).describe()).contains("on paper");
    }

    @Test
    void aKeyInTheWrongHandsNamesBothPeople() {
        List<Sighting> seen = List.of(
                new Sighting("41207", "dsalo"), new Sighting("41208", "dsalo"), new Sighting("41209", "rvirta"));
        AuditReport report = Audit.reconcile(aLedger(), "geography", seen, WALK);
        List<AuditFinding> findings = report.ofKind(FindingKind.WRONG_HOLDER);
        assertThat(findings.get(0).expected()).isEqualTo("kmaki");
        assertThat(findings.get(0).seen()).isEqualTo("dsalo");
    }

    @Test
    void aKeyFromAnotherDepartmentTurnsUpAsAWrongHolder() {
        List<Sighting> seen = everythingSeenAnd(new Sighting("41301", "kmaki"));
        AuditReport report = Audit.reconcile(aLedger(), "geography", seen, WALK);
        List<AuditFinding> findings = report.ofKind(FindingKind.WRONG_HOLDER);
        assertThat(tags(findings)).containsExactly("41301");
        assertThat(findings.get(0).expected()).isEqualTo("phamm");
    }

    @Test
    void aDepartmentThatProducedNothingIsAllUnaccounted() {
        AuditReport report = Audit.reconcile(aLedger(), "geography", List.of(), WALK);
        assertThat(report.unaccounted()).hasSize(3);
        assertThat(report.counted()).isZero();
    }

    @Test
    void aLostKeyIsNotExpectedOnTheTable() {
        KeyLedger ledger = aLedger();
        ledger.reportLost("41208", WALK);
        AuditReport report = Audit.reconcile(ledger, "geography", everythingSeenBut("41208"), WALK);
        assertThat(report.isClean()).isTrue();
    }

    @Test
    void theDepositBookAndTheLedgerAgreeWhenNothingHasMoved() {
        KeyLedger ledger = aLedger();
        DepositCheck check = Audit.checkDeposits(ledger, aBook(ledger));
        assertThat(check.balances()).isTrue();
        assertThat(check.difference()).isZero();
    }

    @Test
    void aKeyReturnedWithoutARefundShowsUpAsAnOrphan() {
        KeyLedger ledger = aLedger();
        DepositBook book = aBook(ledger);
        ledger.takeBack("41207", WALK);
        DepositCheck check = Audit.checkDeposits(ledger, book);
        assertThat(check.depositsWithoutKeys()).containsExactly("41207");
        assertThat(check.balances()).isFalse();
        assertThat(check.difference()).isEqualTo(-2500);
    }

    @Test
    void aKeyIssuedWithoutADepositLineShowsUpTheOtherWay() {
        KeyLedger ledger = aLedger();
        DepositBook book = aBook(ledger);
        ledger.issue("41210", "AA1", "kmaki", TODAY, 2500);
        DepositCheck check = Audit.checkDeposits(ledger, book);
        assertThat(check.tagsWithoutDeposits()).containsExactly("41210");
        assertThat(check.difference()).isEqualTo(2500);
    }

    @Test
    void aKeyWithNoDepositIsNotExpectedInTheBook() {
        KeyLedger ledger = aLedger();
        DepositBook book = aBook(ledger);
        ledger.issue("41211", "AA1", "kmaki", TODAY);
        DepositCheck check = Audit.checkDeposits(ledger, book);
        assertThat(check.tagsWithoutDeposits()).isEmpty();
        assertThat(check.balances()).isTrue();
    }

    @Test
    void holdingsAreCountedPerDepartmentBiggestFirst() {
        Map<String, Integer> counts = Audit.holdingsByDepartment(aLedger(), List.of("geography", "facilities"));
        assertThat(counts.keySet()).containsExactly("geography", "facilities");
        assertThat(counts).containsEntry("geography", 3);
        assertThat(counts).containsEntry("facilities", 1);
    }

    @Test
    void aDepartmentHoldingNothingStillAppears() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        Map<String, Integer> counts = Audit.holdingsByDepartment(ledger, List.of("geography"));
        assertThat(counts).containsEntry("geography", 0);
    }
}

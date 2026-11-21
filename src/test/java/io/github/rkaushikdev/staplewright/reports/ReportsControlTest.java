package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.Notice;
import io.github.rkaushikdev.staplewright.control.NoticeLog;
import io.github.rkaushikdev.staplewright.control.Notices;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportsControlTest {

    private static final LocalDate DUE = LocalDate.of(2025, 5, 1);
    private static final LocalDate LATE = LocalDate.of(2025, 7, 1);

    private static KeyLedger aLedger(Directory directory) {
        KeyLedger ledger = new KeyLedger(directory);
        ledger.issue("41207", "AA1", "kmaki", TODAY, Optional.of(DUE), 2500);
        ledger.issue("41208", "AA", "kmaki", TODAY, 5000);
        ledger.issue("41209", "AA1", "dsalo", TODAY, 2500);
        return ledger;
    }

    private static DepositBook aBook(KeyLedger ledger) {
        DepositBook book = new DepositBook();
        for (IssuedKey key : ledger.out()) {
            book.charge(key.tag(), key.keyholder(), key.depositCents(), key.issuedOn());
        }
        return book;
    }

    @Test
    void aKeyLineCarriesTheTagTheCutAndTheDates() {
        KeyLedger ledger = aLedger(aDirectory());
        String line = ControlPages.keyLine(ledger.key("41207"));
        assertThat(line).contains("41207");
        assertThat(line).contains("AA1");
        assertThat(line).contains(DUE.toString());
    }

    @Test
    void aKeyWithNoDueDateLeavesTheColumnBlank() {
        KeyLedger ledger = aLedger(aDirectory());
        assertThat(ControlPages.keyLine(ledger.key("41208"))).doesNotContain(DUE.toString());
    }

    @Test
    void aLineShowsTheDepositWhenTheBookIsGiven() {
        KeyLedger ledger = aLedger(aDirectory());
        assertThat(ControlPages.keyLine(ledger.key("41207"), aBook(ledger))).contains("$25.00");
    }

    @Test
    void aStatementListsEveryKeyAndAPlaceToSign() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.keyholderStatement(directory.person("kmaki"), ledger, aBook(ledger));
        assertThat(lines.get(0)).startsWith("Kirsi Maki (kmaki)");
        assertThat(lines).anyMatch(line -> line.contains("41207"));
        assertThat(lines).contains("2 keys");
        assertThat(lines.get(lines.size() - 1)).startsWith("signed");
    }

    @Test
    void aStatementTotalsTheDeposits() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.keyholderStatement(directory.person("kmaki"), ledger, aBook(ledger));
        assertThat(lines).contains("deposits held $75.00");
    }

    @Test
    void aStatementCanBeDated() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.keyholderStatement(directory.person("kmaki"), ledger, TODAY);
        assertThat(lines.get(1)).isEqualTo("as at " + TODAY);
    }

    @Test
    void somebodyHoldingNothingStillGetsAStatement() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.keyholderStatement(directory.person("phamm"), ledger);
        assertThat(lines).contains("0 keys");
    }

    @Test
    void aDepartmentSummaryCountsKeysPerPerson() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.departmentSummary(directory, ledger, "geography", TODAY);
        assertThat(lines.get(0)).startsWith("geography: 4 on the books");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("3 keys out to this department");
    }

    @Test
    void aSummaryFlagsSomebodyWhoMayNotHoldKeys() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        KeyLedger ledger = new KeyLedger(directory);
        List<String> lines = ControlPages.departmentSummary(directory, ledger, "geography", TODAY);
        assertThat(lines).anyMatch(line -> line.contains("[separated]"));
    }

    @Test
    void aLapsedContractorIsFlaggedToo() {
        Directory directory = aDirectory();
        KeyLedger ledger = new KeyLedger(directory);
        List<String> lines = ControlPages.departmentSummary(directory, ledger, "facilities", TODAY);
        assertThat(lines).anyMatch(line -> line.contains("[active]"));
    }

    @Test
    void theChaseListIsEmptyWhenNothingIsLate() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        assertThat(ControlPages.overdueReport(ledger, TODAY).get(0)).startsWith("nothing overdue");
    }

    @Test
    void theChaseListNamesTheDays() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<String> lines = ControlPages.overdueReport(ledger, LATE);
        assertThat(lines.get(0)).startsWith("1 keys overdue");
        assertThat(lines.get(1)).contains("61 days");
    }

    @Test
    void aQuietLettersRunSaysSo() {
        assertThat(ControlPages.noticeRun(List.of()).get(0)).isEqualTo("no letters to send today");
    }

    @Test
    void aLettersRunListsWhatGoesOut() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<Notice> due = Notices.noticesDue(ledger, LATE, new NoticeLog());
        List<String> lines = ControlPages.noticeRun(due);
        assertThat(lines.get(0)).isEqualTo("1 letters");
        assertThat(lines.get(1)).contains("41207");
    }

    @Test
    void aLettersRunNamesTheHolds() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        List<Notice> due = Notices.noticesDue(ledger, LocalDate.of(2025, 12, 1), new NoticeLog());
        assertThat(ControlPages.noticeRun(due)).anyMatch(line -> line.startsWith("holds to place"));
    }

    @Test
    void aDepositStatementShowsEveryLineAndTheBalance() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        DepositBook book = aBook(ledger);
        book.refund("41207", LATE);
        List<String> lines = ControlPages.depositStatement(book, "kmaki");
        assertThat(lines.get(0)).isEqualTo("deposits for kmaki");
        assertThat(lines).anyMatch(line -> line.contains("-$25.00"));
        assertThat(lines.get(lines.size() - 1)).isEqualTo("held $50.00");
    }

    @Test
    void somebodyWithNoDepositsGetsAnEmptyStatement() {
        List<String> lines = ControlPages.depositStatement(new DepositBook(), "phamm");
        assertThat(lines).containsExactly("deposits for phamm", "held $0.00");
    }

    @Test
    void linesJoinIntoAPage() {
        Directory directory = aDirectory();
        KeyLedger ledger = aLedger(directory);
        String page = Layout.asText(ControlPages.keyholderStatement(directory.person("kmaki"), ledger));
        assertThat(page).startsWith("Kirsi Maki");
    }
}

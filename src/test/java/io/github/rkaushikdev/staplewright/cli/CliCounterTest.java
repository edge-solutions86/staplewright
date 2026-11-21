package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.People;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliCounterTest {

    private static final String WHEN = People.TODAY.toString();
    private static final String LATER = "2025-06-02";

    /** A database holding the directory and its signatures, with no counters opened yet. */
    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(session -> {
            Directory directory = People.aDirectory();
            for (Keyholder person : directory) {
                session.add(ControlMappers.keyholderToRow(person));
            }
            session.flush();
            for (Authorizer signer : directory.authorizers()) {
                session.add(ControlMappers.authorizerToRow(signer));
            }
        });
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static void withSeries() {
        call("counter", "series");
    }

    private static String aRequest() {
        Cli.Result found = call("counter", "request", "kmaki", "AA1", "MOO", "moved into 214", "--on", WHEN);
        return found.output().split("\\s+")[0];
    }

    private static String anApprovedRequest() {
        String number = aRequest();
        call("counter", "approve", number, "rvirta", "--on", WHEN);
        return number;
    }

    @Test
    void theSeriesOpenOnce() {
        Cli.Result found = call("counter", "series");
        assertThat(found.output()).contains("series opened");
        assertThat(call("counter", "series").exitCode()).isNotZero();
    }

    @Test
    void aRequestComesBackWithItsNumber() {
        withSeries();
        Cli.Result found = call("counter", "request", "kmaki", "AA1", "MOO", "moved in", "--on", WHEN);
        assertThat(found.output()).startsWith("R-0001 raised");
    }

    @Test
    void aRequestForSomebodyWhoIsNotThereFails() {
        withSeries();
        Cli.Result found = call("counter", "request", "ghost", "AA1", "MOO", "moved in");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void approvingSaysWhoSigned() {
        withSeries();
        String number = aRequest();
        Cli.Result found = call("counter", "approve", number, "rvirta", "--on", WHEN);
        assertThat(found.output().strip()).isEqualTo(number + " approved by rvirta");
    }

    @Test
    void aSignatureThatDoesNotReachFails() {
        withSeries();
        String number = aRequest();
        assertThat(call("counter", "approve", number, "phamm", "--on", WHEN).exitCode())
                .isNotZero();
    }

    @Test
    void denyingSaysSo() {
        withSeries();
        String number = aRequest();
        Cli.Result found = call("counter", "deny", number, "rvirta", "shared room", "--on", WHEN);
        assertThat(found.output().strip()).isEqualTo(number + " denied");
    }

    @Test
    void issuingNamesTheTagAndTheDeposit() {
        withSeries();
        String number = anApprovedRequest();
        Cli.Result found = call("counter", "issue", number, "--on", WHEN);
        assertThat(found.output()).contains("tag 41000 issued to kmaki");
        assertThat(found.output()).contains("deposit 2500");
    }

    @Test
    void aDepositCanBeNamedAtTheCounter() {
        withSeries();
        String number = anApprovedRequest();
        Cli.Result found = call("counter", "issue", number, "--on", WHEN, "--deposit", "0");
        assertThat(found.output()).contains("deposit 0");
    }

    @Test
    void aDueDateCanBeGiven() {
        withSeries();
        String number = anApprovedRequest();
        Cli.Result found = call("counter", "issue", number, "--on", WHEN, "--due", LATER);
        assertThat(found.exitCode()).isZero();
    }

    @Test
    void aKeyComesBack() {
        withSeries();
        String number = anApprovedRequest();
        call("counter", "issue", number, "--on", WHEN);
        Cli.Result found = call("counter", "return", "41000", "--on", LATER);
        assertThat(found.output().strip()).isEqualTo("tag 41000 back");
    }

    @Test
    void aKeyThatIsGoneIsWrittenOff() {
        withSeries();
        String number = anApprovedRequest();
        call("counter", "issue", number, "--on", WHEN);
        Cli.Result found = call("counter", "lost", "41000", "left on a bus", "--on", LATER);
        assertThat(found.output()).contains("written off");
    }

    @Test
    void aStatementPrintsWithAPlaceToSign() {
        withSeries();
        String number = anApprovedRequest();
        call("counter", "issue", number, "--on", WHEN);
        Cli.Result found = call("counter", "statement", "kmaki", "--on", WHEN);
        assertThat(found.output()).contains("Kirsi Maki");
        assertThat(found.output()).contains("signed");
    }

    @Test
    void theDepositStatementPrints() {
        withSeries();
        String number = anApprovedRequest();
        call("counter", "issue", number, "--on", WHEN);
        Cli.Result found = call("counter", "deposits", "kmaki");
        assertThat(found.output()).contains("held $25.00");
    }

    @Test
    void theDepartmentSummaryPrints() {
        Cli.Result found = call("counter", "department", "geography", "--on", WHEN);
        assertThat(found.output()).contains("geography: 4 on the books");
    }

    @Test
    void theChaseListIsQuietWhenNothingIsLate() {
        Cli.Result found = call("counter", "overdue", "--on", WHEN);
        assertThat(found.output()).contains("nothing overdue");
    }

    @Test
    void theLettersRunIsQuietWhenNothingIsLate() {
        Cli.Result found = call("counter", "letters", "--on", WHEN);
        assertThat(found.output()).contains("no letters");
    }

    @Test
    void theLettersRunReportsWhatWouldGoOut() {
        withSeries();
        String number = anApprovedRequest();
        call("counter", "issue", number, "--on", WHEN, "--due", LATER);
        Cli.Result found = call("counter", "letters", "--on", "2025-12-01");
        assertThat(found.output()).contains("letters");
    }
}

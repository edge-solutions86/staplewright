package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliRekeyTest {

    private static final String WHEN = "2025-04-08";

    /** The campus, with neither counter opened yet. */
    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(DbCampus::build);
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static void withSeries() {
        call("rekey", "series");
    }

    private static String reported() {
        Cli.Result found = call("rekey", "report", "41207", "--on", WHEN);
        return found.output().split("\\s+")[0];
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            found++;
        }
        return found;
    }

    @Test
    void theSeriesOpenOnce() {
        Cli.Result found = call("rekey", "series");
        assertThat(found.output()).contains("series opened");
        assertThat(call("rekey", "series").exitCode()).isNotZero();
    }

    @Test
    void reportingGivesANumberAndAGrade() {
        withSeries();
        Cli.Result found = call("rekey", "report", "41207", "--on", WHEN);
        assertThat(found.output()).startsWith("I-0001 logged, graded routine");
    }

    @Test
    void aGrandMasterIsGradedCritical() {
        withSeries();
        Cli.Result found = call("rekey", "report", "41211", "--on", WHEN);
        assertThat(found.output()).contains("critical");
    }

    @Test
    void aTheftWithoutANoteFails() {
        withSeries();
        Cli.Result found = call("rekey", "report", "41207", "--cause", "stolen", "--on", WHEN);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aCauseNobodyHasHeardOfFails() {
        withSeries();
        Cli.Result found = call("rekey", "report", "41207", "--cause", "mislaid");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theCutCanBeKept() {
        withSeries();
        Cli.Result found = call("rekey", "report", "41207", "--keep-cut", "--on", WHEN);
        assertThat(found.exitCode()).isZero();
    }

    @Test
    void theAssessmentPrints() {
        withSeries();
        String number = reported();
        Cli.Result found = call("rekey", "assess", number);
        assertThat(found.output()).startsWith("incident " + number);
        assertThat(found.output()).contains("grade routine");
    }

    @Test
    void thePlanPrintsTheScope() {
        withSeries();
        String number = reported();
        Cli.Result found = call("rekey", "plan", number);
        assertThat(found.output()).contains("scope group");
    }

    @Test
    void thePlanCanBePriced() {
        withSeries();
        String number = reported();
        Cli.Result found = call("rekey", "plan", number, "--cost");
        assertThat(found.output()).contains("total");
        assertThat(found.output()).contains("cents");
    }

    @Test
    void bookingRaisesAnOrder() {
        withSeries();
        String number = reported();
        Cli.Result found = call("rekey", "book", number);
        assertThat(found.output()).contains("W-1041 raised with 2 cylinders");
    }

    @Test
    void anOrderCanBeBookedForADay() {
        withSeries();
        String number = reported();
        Cli.Result found = call("rekey", "book", number, "--for", "2025-04-15");
        assertThat(found.exitCode()).isZero();
    }

    @Test
    void thePacketPrintsASheetACylinder() {
        withSeries();
        String number = reported();
        call("rekey", "book", number);
        Cli.Result found = call("rekey", "packet", "W-1041");
        assertThat(count(found.output(), "cylinder ")).isEqualTo(2);
    }

    @Test
    void aPacketForAnOrderThatIsNotThereFails() {
        withSeries();
        assertThat(call("rekey", "packet", "W-9999").exitCode()).isNotZero();
    }

    @Test
    void theOpenOrdersList() {
        withSeries();
        String number = reported();
        call("rekey", "book", number);
        Cli.Result found = call("rekey", "orders");
        assertThat(found.output()).contains("W-1041");
        assertThat(found.output()).contains("unassigned");
    }

    @Test
    void nothingOpenSaysSo() {
        withSeries();
        assertThat(call("rekey", "orders").output().strip()).isEqualTo("nothing open");
    }

    @Test
    void ordersCanBeFilteredByTechnician() {
        withSeries();
        String number = reported();
        call("rekey", "book", number);
        Cli.Result found = call("rekey", "orders", "--technician", "jkoivu");
        assertThat(found.output().strip()).isEqualTo("nothing open");
    }
}

package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliDoorsTest {

    private static final String WHEN = "2025-05-12";
    private static final String OPENED = "2025-09-01";
    private static final String DUE = "2025-10-01";
    private static final String AT = "2025-04-08T14:12:00";

    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(DbCampus::build);
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static void withSeries() {
        call("doors", "lockout-series");
    }

    @Test
    void theLockoutSeriesOpensOnce() {
        assertThat(call("doors", "lockout-series").output()).contains("series opened");
        assertThat(call("doors", "lockout-series").exitCode()).isNotZero();
    }

    @Test
    void aLockoutIsLogged() {
        withSeries();
        Cli.Result found = call("doors", "lockout", "MOO-214", "kmaki", "--at", AT);
        assertThat(found.output()).contains("L-00001 logged for kmaki at MOO-214");
    }

    @Test
    void aLockoutAboutADoorNobodyHasFails() {
        withSeries();
        assertThat(call("doors", "lockout", "ZZZ-1", "kmaki").exitCode()).isNotZero();
    }

    @Test
    void attendingAndLettingIn() {
        withSeries();
        call("doors", "lockout", "MOO-214", "kmaki", "--at", AT);
        call("doors", "attend", "L-00001", "jkoivu", "--at", "2025-04-08T14:31:00");
        Cli.Result found = call("doors", "let-in", "L-00001", "--at", "2025-04-08T14:35:00");
        assertThat(found.output()).contains("opened, identified by campus card");
    }

    @Test
    void lettingInBeforeAnybodyAttendsFails() {
        withSeries();
        call("doors", "lockout", "MOO-214", "kmaki", "--at", AT);
        assertThat(call("doors", "let-in", "L-00001").exitCode()).isNotZero();
    }

    @Test
    void aSurveyStartsAndTakesLines() {
        call("doors", "survey-start", "MOO", "--on", WHEN);
        Cli.Result found = call("doors", "survey-line", "MOO", "MOO-214", "jkoivu", "--on", WHEN);
        assertThat(found.output()).contains("MOO-214 recorded good");
    }

    @Test
    void aSurveyStartsOnceADay() {
        call("doors", "survey-start", "MOO", "--on", WHEN);
        assertThat(call("doors", "survey-start", "MOO", "--on", WHEN).exitCode())
                .isNotZero();
    }

    @Test
    void aDeficiencyNobodyHasHeardOfIsRefused() {
        call("doors", "survey-start", "MOO", "--on", WHEN);
        Cli.Result found = call("doors", "survey-line", "MOO", "MOO-214", "jkoivu", "--fault", "haunted", "--on", WHEN);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aKnownDeficiencyIsRecorded() {
        call("doors", "survey-start", "MOO", "--on", WHEN);
        call(
                "doors",
                "survey-line",
                "MOO",
                "MOO-214",
                "jkoivu",
                "--condition",
                "failing",
                "--fault",
                "hardware needs replacing before it is rekeyed",
                "--on",
                WHEN);
        Cli.Result found = call("doors", "survey", "MOO", "--on", WHEN);
        assertThat(found.output()).contains("needs replacing");
    }

    @Test
    void theSurveyPrintsWorstFirst() {
        call("doors", "survey-start", "MOO", "--on", WHEN);
        call("doors", "survey-line", "MOO", "MOO-214", "jkoivu", "--on", WHEN);
        call("doors", "survey-line", "MOO", "MOO-216", "jkoivu", "--condition", "broken", "--on", WHEN);
        Cli.Result found = call("doors", "survey", "MOO", "--on", WHEN);
        List<String> lines =
                found.lines().stream().filter(line -> line.startsWith("  ")).toList();
        assertThat(lines.get(0).strip()).startsWith("MOO-216");
    }

    @Test
    void aReviewOpensAndLists() {
        call("doors", "review-open", "geography", DUE, "--on", OPENED);
        Cli.Result found = call("doors", "reviews");
        assertThat(found.output()).contains("geography");
        assertThat(found.output()).contains("3 unanswered");
    }

    @Test
    void aReviewTakesAnAnswer() {
        call("doors", "review-open", "geography", DUE, "--on", OPENED);
        Cli.Result found = call(
                "doors", "review-answer", "geography", "41207", "--answer", "return", "--opened", OPENED, "--on", DUE);
        assertThat(found.output()).contains("41207 asked back");
    }

    @Test
    void anAnswerNobodyHasHeardOfIsRefused() {
        call("doors", "review-open", "geography", DUE, "--on", OPENED);
        Cli.Result found =
                call("doors", "review-answer", "geography", "41207", "--answer", "maybe", "--opened", OPENED);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void nothingOpenSaysSo() {
        assertThat(call("doors", "reviews").output().strip()).isEqualTo("nothing open");
    }

    @Test
    void aKeyedAlikeSetIsMadeAndListed() {
        Cli.Result found = call("doors", "alike", "cupboards", "MOO-214", "MOO-216");
        assertThat(found.output()).contains("keyed alike across 2 doors");
        assertThat(call("doors", "alike-list").output()).contains("MOO-214, MOO-216");
    }

    @Test
    void aDoorAlreadyInASetIsRefused() {
        call("doors", "alike", "cupboards", "MOO-214", "MOO-216");
        assertThat(call("doors", "alike", "stores", "MOO-214", "MOO-218").exitCode())
                .isNotZero();
    }

    @Test
    void noSetsSaysSo() {
        assertThat(call("doors", "alike-list").output().strip()).isEqualTo("no keyed alike sets");
    }
}

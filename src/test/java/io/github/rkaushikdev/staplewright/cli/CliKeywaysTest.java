package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.services.KeywayService;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliKeywaysTest {

    private static final String TODAY = "2025-06-30";

    static KeywayFamily everest() {
        KeywayFamily family =
                new KeywayFamily("everest 29", new KeywaySection("C", "Schlage", true, Optional.of("2031-04-01")));
        family.add(new KeywaySection("CE", "Schlage", true, Optional.of("2027-02-14")), "C");
        family.add(new KeywaySection("CF", "Schlage", true, Optional.of("2023-11-30")), "C");
        family.add(new KeywaySection("CEA", "Schlage"), "CE");
        return family;
    }

    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(session -> {
            DbCampus.build(session);
            new KeywayService(session).store(everest());
        });
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static void keyedOn(String name, String section) {
        TestDatabase.database().inSession(session -> {
            SystemRow first = session.all(SystemRow.class).get(0);
            session.add(new SystemRow(name, first.getChart(), "everest 29", section));
        });
    }

    @Test
    void theFamiliesListCountsTheSections() {
        String output = call("keyways", "families").output();
        assertThat(output).contains("everest 29  4 sections").contains("Sargent LA  6 sections");
    }

    @Test
    void theSectionsComeOutSoonestFirst() {
        List<String> lines =
                call("keyways", "sections", "everest 29", "--on", TODAY).lines();
        assertThat(lines.get(0)).isEqualTo("CF patent ran out 2023-11-30");
        assertThat(lines.get(1)).isEqualTo("CE restricted until 2027-02-14");
    }

    @Test
    void anUnrestrictedSectionSaysSo() {
        assertThat(call("keyways", "sections", "everest 29", "--on", TODAY).output())
                .contains("CEA open keyway");
    }

    @Test
    void aFamilyNobodyStoredIsRefused() {
        assertThat(call("keyways", "sections", "nothing").exitCode()).isNotZero();
    }

    @Test
    void theTreeIsIndentedByDepth() {
        List<String> lines = call("keyways", "tree", "everest 29").lines();
        assertThat(lines.get(0)).isEqualTo("C");
        assertThat(lines).contains("  CE", "    CEA");
    }

    @Test
    void thePatentReportLeadsWithWhatIsGone() {
        List<String> lines =
                call("keyways", "patents", "everest 29", "--on", TODAY).lines();
        assertThat(lines.get(0)).isEqualTo("everest 29 patent position at 2025-06-30");
        assertThat(lines.get(1)).isEqualTo("1 sections are open and were not meant to be");
    }

    @Test
    void theRiskReportIsQuietWhenNothingIsKeyedOnAGoneSection() {
        assertThat(call("keyways", "risk", "--on", TODAY).output()).contains("nothing the shop keys on has gone open");
    }

    @Test
    void theRiskReportNamesTheSystemSittingOnIt() {
        keyedOn("voss hall", "CF");
        String output = call("keyways", "risk", "--on", TODAY).output();
        assertThat(output).contains("1 sections in use have gone open").contains("carrying voss hall");
    }

    @Test
    void checkingASectionWithYearsLeftPasses() {
        assertThat(call("keyways", "check", "everest 29", "C", "--on", TODAY)
                        .output()
                        .strip())
                .isEqualTo("C is good for 5 years");
    }

    @Test
    void checkingASectionThatGoesFirstIsRefused() {
        assertThat(call("keyways", "check", "everest 29", "CE", "--on", TODAY).exitCode())
                .isNotZero();
    }

    @Test
    void aShorterHorizonLetsItThrough() {
        assertThat(call("keyways", "check", "everest 29", "CE", "--on", TODAY, "--years", "1")
                        .output()
                        .strip())
                .isEqualTo("CE is good for 1 years");
    }

    @Test
    void aHorizonThatRunsBackwardsIsRejectedAsUnreadable() {
        assertThat(call("keyways", "check", "everest 29", "C", "--years", "-1").exitCode())
                .isNotZero();
    }

    @Test
    void nothingKeyedOnASectionSaysSo() {
        assertThat(call("keyways", "carrying", "everest 29", "CF").output().strip())
                .isEqualTo("nothing is keyed on CF");
    }

    @Test
    void theSystemsOnASectionAreListed() {
        keyedOn("voss hall", "CF");
        assertThat(call("keyways", "carrying", "everest 29", "CF").output().strip())
                .isEqualTo("voss hall");
    }

    @Test
    void theSectionsDefaultToTodayWhenNobodySays() {
        Cli.Result found = call("keyways", "sections", "everest 29");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("CF");
    }
}

package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class CliTest {

    /** A database holding a chart and the campus family, so a system can be opened on it. */
    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(DbCampus::chartAndFamily);
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static Cli.Result opened() {
        return call("system", "open", "chester park", "1", "Sargent LA", "L");
    }

    private static List<String> names(List<Class<?>> commands) {
        return commands.stream()
                .map(kind -> kind.getAnnotation(CommandLine.Command.class).name())
                .toList();
    }

    @Test
    void theSettingsPrint() {
        Cli.Result found = call("settings");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("deposits:");
    }

    @Test
    void theTablesPrint() {
        assertThat(call("tables").output()).contains("keying_systems");
    }

    @Test
    void theSchemaCanBeBuilt() {
        Cli.Result found = call("build");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("schema built");
    }

    @Test
    void openingASystemSaysSo() {
        Cli.Result found = opened();
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("chester park opened");
    }

    @Test
    void aSystemCannotBeOpenedTwice() {
        opened();
        assertThat(opened().exitCode()).isNotZero();
    }

    @Test
    void drawingASymbolSaysWhereItLanded() {
        opened();
        assertThat(call("system", "draw", "chester park", "A", "--section", "LA")
                        .output())
                .contains("A drawn on LA");
    }

    @Test
    void aSymbolInheritsTheSectionAboveIt() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        assertThat(call("system", "draw", "chester park", "AA").output()).contains("AA drawn on LA");
    }

    @Test
    void cuttingASymbolSaysSo() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        assertThat(call("system", "cut", "chester park", "A", "642446").output().strip())
                .isEqualTo("A cut 642446");
    }

    @Test
    void aCutTheRulesRefuseFails() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        assertThat(call("system", "cut", "chester park", "A", "192156").exitCode())
                .isNotZero();
    }

    @Test
    void uncuttingPutsItBackOnTheOpenList() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        call("system", "cut", "chester park", "A", "642446");
        assertThat(call("system", "uncut", "chester park", "A").output()).contains("open list");
    }

    @Test
    void theSchedulePrints() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        assertThat(call("system", "schedule", "chester park").output())
                .contains("chester park")
                .contains("GGM");
    }

    @Test
    void theScheduleCanBeSortedByCut() {
        opened();
        call("system", "draw", "chester park", "A", "--section", "LA");
        call("system", "cut", "chester park", "A", "642446");
        assertThat(call("system", "schedule", "chester park", "--bittings")
                        .lines()
                        .get(0))
                .startsWith("cut");
    }

    @Test
    void theTreePrintsOnItsOwn() {
        opened();
        assertThat(call("system", "schedule", "chester park", "--tree").output())
                .contains("GGM")
                .contains("leaf");
    }

    @Test
    void theOpenSymbolsPrint() {
        opened();
        assertThat(call("system", "open-symbols", "chester park").output()).contains("1 symbols drawn and not cut");
    }

    @Test
    void theReviewReportsNothingOnAFinishedSystem() {
        opened();
        call("system", "cut", "chester park", "GGM", "442446");
        assertThat(call("system", "review", "chester park").output().strip()).isEqualTo("nothing to report");
    }

    @Test
    void theReviewListsFindings() {
        opened();
        assertThat(call("system", "review", "chester park").output()).contains("uncut");
    }

    @Test
    void archivingSaysSo() {
        opened();
        assertThat(call("system", "archive", "chester park").output()).contains("archived");
    }

    @Test
    void aSystemThatIsNotThereFails() {
        assertThat(call("system", "schedule", "nothing at all").exitCode()).isNotZero();
    }

    @Test
    void theEntryPointReturnsZeroOnSuccess() {
        assertThat(call("settings").exitCode()).isZero();
    }

    @Test
    void theEntryPointReturnsTwoOnABadCommand() {
        assertThat(call("nonsense").exitCode()).isEqualTo(2);
    }

    @Test
    void theSelfCheckPrintsAReport() {
        assertThat(call("check", "--on", "2025-06-30").lines().get(0)).isEqualTo("self check at 2025-06-30");
    }

    @Test
    void theSelfCheckCanBeToldToSayNothing() {
        assertThat(call("check", "--on", "2025-06-30", "--quiet").output()).isEmpty();
    }

    @Test
    void theSelfCheckDefaultsToToday() {
        assertThat(call("check").exitCode()).isIn(0, 1);
    }

    @Test
    void aFaultMakesTheSelfCheckExitOne() {
        TestDatabase.database().inSession(session -> {
            KeyholderRow person = new KeyholderRow("jsalo", "j salo", "staff", "grounds");
            person.setEndsOn(LocalDate.of(2024, 1, 1));
            session.add(person);
            session.flush();
            IssuedKeyRow key = new IssuedKeyRow("41207", "AA1", "jsalo", LocalDate.of(2023, 9, 1));
            key.setDueOn(LocalDate.of(2024, 6, 1));
            key.setState("out");
            session.add(key);
        });
        Cli.Result found = call("check", "--on", "2025-06-30");
        assertThat(found.exitCode()).isEqualTo(1);
        assertThat(found.output()).contains("keys out to lapsed holders");
    }

    @Test
    void aShopWithNothingInItAnswersForNothing() {
        Cli.Result found = call("check", "--on", "2025-06-30");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("nothing to answer for");
    }

    @Test
    void everyGroupInTheListIsAttached() {
        CommandLine root = Main.commandLine(new Main(io.github.rkaushikdev.staplewright.cli.Terminal.system()));
        assertThat(Main.GROUPS).hasSize(15);
        assertThat(root.getSubcommands().keySet()).containsAll(names(Main.GROUPS));
    }

    @Test
    void everyServiceCommandIsAttached() {
        CommandLine root = Main.commandLine(new Main(io.github.rkaushikdev.staplewright.cli.Terminal.system()));
        assertThat(root.getSubcommands().keySet()).containsAll(names(Main.COMMANDS));
    }

    @Test
    void noGroupIsListedTwice() {
        assertThat(new HashSet<>(names(Main.GROUPS))).hasSize(Main.GROUPS.size());
    }

    @Test
    void theGroupsAreInNameOrder() {
        assertThat(names(Main.GROUPS)).isSorted();
    }

    @Test
    void theHelpListsEveryGroup() {
        String output = call("--help").output();
        for (String group : names(Main.GROUPS)) {
            assertThat(output).contains(group);
        }
    }

    @Test
    void theVersionOptionPrintsTheVersion() {
        assertThat(call("--version").output()).contains(Staplewright.VERSION);
    }
}

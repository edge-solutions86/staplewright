package io.github.rkaushikdev.staplewright.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.support.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ReleaseTest {

    private static final Pattern MIGRATION = Pattern.compile("V(\\d+)__\\w+\\.sql");

    @Test
    void thePackageVersionIsTheLatestRelease() {
        assertThat(Releases.matchesPackage(Staplewright.VERSION)).isTrue();
        assertThat(Releases.CURRENT.version()).isEqualTo(Staplewright.VERSION);
    }

    @Test
    void theReleasesAreOldestFirst() {
        assertThat(Releases.RELEASES).isSortedAccordingTo(Release.BY_VERSION);
    }

    @Test
    void noVersionIsReleasedTwice() {
        assertThat(new HashSet<>(Releases.versions()))
                .hasSize(Releases.versions().size());
    }

    @Test
    void theDatesOnlyMoveForward() {
        List<LocalDate> days =
                Releases.RELEASES.stream().map(Release::releasedOn).toList();
        assertThat(days).isSorted();
    }

    @Test
    void everyMigrationIsShippedExactlyOnce() {
        List<String> found = Releases.everyMigration();
        assertThat(new HashSet<>(found)).hasSize(found.size());
    }

    @Test
    void theMigrationsAreShippedInOrder() {
        assertThat(Releases.everyMigration()).isSorted();
    }

    @Test
    void everyMigrationOnDiskIsInARelease() throws IOException {
        Path root = Path.of("src/main/resources/db/migration");
        Set<String> onDisk;
        try (Stream<Path> files = Files.list(root)) {
            onDisk = files.filter(Files::isRegularFile)
                    .map(path -> MIGRATION.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(match -> String.format(Locale.ROOT, "%04d", Integer.parseInt(match.group(1))))
                    .collect(Collectors.toSet());
        }
        assertThat(onDisk).isNotEmpty().isEqualTo(Set.copyOf(Releases.everyMigration()));
    }

    @Test
    void aReleaseReadsBackAsOneLine() {
        String line = Releases.release("1.0.0").describe();
        assertThat(line).startsWith("1.0.0").contains("the interface is settled");
    }

    @Test
    void aVersionNobodyReleasedIsA404() {
        assertThatThrownBy(() -> Releases.release("0.0.1")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void anUpgradeCrossesTheMigrationsInBetween() {
        assertThat(Releases.migrationsBetween("0.6.0", "0.8.0"))
                .containsExactly("0005", "0006", "0007", "0008", "0009");
    }

    @Test
    void anUpgradeToTheSameVersionCrossesNothing() {
        assertThat(Releases.migrationsBetween("0.8.0", "0.8.0")).isEmpty();
    }

    @Test
    void aDowngradeIsRefused() {
        assertThatThrownBy(() -> Releases.migrationsBetween("0.8.0", "0.6.0")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theNotesComeWithTheVersionThatCarriesThem() {
        assertThat(Releases.notesBetween("0.6.0", "0.7.0").get(0)).startsWith("0.7.0: ");
    }

    @Test
    void anUpgradeWithNothingToReadSaysNothing() {
        assertThat(Releases.notesBetween("0.2.0", "0.3.0")).isEmpty();
    }

    @Test
    void theHistoryCountsTheReleases() {
        List<String> lines = Releases.historySheet();
        assertThat(lines.get(0))
                .isEqualTo(Releases.RELEASES.size() + " releases, " + Releases.CURRENT.version() + " is current");
        assertThat(lines).hasSize(Releases.RELEASES.size() + 1);
    }

    @Test
    void theUpgradeSheetNamesTheMigrations() {
        List<String> lines = Releases.upgradeSheet("0.6.0", "0.7.0");
        assertThat(lines.get(0)).isEqualTo("0.6.0 to 0.7.0");
        assertThat(lines.get(1)).isEqualTo("2 migrations: 0005, 0006");
    }

    @Test
    void theUpgradeSheetDefaultsToCurrent() {
        assertThat(Releases.upgradeSheet("0.9.0").get(0)).isEqualTo("0.9.0 to " + Releases.CURRENT.version());
    }

    @Test
    void anUpgradeWithNoMigrationsSaysSo() {
        assertThat(Releases.upgradeSheet("0.9.0", "1.0.0").get(1)).isEqualTo("no migrations to run");
    }

    @Test
    void theUpgradeSheetCarriesTheNotes() {
        assertThat(Releases.upgradeSheet("0.6.0", "0.7.0")).contains("read first");
    }

    @Test
    void whatAVersionIsBehindBy() {
        assertThat(Releases.unreleased("0.9.0")).containsExactly("1.0.0");
    }

    @Test
    void theLatestVersionIsBehindNothing() {
        assertThat(Releases.unreleased(Releases.CURRENT.version())).isEmpty();
    }

    @Test
    void theCurrentReleaseDescribesItself() {
        List<String> lines = Releases.describeCurrent();
        assertThat(lines.get(0)).startsWith(Releases.CURRENT.version());
        assertThat(lines).hasSize(Releases.CURRENT.notes().size() + 1);
    }

    private static Cli.Result call(String... args) {
        java.util.List<String> all = new java.util.ArrayList<>(List.of("--database", "jdbc:h2:mem:release-commands"));
        all.addAll(List.of(args));
        return Cli.call(all.toArray(String[]::new));
    }

    @Test
    void theVersionCommandPrintsTheHeadline() {
        assertThat(call("release", "version").lines().get(0)).startsWith(Staplewright.VERSION);
    }

    @Test
    void theHistoryCommandPrintsEveryRelease() {
        assertThat(call("release", "history").lines()).hasSize(Releases.RELEASES.size() + 1);
    }

    @Test
    void theUpgradeCommandNamesTheMigrations() {
        assertThat(call("release", "upgrade", "0.6.0", "--to", "0.7.0").output())
                .contains("2 migrations: 0005, 0006");
    }

    @Test
    void theUpgradeCommandDefaultsToCurrent() {
        assertThat(call("release", "upgrade", "0.9.0").output()).contains("to " + Releases.CURRENT.version());
    }

    @Test
    void theUpgradeCommandRefusesADowngrade() {
        assertThat(call("release", "upgrade", "1.0.0", "--to", "0.6.0").exitCode())
                .isNotZero();
    }

    @Test
    void theMigrationsCommandListsThemAll() {
        assertThat(call("release", "migrations").lines()).isEqualTo(Releases.everyMigration());
    }
}

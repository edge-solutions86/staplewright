package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.health.Finding;
import io.github.rkaushikdev.staplewright.health.Health;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.services.EscrowService;
import io.github.rkaushikdev.staplewright.services.StockService;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private Session seeded() {
        Seed.seed(session, TODAY);
        return session;
    }

    /** A database file of the test's own, which a second command finds as the first one left it. */
    private static String demoDatabase(Path folder) {
        return "jdbc:h2:" + folder.resolve("demo").toAbsolutePath();
    }

    @Test
    void theDemoFamilyHasASectionWhosePatentRanOut() {
        assertThat(Seed.demoFamily().section("LD").patentExpires()).contains("2023-11-30");
    }

    @Test
    void theDemoFamilyHasOneThatHasNot() {
        assertThat(Seed.demoFamily().section("LC").patentExpires()).contains("2031-04-01");
    }

    @Test
    void seedingHandsBackTheSystem() {
        assertThat(Seed.seed(session, TODAY).getName()).isEqualTo("chester park");
    }

    @Test
    void theSystemIsStampedWithTheDayItOpened() {
        Session seeded = seeded();
        assertThat(seeded.get(SystemRow.class, 1).orElseThrow().getOpenedOn()).contains(LocalDate.of(2024, 8, 19));
    }

    @Test
    void seedingTwiceIsRefused() {
        Session seeded = seeded();
        assertThatThrownBy(() -> Seed.seed(seeded, TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void theSectionsComeOutAsATree() {
        List<KeywaySectionRow> rows = seeded().all(KeywaySectionRow.class);
        assertThat(rows).hasSize(6);
        List<String> roots = rows.stream()
                .filter(row -> row.getParentId().isEmpty())
                .map(KeywaySectionRow::getCode)
                .toList();
        assertThat(roots).containsExactly("L");
    }

    @Test
    void thePatentDatesSurviveTheSeed() {
        List<KeywaySectionRow> found =
                seeded().list("select s from KeywaySectionRow s where s.code = ?1", KeywaySectionRow.class, "LD");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getPatentExpires()).contains(LocalDate.of(2023, 11, 30));
    }

    @Test
    void everySymbolIsCut() {
        List<ScheduleEntryRow> rows = seeded().all(ScheduleEntryRow.class);
        assertThat(rows).hasSize(10);
        assertThat(rows).allMatch(row -> row.getBitting().isPresent());
    }

    @Test
    void theKeyingGroupsHaveMembers() {
        List<GroupRow> rows = seeded().all(GroupRow.class);
        assertThat(rows.stream().map(GroupRow::getName).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("moore offices", "moore entrance", "voss offices", "shops");
        assertThat(rows).allMatch(row -> !row.getMembers().isEmpty());
    }

    @Test
    void theOpeningsAreWritten() {
        assertThat(seeded().all(OpeningRow.class)).hasSize(11);
    }

    @Test
    void aCylinderIsLeftOnTheShelf() {
        List<CylinderRow> rows = seeded().all(CylinderRow.class);
        assertThat(rows.stream()
                        .filter(row -> row.getInstalledAt().isEmpty())
                        .map(CylinderRow::getSerial)
                        .toList())
                .containsExactly("C-1007");
    }

    @Test
    void theKeysAreOutToPeople() {
        List<IssuedKeyRow> rows = seeded().all(IssuedKeyRow.class);
        assertThat(rows).hasSize(6);
        assertThat(rows).allMatch(row -> row.getState().equals("out"));
    }

    @Test
    void oneKeyIsOutToSomebodyWhoseAppointmentEnded() {
        List<Finding> found = Health.keysHeldByLapsedPeople(seeded(), TODAY);
        assertThat(found.stream().map(Finding::subject).toList()).containsExactly("41212");
    }

    @Test
    void theBlanksAreStocked() {
        List<BlankRow> rows = seeded().all(BlankRow.class);
        assertThat(rows.stream().map(BlankRow::getPartNumber).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("1145A", "RA4");
    }

    @Test
    void theStocktakeCameUpShort() {
        StockService service = new StockService(seeded());
        List<StockLineRow> lines = service.movements("1145A");
        assertThat(lines.stream().map(StockLineRow::getMovement).toList())
                .containsExactly("received", "cut", "counted");
        assertThat(lines.get(lines.size() - 1).getAdjustment()).isEqualTo(Optional.of(-12));
    }

    @Test
    void theDrawerMatchesWhatWasCounted() {
        assertThat(new StockService(seeded()).onHand("1145A")).isEqualTo(182);
    }

    @Test
    void twoBoxesAreOnTheWall() {
        List<KeyBoxRow> rows = seeded().all(KeyBoxRow.class);
        assertThat(rows.stream().map(KeyBoxRow::getNumber).toList()).containsExactly("KB-01", "KB-02");
    }

    @Test
    void oneBoxIsAlreadyDueACheck() {
        List<KeyBox> found = new EscrowService(seeded()).dueForCheck(TODAY);
        assertThat(found.stream().map(KeyBox::number).toList()).containsExactly("KB-01");
    }

    @Test
    void theSelfCheckFindsSomething() {
        assertThat(Health.runChecks(seeded(), TODAY)).isNotEmpty();
    }

    @Test
    void theSummarySaysWhatIsInIt() {
        List<String> lines = Seed.summary();
        assertThat(lines.get(0)).startsWith("3 buildings");
        assertThat(lines.get(lines.size() - 1)).endsWith("on purpose");
    }

    @Test
    void theDemoCommandBuildsAndFills(@TempDir Path folder) {
        Cli.Result found = Cli.call("--database", demoDatabase(folder), "demo", "--on", "2025-06-30");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("demo campus written to");
        assertThat(found.output()).contains("3 buildings");
    }

    @Test
    void theDemoCommandRefusesADatabaseThatHasOne(@TempDir Path folder) {
        String url = demoDatabase(folder);
        Cli.call("--database", url, "demo", "--on", "2025-06-30");
        Cli.Result again = Cli.call("--database", url, "demo");
        assertThat(again.exitCode()).isNotZero();
    }

    @Test
    void theSelfCheckRunsOnTheDemo(@TempDir Path folder) {
        String url = demoDatabase(folder);
        Cli.call("--database", url, "demo", "--on", "2025-06-30");
        Cli.Result found = Cli.call("--database", url, "check", "--on", "2025-06-30");
        assertThat(found.output()).startsWith("self check at 2025-06-30");
        assertThat(found.exitCode()).isEqualTo(1);
    }
}

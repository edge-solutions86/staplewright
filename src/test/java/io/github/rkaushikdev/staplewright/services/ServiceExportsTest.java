package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.reports.Row;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceExportsTest {

    private static final String SYSTEM = "chester park";
    private static final ObjectMapper JSON = new ObjectMapper();

    private Session session;

    /** The campus, written and committed, so the commands see it as well as the service. */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        session = TestDatabase.database().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ExportService aService() {
        return new ExportService(session);
    }

    private static List<Map<String, Object>> parsed(String text) throws IOException {
        return JSON.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    @Test
    void theNamesAreTheTablesThatExist() {
        assertThat(aService().names()).containsExactly("cylinders", "holdings", "openings", "schedule");
    }

    @Test
    void theOpeningsExportCarriesEveryDoor() {
        List<Row> rows = aService().rowsFor("openings").rows();
        assertThat(rows).hasSize(11);
    }

    @Test
    void theCylindersExportCarriesTheRegister() {
        List<Row> rows = aService().rowsFor("cylinders").rows();
        assertThat(rows.stream().map(row -> row.cell(0).orElseThrow()).limit(2).toList())
                .containsExactly("C-1001", "C-1002");
    }

    @Test
    void theHoldingsExportOnlyHasWhatIsOut() {
        List<Row> rows = aService().rowsFor("holdings").rows();
        assertThat(rows).allSatisfy(row -> assertThat(row.cell(row.size() - 1)).contains("out"));
    }

    @Test
    void theScheduleExportNeedsASystem() {
        assertThatThrownBy(() -> aService().rowsFor("schedule")).isInstanceOf(InputError.class);
    }

    @Test
    void aSystemNobodyOpenedIsA404() {
        assertThatThrownBy(() -> aService().rowsFor("schedule", "nowhere")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theScheduleExportCarriesTheCuts() {
        List<Row> rows = aService().rowsFor("schedule", SYSTEM).rows();
        assertThat(rows.get(0).cell(0)).contains("GGM");
        assertThat(rows.get(0).cell(3)).contains("442446");
    }

    @Test
    void aTableNobodyExportsIsA404() {
        assertThatThrownBy(() -> aService().rowsFor("payroll")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theCsvHasAHeaderFirst() {
        String text = aService().text("openings");
        assertThat(text.lines().findFirst().orElseThrow()).startsWith("number,building");
    }

    @Test
    void theJsonIsAListOfObjects() throws IOException {
        List<Map<String, Object>> found = parsed(aService().text("openings", "json"));
        assertThat((String) found.get(0).get("number")).isNotEmpty();
        // The compiler already holds the answer to whether this is a list; what is left is that it parsed as one.
        assertThat(found).isInstanceOf(List.class);
    }

    @Test
    void aFormatNobodyWritesIsRefused() {
        assertThatThrownBy(() -> aService().text("openings", "xml")).isInstanceOf(InputError.class);
    }

    @Test
    void theRecordsComeBackKeyedByColumn() {
        List<Map<String, Optional<Object>>> found = aService().records("cylinders");
        assertThat(found.get(0).keySet())
                .containsExactlyInAnyOrder("serial", "format", "section", "chambers", "opening", "position", "group");
    }

    @Test
    void theFilenameCarriesTheDayAndTheFormat() {
        ExportService service = aService();
        assertThat(service.filename("openings", LocalDate.of(2025, 4, 8)))
                .isEqualTo("staplewright-openings-2025-04-08.csv");
        assertThat(service.filename("openings", LocalDate.of(2025, 4, 8), "json"))
                .endsWith(".json");
    }

    @Test
    void theBulkExportLeavesTheScheduleOut() {
        Map<String, String> found = aService().everyTable(LocalDate.of(2025, 4, 8));
        assertThat(found).hasSize(3);
        assertThat(found.keySet()).noneMatch(name -> name.contains("schedule"));
    }

    @Test
    void theBulkExportIsKeyedByFilename() {
        Map<String, String> found = aService().everyTable(LocalDate.of(2025, 4, 8));
        assertThat(found).containsKey("staplewright-openings-2025-04-08.csv");
    }

    @Test
    void theLiveSystemsAreListed() {
        assertThat(aService().systemsToExport()).containsExactly(SYSTEM);
    }

    @Test
    void theListCommandNamesTheTables() {
        assertThat(call("export", "list").lines()).containsExactly("cylinders", "holdings", "openings", "schedule");
    }

    @Test
    void oneTableGoesToStandardOutput() {
        List<String> lines = call("export", "table", "openings").lines();
        assertThat(lines.get(0)).startsWith("number,building");
        assertThat(lines).hasSize(12);
    }

    @Test
    void aTableCanComeOutAsJson() throws IOException {
        List<Map<String, Object>> found =
                parsed(call("export", "table", "openings", "--as", "json").output());
        assertThat((String) found.get(0).get("building")).isNotEmpty();
    }

    @Test
    void aFormatNobodyWritesIsRejectedAsUnreadable() {
        assertThat(call("export", "table", "openings", "--as", "xml").exitCode())
                .isNotZero();
    }

    @Test
    void aScheduleNeedsASystemNamed() {
        assertThat(call("export", "table", "schedule").exitCode()).isNotZero();
    }

    @Test
    void aScheduleWithASystemComesOut() {
        List<String> lines =
                call("export", "table", "schedule", "--system", SYSTEM).lines();
        assertThat(lines.get(1)).startsWith("GGM");
    }

    @Test
    void theColumnsCommandNamesThem() {
        assertThat(call("export", "columns", "cylinders").lines().get(0)).isEqualTo("serial");
    }

    @Test
    void theScheduleCommandWritesAFile(@TempDir Path folder) {
        Path target = folder.resolve("schedule.csv");
        Cli.Result found = call("export", "schedule", SYSTEM, "--to", target.toString());
        assertThat(target).exists();
        assertThat(found.output()).contains(target.toString());
    }

    @Test
    void theScheduleCommandPrintsWhenThereIsNoFile() {
        String output = call("export", "schedule", SYSTEM).output();
        assertThat(output.lines().findFirst().orElseThrow()).startsWith("symbol,");
    }

    @Test
    void theBulkExportDropsFilesInAFolder(@TempDir Path tmp) {
        Path folder = tmp.resolve("drop");
        Cli.Result found = call("export", "all", folder.toString(), "--on", "2025-04-08");
        assertThat(folder.resolve("staplewright-openings-2025-04-08.csv")).exists();
        assertThat(found.lines()).hasSize(3);
    }

    @Test
    void theBulkExportCanWriteJson(@TempDir Path tmp) {
        Path folder = tmp.resolve("drop");
        call("export", "all", folder.toString(), "--as", "json", "--on", "2025-04-08");
        assertThat(Files.exists(folder.resolve("staplewright-holdings-2025-04-08.json")))
                .isTrue();
    }

    @Test
    void theNameCommandSaysWhatAFileWouldBeCalled() {
        Cli.Result found = call("export", "name", "cylinders", "--on", "2025-04-08");
        assertThat(found.output().strip()).isEqualTo("staplewright-cylinders-2025-04-08.csv");
    }

    @Test
    void aTableNobodyExportsIsRefusedFromTheCommandLine() {
        assertThat(call("export", "table", "payroll").exitCode()).isNotZero();
    }
}

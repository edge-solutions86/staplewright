package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.reports.Imports;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceImportsTest {

    private static final String BUILDINGS = Imports.asCsv(List.of(
            List.of("code", "name", "built"),
            List.of("MOO", "Moore Hall", "1953"),
            List.of("VOS", "Voss Hall", "1971")));

    private static final String OPENINGS = Imports.asCsv(List.of(
            List.of("number", "building", "floor", "room", "function"),
            List.of("MOO-101", "MOO", "1", "101", "entrance"),
            List.of("MOO-110", "MOO", "1", "110", "classroom")));

    private static final String PEOPLE = Imports.asCsv(List.of(
            List.of("identifier", "name", "affiliation", "department"),
            List.of("kmaki", "K Maki", "staff", "facilities"),
            List.of("rvirta", "R Virta", "faculty", "geography")));

    private Session session;

    /** An empty database, the schema and nothing in it, for the service and the commands alike. */
    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ImportService aService() {
        return new ImportService(session);
    }

    private ImportService loadedBuildings() {
        ImportService service = aService();
        service.loadBuildings(BUILDINGS);
        return service;
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static String aFile(Path folder, String name, String text) throws IOException {
        Path path = folder.resolve(name);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path.toString();
    }

    @Test
    void aDryRunWritesNothing() {
        ImportService service = aService();
        Load found = service.loadBuildings(BUILDINGS, true);
        assertThat(found.written()).isEqualTo(2);
        assertThat(found.dryRun()).isTrue();
        assertThat(session.all(BuildingRow.class)).isEmpty();
    }

    @Test
    void aWritePutsTheRowsIn() {
        loadedBuildings();
        List<BuildingRow> rows = session.all(BuildingRow.class);
        assertThat(rows.stream().map(BuildingRow::getCode).toList()).containsExactly("MOO", "VOS");
    }

    @Test
    void aBuildingAlreadyThereIsAProblem() {
        ImportService service = loadedBuildings();
        Load found = service.loadBuildings(BUILDINGS);
        assertThat(found.isClean()).isFalse();
        assertThat(found.problems().get(0).message()).contains("already there");
    }

    @Test
    void aMissingColumnIsNamed() {
        Load found = aService().loadBuildings(Imports.asCsv(List.of(List.of("code"), List.of("MOO"))));
        assertThat(found.problems().get(0).column()).isEqualTo("name");
    }

    @Test
    void theOpeningsLoadOntoTheirBuildings() {
        ImportService service = loadedBuildings();
        Load found = service.loadOpenings(OPENINGS);
        assertThat(found.written()).isEqualTo(2);
        assertThat(session.all(OpeningRow.class)).hasSize(2);
    }

    @Test
    void anOpeningOnABuildingNobodyHasIsAProblem() {
        Load found = aService().loadOpenings(OPENINGS);
        assertThat(found.isClean()).isFalse();
        assertThat(found.problems().get(0).message()).contains("no such building");
    }

    @Test
    void anOpeningAlreadyThereIsAProblem() {
        ImportService service = loadedBuildings();
        service.loadOpenings(OPENINGS);
        Load found = service.loadOpenings(OPENINGS);
        assertThat(found.problems().get(0).message()).contains("already there");
    }

    @Test
    void aBadFunctionStopsTheWholeFile() {
        ImportService service = loadedBuildings();
        String text = Imports.asCsv(List.of(
                List.of("number", "building", "floor", "room", "function"),
                List.of("MOO-101", "MOO", "1", "101", "entrance"),
                List.of("MOO-110", "MOO", "1", "110", "hinged")));
        Load found = service.loadOpenings(text);
        assertThat(found.written()).isZero();
        assertThat(session.all(OpeningRow.class)).isEmpty();
    }

    @Test
    void theKeyholdersLoad() {
        Load found = aService().loadKeyholders(PEOPLE);
        assertThat(found.written()).isEqualTo(2);
        assertThat(session.all(KeyholderRow.class)).hasSize(2);
    }

    @Test
    void aKeyholderAlreadyThereIsAProblem() {
        ImportService service = aService();
        service.loadKeyholders(PEOPLE);
        assertThat(service.loadKeyholders(PEOPLE).isClean()).isFalse();
    }

    @Test
    void anAffiliationNobodyRecognisesIsAProblem() {
        String text = Imports.asCsv(List.of(
                List.of("identifier", "name", "affiliation", "department"),
                List.of("kmaki", "K Maki", "wizard", "facilities")));
        Load found = aService().loadKeyholders(text);
        assertThat(found.written()).isZero();
        assertThat(found.isClean()).isFalse();
    }

    @Test
    void aTemporaryPersonCanCarryAnEndDate() {
        String text = Imports.asCsv(List.of(
                List.of("identifier", "name", "affiliation", "department", "ends_on"),
                List.of("jwex", "J Wexler", "contractor", "facilities", "2026-01-31")));
        assertThat(aService().loadKeyholders(text).written()).isEqualTo(1);
    }

    @Test
    void aKindNobodyLoadsIsRefused() {
        assertThatThrownBy(() -> aService().load("payroll", BUILDINGS)).isInstanceOf(InputError.class);
    }

    @Test
    void theKindsAreListed() {
        assertThat(aService().kinds()).containsExactly("buildings", "keyholders", "openings");
    }

    @Test
    void theColumnsForAKindComeBack() {
        assertThat(aService().columnsFor("keyholders").get(0)).isEqualTo("identifier");
    }

    @Test
    void theColumnsForAKindNobodyLoadsIsRefused() {
        assertThatThrownBy(() -> aService().columnsFor("payroll")).isInstanceOf(InputError.class);
    }

    @Test
    void aCleanLoadReadsBackAsOneLine() {
        Load found = aService().loadBuildings(BUILDINGS, true);
        assertThat(found.describe()).containsExactly("buildings: 2 rows would load");
    }

    @Test
    void aDirtyLoadListsTheProblems() {
        Load found = aService().loadOpenings(OPENINGS);
        List<String> lines = found.describe();
        assertThat(lines.get(1)).endsWith("problems");
    }

    @Test
    void theKindsCommandNamesTheColumns() {
        List<String> lines = call("import", "kinds").lines();
        assertThat(lines.get(0)).startsWith("buildings");
        assertThat(lines.get(0)).contains("code, name");
    }

    @Test
    void theColumnsCommandListsThem() {
        assertThat(call("import", "columns", "keyholders").lines().get(0)).isEqualTo("identifier");
    }

    @Test
    void aLoadDryRunsByDefault(@TempDir Path folder) throws IOException {
        String path = aFile(folder, "b.csv", BUILDINGS);
        Cli.Result found = call("import", "load", "buildings", path);
        assertThat(found.output()).contains("would load");
        Cli.Result again = call("import", "load", "buildings", path);
        assertThat(again.output()).contains("would load");
    }

    @Test
    void aLoadWithWritePutsThemIn(@TempDir Path folder) throws IOException {
        String path = aFile(folder, "b.csv", BUILDINGS);
        Cli.Result found = call("import", "load", "buildings", path, "--write");
        assertThat(found.output().strip()).isEqualTo("buildings: 2 rows");
        Cli.Result again = call("import", "load", "buildings", path, "--write");
        assertThat(again.exitCode()).isEqualTo(1);
    }

    @Test
    void aDirtyLoadExitsOne(@TempDir Path folder) throws IOException {
        String path = aFile(folder, "o.csv", OPENINGS);
        Cli.Result found = call("import", "load", "openings", path);
        assertThat(found.exitCode()).isEqualTo(1);
        assertThat(found.output()).contains("no such building");
    }

    @Test
    void theCheckCommandIsQuietOnACleanFile(@TempDir Path folder) throws IOException {
        String path = aFile(folder, "b.csv", BUILDINGS);
        Cli.Result found = call("import", "check", "buildings", path);
        assertThat(found.output().strip()).isEqualTo("2 rows read, nothing to fix");
    }

    @Test
    void theCheckCommandListsOnlyTheProblems(@TempDir Path folder) throws IOException {
        String path = aFile(folder, "o.csv", OPENINGS);
        Cli.Result found = call("import", "check", "openings", path);
        assertThat(found.exitCode()).isEqualTo(1);
        assertThat(found.lines().get(0)).isEqualTo("2 problems");
    }

    @Test
    void aFileThatIsNotThereIsRefused() {
        assertThat(call("import", "load", "buildings", "nowhere.csv").exitCode())
                .isNotZero();
    }

    @Test
    void aWholeCampusLoadsInOrder(@TempDir Path folder) throws IOException {
        call("import", "load", "buildings", aFile(folder, "b.csv", BUILDINGS), "--write");
        Cli.Result found = call("import", "load", "openings", aFile(folder, "o.csv", OPENINGS), "--write");
        assertThat(found.output().strip()).isEqualTo("openings: 2 rows");
    }
}

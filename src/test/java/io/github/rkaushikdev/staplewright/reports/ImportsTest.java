package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ImportsTest {

    private static final List<String> HEADER =
            List.of("number", "building", "floor", "room", "function", "exterior", "description");

    @SafeVarargs
    private static String aFile(List<String>... rows) {
        List<List<String>> all = new ArrayList<>();
        all.add(HEADER);
        for (List<String> row : rows) {
            all.add(row);
        }
        return Imports.asCsv(all);
    }

    private static List<String> aRow() {
        return aRow("NEW-1");
    }

    private static List<String> aRow(String number) {
        return aRow(number, "MOO", "3", "301", "office", "no", "");
    }

    private static List<String> aRow(
            String number,
            String building,
            String floor,
            String room,
            String function,
            String exterior,
            String description) {
        return List.of(number, building, floor, room, function, exterior, description);
    }

    private static OpeningSchedule anEmptyCampus() {
        OpeningSchedule schedule = new OpeningSchedule();
        schedule.addBuilding(new Building("MOO", "Moore Hall"));
        return schedule;
    }

    @Test
    void aCleanFileReadsEveryRow() {
        Reading found = Imports.readOpenings(aFile(aRow(), aRow("NEW-2", "MOO", "3", "302", "office", "no", "")));
        assertThat(found.isClean()).isTrue();
        assertThat(found.count()).isEqualTo(2);
        assertThat(found.summary()).isEqualTo("2 rows read, 0 to fix");
    }

    @Test
    void theRowsComeBackAsOpenings() {
        Reading found = Imports.readOpenings(aFile(aRow()));
        Opening opening = found.openings().get(0);
        assertThat(opening.number()).isEqualTo("NEW-1");
        assertThat(opening.function()).isSameAs(DoorFunction.OFFICE);
        assertThat(opening.exterior()).isFalse();
    }

    @Test
    void anExteriorFlagIsRead() {
        Reading found = Imports.readOpenings(aFile(aRow("NEW-1", "MOO", "3", "301", "entrance", "yes", "")));
        assertThat(found.openings().get(0).exterior()).isTrue();
    }

    @Test
    void aDescriptionSurvivesAComma() {
        Reading found = Imports.readOpenings(
                aFile(aRow("NEW-1", "MOO", "3", "301", "office", "no", "north door, by the loading bay")));
        assertThat(found.openings().get(0).description()).startsWith("north door,");
    }

    @Test
    void aMissingColumnIsTheOnlyThingReported() {
        String text = Imports.asCsv(List.of(List.of("number", "building"), List.of("NEW-1", "MOO")));
        Reading found = Imports.readOpenings(text);
        assertThat(found.isClean()).isFalse();
        assertThat(found.problems().stream().map(RowProblem::column).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("floor", "room", "function");
        assertThat(found.count()).isEqualTo(0);
    }

    @Test
    void theMissingColumnsCanBeAskedForFirst() {
        String text = Imports.asCsv(List.of(List.of("number", "building"), List.of("NEW-1", "MOO")));
        assertThat(Imports.missingColumns(text)).containsExactly("floor", "room", "function");
        assertThat(Imports.missingColumns(aFile(aRow()))).isEmpty();
    }

    @Test
    void aRowWithNoNumberIsReportedWithItsLine() {
        Reading found = Imports.readOpenings(aFile(aRow(""), aRow("NEW-2")));
        assertThat(found.problems().stream().map(RowProblem::line).toList()).containsExactly(2);
        assertThat(found.count()).isEqualTo(1);
    }

    @Test
    void aNumberTwiceInOneFileIsReported() {
        Reading found = Imports.readOpenings(aFile(aRow(), aRow()));
        assertThat(found.problems().get(0).message()).endsWith("twice");
        assertThat(found.count()).isEqualTo(1);
    }

    @Test
    void aFunctionNobodyHasHeardOfIsReported() {
        Reading found = Imports.readOpenings(aFile(aRow("NEW-1", "MOO", "3", "301", "hovercraft", "no", "")));
        assertThat(found.problems().get(0).message()).contains("hovercraft");
        assertThat(found.count()).isEqualTo(0);
    }

    @Test
    void aRowTheDomainRefusesIsReportedRatherThanRaised() {
        Reading found = Imports.readOpenings(aFile(aRow("NEW-1", "MOO", "3", "301", "privacy", "yes", "")));
        assertThat(found.isClean()).isFalse();
        assertThat(found.problems().get(0).column()).isEqualTo("row");
    }

    @Test
    void theProblemsDescribeThemselvesWithLineNumbers() {
        Reading found = Imports.readOpenings(aFile(aRow("")));
        assertThat(Imports.describe(found.problems()).get(0)).startsWith("line 2, number:");
    }

    @Test
    void aCleanReadingAppliesWhole() {
        OpeningSchedule schedule = anEmptyCampus();
        Reading found = Imports.readOpenings(aFile(aRow(), aRow("NEW-2", "MOO", "3", "302", "office", "no", "")));
        Applied applied = Imports.applyOpenings(schedule, found);
        assertThat(applied.written()).isEqualTo(2);
        assertThat(applied.problems()).isEmpty();
        assertThat(schedule.size()).isEqualTo(2);
    }

    @Test
    void aDirtyReadingAppliesNothing() {
        OpeningSchedule schedule = anEmptyCampus();
        Reading found = Imports.readOpenings(aFile(aRow(), aRow("")));
        Applied applied = Imports.applyOpenings(schedule, found);
        assertThat(applied.written()).isEqualTo(0);
        assertThat(applied.problems()).isNotEmpty();
        assertThat(schedule.size()).isEqualTo(0);
    }

    @Test
    void aBuildingNobodyCreatedStopsTheWholeLoad() {
        OpeningSchedule schedule = anEmptyCampus();
        Reading found = Imports.readOpenings(aFile(aRow(), aRow("NEW-2", "ZZZ", "3", "301", "office", "no", "")));
        Applied applied = Imports.applyOpenings(schedule, found);
        assertThat(applied.written()).isEqualTo(0);
        assertThat(applied.problems().get(0).column()).isEqualTo("building");
        assertThat(schedule.size()).isEqualTo(0);
    }

    @Test
    void anOpeningThatIsAlreadyThereStopsTheLoad() {
        OpeningSchedule schedule = aCampus();
        Reading found = Imports.readOpenings(aFile(aRow("MOO-214", "MOO", "2", "214", "office", "no", "")));
        Applied applied = Imports.applyOpenings(schedule, found);
        assertThat(applied.written()).isEqualTo(0);
        assertThat(applied.problems().get(0).message()).endsWith("already on the list");
    }

    @Test
    void anEmptyFileReadsNothingAndComplainsAboutNothing() {
        Reading found = Imports.readOpenings(Imports.asCsv(List.of(HEADER)));
        assertThat(found.isClean()).isTrue();
        assertThat(found.count()).isEqualTo(0);
    }
}

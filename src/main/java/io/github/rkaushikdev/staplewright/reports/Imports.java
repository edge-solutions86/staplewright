package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.StaplewrightError;
import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reading a survey back in, and refusing to guess what a bad row meant.
 *
 * <p>Somebody walks a building with a tablet and a spreadsheet and comes back with four hundred rows.
 * Twenty of them have the room number in the floor column, three have a function nobody has heard
 * of, and one has a comma where a door description should be. The useful thing is not to import
 * three hundred and seventy six of them quietly: it is to import none of them and hand back a list of
 * the rows that need fixing, with line numbers.
 *
 * <p>So every import is two passes. Read and check, which produces rows and problems and touches
 * nothing, and then apply, which only runs when there are no problems. A half loaded survey is worse
 * than no survey, because nobody can tell afterwards which half is real.
 */
public final class Imports {

    /** The columns an openings file has to have. Exterior and description are optional. */
    public static final List<String> OPENING_COLUMNS = List.of("number", "building", "floor", "room", "function");

    private static final Set<String> YES = Set.of("yes", "true", "1");

    private Imports() {}

    /** The file's header and its rows, each row keyed by column name, blank lines left out. */
    private record Parsed(List<String> header, List<Map<String, String>> rows) {}

    private static Parsed parse(String text) {
        List<List<String>> records = Csv.read(text);
        List<String> header = records.isEmpty() ? List.of() : records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> record : records.subList(Math.min(1, records.size()), records.size())) {
            if (record.isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < Math.min(header.size(), record.size()); index++) {
                row.put(header.get(index), record.get(index));
            }
            for (int index = record.size(); index < header.size(); index++) {
                row.remove(header.get(index));
            }
            rows.add(row);
        }
        return new Parsed(header, rows);
    }

    /** The column names on the first line of a file, as written; empty for an empty file. */
    public static List<String> header(String text) {
        return List.copyOf(parse(text).header());
    }

    /**
     * Every row after the first line, each keyed by column name, blank lines left out. A row shorter
     * than the header has no value at all for the columns it stops short of.
     */
    public static List<Map<String, String>> rows(String text) {
        return parse(text).rows().stream().map(Collections::unmodifiableMap).toList();
    }

    private static String cell(Map<String, String> row, String column) {
        return Text.strip(row.getOrDefault(column, ""));
    }

    /** Columns the file does not have, before any row is looked at, in the order they are wanted. */
    public static List<String> missingColumns(String text) {
        Set<String> present = new HashSet<>(parse(text).header());
        return OPENING_COLUMNS.stream().filter(name -> !present.contains(name)).toList();
    }

    /**
     * Read a survey, checking every row and building none of them twice. A file missing a column is
     * reported for the columns alone, against line one, with no row read.
     */
    public static Reading readOpenings(String text) {
        List<String> absent = missingColumns(text);
        if (!absent.isEmpty()) {
            List<RowProblem> missing = absent.stream()
                    .map(name -> new RowProblem(1, name, "column is missing from the file"))
                    .toList();
            return new Reading(List.of(), missing);
        }
        List<Opening> openings = new ArrayList<>();
        List<RowProblem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int line = 1;
        for (Map<String, String> row : parse(text).rows()) {
            line++;
            String number = cell(row, "number");
            if (number.isEmpty()) {
                problems.add(new RowProblem(line, "number", "an opening needs a number"));
                continue;
            }
            if (!seen.add(number)) {
                problems.add(new RowProblem(line, "number", "that number is in the file twice"));
                continue;
            }
            Optional<DoorFunction> function = DoorFunction.find(cell(row, "function"));
            if (function.isEmpty()) {
                problems.add(new RowProblem(line, "function", "no such function: " + row.getOrDefault("function", "")));
                continue;
            }
            boolean exterior = YES.contains(Text.lower(cell(row, "exterior")));
            try {
                openings.add(new Opening(
                        number,
                        cell(row, "building"),
                        cell(row, "floor"),
                        cell(row, "room"),
                        function.get(),
                        cell(row, "description"),
                        exterior));
            } catch (StaplewrightError refused) {
                problems.add(new RowProblem(line, "row", refused.getMessage()));
            }
        }
        return new Reading(openings, problems);
    }

    /**
     * Write a clean reading into a schedule, or write none of it.
     *
     * <p>Buildings have to be there already. A survey that names a building nobody has created is a
     * survey of somewhere else, and an opening already on the schedule is not overwritten.
     */
    public static Applied applyOpenings(OpeningSchedule schedule, Reading reading) {
        if (!reading.isClean()) {
            return new Applied(0, reading.problems());
        }
        Set<String> buildings = new HashSet<>();
        for (Building building : schedule.buildings()) {
            buildings.add(building.code());
        }
        List<RowProblem> problems = new ArrayList<>();
        int line = 1;
        for (Opening opening : reading.openings()) {
            line++;
            if (!buildings.contains(opening.building())) {
                problems.add(new RowProblem(line, "building", "no such building: " + opening.building()));
            } else if (schedule.contains(opening.number())) {
                problems.add(new RowProblem(line, "number", "that opening is already on the list"));
            }
        }
        if (!problems.isEmpty()) {
            return new Applied(0, problems);
        }
        for (Opening opening : reading.openings()) {
            schedule.add(opening);
        }
        return new Applied(reading.count(), List.of());
    }

    /** Every problem as one line for the person fixing the file. */
    public static List<String> describe(Collection<RowProblem> problems) {
        return problems.stream().map(RowProblem::describe).toList();
    }

    /** A small csv document, a line a row, which the tests and the examples both want. */
    public static String asCsv(List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            out.append(Csv.line(row));
        }
        return out.toString();
    }
}

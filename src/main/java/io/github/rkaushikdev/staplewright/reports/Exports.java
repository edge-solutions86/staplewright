package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Exports, in whatever the receiving system happens to eat this year.
 *
 * <p>Not a serialisation format and not an interchange format. An export here is one table, flat,
 * with a header row naming columns that a person picked, because the campus space management system
 * imports csv and nothing else. Every export hands back rows rather than writing a file, so a caller
 * can send them to a response, a file, or a test without any of them caring.
 *
 * <p>Dates are ISO and money is in cents, both of which the receiving system reads without asking.
 * Nothing is quoted by hand: a field is quoted when it carries a comma, a quote or a line break, and
 * only then.
 */
public final class Exports {

    /** Every symbol on a schedule, with its level, its section and its cut. */
    public static final Table<Iterable<ScheduleEntry>> SCHEDULE =
            new Table<>("schedule", List.of("symbol", "level", "section", "bitting", "note"), Exports::scheduleRows);

    /** Every opening on the campus, with what it does and whether it faces outside. */
    public static final Table<Iterable<Opening>> OPENINGS = new Table<>(
            "openings",
            List.of("number", "building", "floor", "room", "function", "exterior", "description"),
            Exports::openingRows);

    /** Every cylinder the shop owns, and where it sits when it sits anywhere. */
    public static final Table<Iterable<Cylinder>> CYLINDERS = new Table<>(
            "cylinders",
            List.of("serial", "format", "section", "chambers", "opening", "position", "group"),
            Exports::cylinderRows);

    /** Every key out, who has it, and the deposit taken on it. */
    public static final Table<Iterable<IssuedKey>> HOLDINGS = new Table<>(
            "holdings",
            List.of("tag", "symbol", "keyholder", "issued", "due", "deposit_cents", "state"),
            Exports::holdingRows);

    private static final Map<String, Table<?>> TABLES = tables();

    private Exports() {}

    private static Map<String, Table<?>> tables() {
        Map<String, Table<?>> found = new LinkedHashMap<>();
        for (Table<?> table : List.of(SCHEDULE, OPENINGS, CYLINDERS, HOLDINGS)) {
            found.put(table.name(), table);
        }
        return Collections.unmodifiableMap(found);
    }

    /** One csv document, with the header first and unix line endings. A missing value is an empty field. */
    public static String toText(List<String> header, Iterable<Row> rows) {
        StringBuilder out = new StringBuilder(Csv.line(header));
        for (Row row : rows) {
            List<String> fields = new ArrayList<>();
            for (int index = 0; index < row.size(); index++) {
                fields.add(row.text(index));
            }
            out.append(Csv.line(fields));
        }
        return out.toString();
    }

    /**
     * The same rows as records, a column name to each value, for anything that speaks json.
     *
     * @throws IllegalArgumentException when a row does not have one value a column
     */
    public static List<Map<String, Optional<Object>>> toRecords(List<String> header, Iterable<Row> rows) {
        List<Map<String, Optional<Object>>> records = new ArrayList<>();
        for (Row row : rows) {
            if (row.size() != header.size()) {
                throw new IllegalArgumentException(
                        "a row has " + row.size() + " values for " + header.size() + " columns");
            }
            Map<String, Optional<Object>> record = new LinkedHashMap<>();
            for (int index = 0; index < header.size(); index++) {
                record.put(header.get(index), row.cell(index));
            }
            records.add(Collections.unmodifiableMap(record));
        }
        return List.copyOf(records);
    }

    /** The rows as a json list of records, indented two spaces, a missing value written as null. */
    public static String toJson(List<String> header, Iterable<Row> rows) {
        return Json.records(toRecords(header, rows));
    }

    /** A row a symbol: the symbol, its level, its section, its cut and the note. */
    public static List<Row> scheduleRows(Iterable<ScheduleEntry> entries) {
        List<Row> rows = new ArrayList<>();
        for (ScheduleEntry entry : entries) {
            rows.add(Row.of(
                    entry.symbol().render(),
                    entry.symbol().level(),
                    entry.section(),
                    entry.bitting().map(Bitting::render).orElse(null),
                    entry.note()));
        }
        return List.copyOf(rows);
    }

    /** A row an opening, with {@code yes} or {@code no} for whether it faces outside. */
    public static List<Row> openingRows(Iterable<Opening> openings) {
        List<Row> rows = new ArrayList<>();
        for (Opening opening : openings) {
            rows.add(Row.of(
                    opening.number(),
                    opening.building(),
                    opening.floor(),
                    opening.room(),
                    opening.function().value(),
                    opening.exterior() ? "yes" : "no",
                    opening.description()));
        }
        return List.copyOf(rows);
    }

    /** A row a cylinder, the opening and position left blank on one that is on the shelf. */
    public static List<Row> cylinderRows(Iterable<Cylinder> cylinders) {
        List<Row> rows = new ArrayList<>();
        for (Cylinder cylinder : cylinders) {
            rows.add(Row.of(
                    cylinder.serial(),
                    cylinder.format().value(),
                    cylinder.section(),
                    cylinder.chambers(),
                    cylinder.installedAt().orElse(null),
                    cylinder.position().map(Position::value).orElse(null),
                    cylinder.group()));
        }
        return List.copyOf(rows);
    }

    /** A row a key, the deposit being what the key itself says was taken. */
    public static List<Row> holdingRows(Iterable<IssuedKey> keys) {
        return holdingRows(keys, Optional.empty());
    }

    /** A row a key, the deposit coming off the book. */
    public static List<Row> holdingRows(Iterable<IssuedKey> keys, DepositBook book) {
        return holdingRows(keys, Optional.of(book));
    }

    /**
     * A row a key. The deposit comes off the book when there is one with anything in it, and
     * otherwise is what the key says was taken.
     */
    public static List<Row> holdingRows(Iterable<IssuedKey> keys, Optional<DepositBook> book) {
        Optional<DepositBook> used = ControlPages.inUse(book);
        List<Row> rows = new ArrayList<>();
        for (IssuedKey key : keys) {
            long held = used.map(found -> found.heldOn(key.tag())).orElse((long) key.depositCents());
            rows.add(Row.of(
                    key.tag(),
                    key.symbol(),
                    key.keyholder(),
                    key.issuedOn().toString(),
                    key.dueOn().map(LocalDate::toString).orElse(null),
                    held,
                    key.state().value()));
        }
        return List.copyOf(rows);
    }

    /**
     * The table somebody asked for by name, which is how the command line reaches one.
     *
     * @throws NotFoundError when nothing is exported under that name
     */
    public static Table<?> table(String name) {
        Table<?> found = TABLES.get(name);
        if (found == null) {
            throw new NotFoundError("nothing is exported under that name", Facts.of("table", name));
        }
        return found;
    }

    /** Every table's name, in name order. */
    public static List<String> tableNames() {
        return List.copyOf(new TreeMap<>(TABLES).keySet());
    }

    /** What the shop calls a csv file of {@code kind} when it lands in the shared folder on {@code on}. */
    public static String filename(String kind, LocalDate on) {
        return filename(kind, on, "csv");
    }

    /** What the shop calls the file when it lands in the shared folder: {@code staplewright-holdings-2025-04-08.csv}. */
    public static String filename(String kind, LocalDate on, String suffix) {
        return "staplewright-" + kind + "-" + on + "." + suffix;
    }
}

package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.SystemRepository;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.reports.Exports;
import io.github.rkaushikdev.staplewright.reports.Row;
import io.github.rkaushikdev.staplewright.reports.Table;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Getting a table out of the database and into whatever is asking for it.
 *
 * <p>The export tables take domain objects, not rows, and that is on purpose: they can be tested
 * against a schedule somebody built in four lines. What they cannot do is get that schedule out of a
 * database, which is this.
 *
 * <p>Every method here loads through the workspace rather than querying, so an export sees exactly
 * what a report sees. An export that queried directly would eventually disagree with the report of
 * the same name, and then the finance system and the printed sheet would be arguing about which is
 * right.
 */
public final class ExportService {

    /** The formats an export can be written in. */
    public static final List<String> FORMATS = List.of("csv", "json");

    private final Session session;
    private final Workspace space;
    private final SystemRepository systems;

    /** An export service on a workspace of its own, under the standing rules. */
    public ExportService(Session session) {
        this(session, new Workspace(session));
    }

    /** An export service sharing a workspace, so it loads nothing the caller already has. */
    public ExportService(Session session, Workspace workspace) {
        this.session = session;
        this.space = workspace;
        this.systems = workspace.systems();
    }

    /**
     * One export's table and the rows built for it, which is everything a format needs.
     *
     * @param table the export, with its name and its columns
     * @param rows a row a thing exported, in the order the table builds them
     */
    public record Extract(Table<?> table, List<Row> rows) {

        public Extract {
            rows = List.copyOf(rows);
        }

        /** The columns, in order. */
        public List<String> header() {
            return table.header();
        }
    }

    public Session session() {
        return session;
    }

    /** The workspace every export loads through. */
    public Workspace workspace() {
        return space;
    }

    /** Every table there is to export, in name order. */
    public List<String> names() {
        return Exports.tableNames();
    }

    /** The table and its rows, for an export that needs no system named. */
    public Extract rowsFor(String name) {
        return rowsFor(name, "");
    }

    /**
     * The table and its rows. Only the schedule export reads {@code system}.
     *
     * @throws NotFoundError when nothing is exported under that name, or the system is not there
     * @throws InputError when the schedule is asked for without a system
     */
    public Extract rowsFor(String name, String system) {
        Table<?> wanted = Exports.table(name);
        List<Row> rows =
                switch (name) {
                    case "schedule" -> Exports.SCHEDULE.rows(schedule(system).entries());
                    case "openings" -> Exports.OPENINGS.rows(space.openings());
                    case "cylinders" -> Exports.CYLINDERS.rows(space.register());
                    case "holdings" -> Exports.holdingRows(space.ledger().out(), space.deposits());
                    default -> throw new NotFoundError("nothing is exported under that name", Facts.of("table", name));
                };
        return new Extract(wanted, rows);
    }

    /** One export as a csv document. */
    public String text(String name) {
        return text(name, "csv", "");
    }

    /** One export as a document in {@code shape}, for an export that needs no system named. */
    public String text(String name, String shape) {
        return text(name, shape, "");
    }

    /**
     * One export as a document, in csv or json.
     *
     * @throws InputError for a format this does not write
     */
    public String text(String name, String shape, String system) {
        if (!FORMATS.contains(shape)) {
            throw new InputError("that is not a format this exports", Facts.of("shape", shape));
        }
        Extract wanted = rowsFor(name, system);
        if (shape.equals("json")) {
            return Exports.toJson(wanted.header(), wanted.rows());
        }
        return Exports.toText(wanted.header(), wanted.rows());
    }

    /** One export as records, a column name to each value, for an export that needs no system named. */
    public List<Map<String, Optional<Object>>> records(String name) {
        return records(name, "");
    }

    /** One export as records, a column name to each value. */
    public List<Map<String, Optional<Object>>> records(String name, String system) {
        Extract wanted = rowsFor(name, system);
        return Exports.toRecords(wanted.header(), wanted.rows());
    }

    /** What a csv file of one export is called when it lands on {@code on}. */
    public String filename(String name, LocalDate on) {
        return filename(name, on, "csv");
    }

    /** What the file of one export is called when it lands on {@code on}, in {@code shape}. */
    public String filename(String name, LocalDate on, String shape) {
        return Exports.table(name).filename(on, shape);
    }

    /** Every export that needs no system named, as csv, keyed by file name. */
    public Map<String, String> everyTable(LocalDate on) {
        return everyTable(on, "csv");
    }

    /** Every export that needs no system named, keyed by file name, in name order. */
    public Map<String, String> everyTable(LocalDate on, String shape) {
        Map<String, String> found = new LinkedHashMap<>();
        for (String name : names()) {
            if (name.equals("schedule")) {
                continue;
            }
            found.put(filename(name, on, shape), text(name, shape));
        }
        return Collections.unmodifiableMap(found);
    }

    /** The names of the systems still in use, which are the ones a schedule export can name. */
    public List<String> systemsToExport() {
        return systems.live().stream().map(SystemRow::getName).toList();
    }

    private KeyingSchedule schedule(String system) {
        if (Text.isBlank(system)) {
            throw new InputError("a schedule export names a system");
        }
        return space.schedule(system);
    }
}

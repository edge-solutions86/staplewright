package io.github.rkaushikdev.staplewright.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One export: what it is called, its columns, and how to build its rows out of what it exports.
 *
 * <p>A table is one object and a format is something you ask it for, so the csv and the json of the
 * same table can never disagree about which columns are in it.
 *
 * @param <S> what the rows are built from: a schedule's entries, the openings, the cylinders, the
 *     keys out
 */
public final class Table<S> {

    private final String name;
    private final List<String> header;
    private final Function<? super S, List<Row>> build;

    /** A table called {@code name}, with these columns, whose rows {@code build} makes. */
    public Table(String name, List<String> header, Function<? super S, List<Row>> build) {
        this.name = name;
        this.header = List.copyOf(header);
        this.build = build;
    }

    /** What the table is exported as, which is also the middle of its file name. */
    public String name() {
        return name;
    }

    /** The column names, in order. */
    public List<String> header() {
        return header;
    }

    /** The rows built from {@code source}. */
    public List<Row> rows(S source) {
        return List.copyOf(build.apply(source));
    }

    /** The table as one csv document, header first. */
    public String csv(S source) {
        return Exports.toText(header, rows(source));
    }

    /** The table as a json list of records. */
    public String json(S source) {
        return Exports.toJson(header, rows(source));
    }

    /** The table as records, a column name to each value. */
    public List<Map<String, Optional<Object>>> records(S source) {
        return Exports.toRecords(header, rows(source));
    }

    /** What the shop calls this table's csv file when it lands on {@code on}. */
    public String filename(LocalDate on) {
        return filename(on, "csv");
    }

    /** What the shop calls this table's file when it lands on {@code on}, ending in {@code suffix}. */
    public String filename(LocalDate on, String suffix) {
        return Exports.filename(name, on, suffix);
    }

    @Override
    public String toString() {
        return "<Table " + name + ">";
    }
}

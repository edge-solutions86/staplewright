package io.github.rkaushikdev.staplewright.reports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of an export, a value a column. A column can be missing a value, which a csv document
 * writes as an empty field and a json document writes as null; a present value is text or a whole
 * number.
 */
public final class Row {

    private final List<Object> values;

    private Row(Object[] values) {
        this.values = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }

    /** A row of these values in column order, a {@code null} standing for a missing value. */
    public static Row of(Object... values) {
        return new Row(values.clone());
    }

    /** How many columns the row has. */
    public int size() {
        return values.size();
    }

    /** The value in one column, empty when the column has none. */
    public Optional<Object> cell(int index) {
        return Optional.ofNullable(values.get(index));
    }

    /** Every column's value in order, each empty where the column has none. */
    public List<Optional<Object>> cells() {
        return values.stream().map(Optional::ofNullable).toList();
    }

    /** The value in one column as a csv field: its text, or nothing when it is missing. */
    String text(int index) {
        Object value = values.get(index);
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Row that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}

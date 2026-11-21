package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * One numbering series and the next number in it.
 *
 * <p>Key tags, work order numbers, incident numbers and request numbers all come out of here, one
 * row per series, and they come out gapless because the row is locked inside the same transaction
 * that writes whatever uses the number. An identity column would be faster and would leave holes in
 * the sequence every time a transaction rolls back, and a hole in a key tag series is an afternoon
 * of somebody looking for a key that never existed. The next number is never below one.
 */
@Entity
@Table(name = "counters")
public class CounterRow {

    @Id
    @Column(length = Schema.SHORT)
    private String name;

    @Column(nullable = false, length = Schema.SHORT)
    private String prefix = "";

    @Column(nullable = false)
    private int width = 4;

    @Column(nullable = false)
    private int nextValue = 1;

    protected CounterRow() {}

    /** A series with no prefix, four digits wide, starting at one. */
    public CounterRow(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** What every number in the series starts with, such as {@code W-}. */
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /** How many digits a number is padded to with zeros; a wider number is never cut short. */
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getNextValue() {
        return nextValue;
    }

    public void setNextValue(int nextValue) {
        this.nextValue = nextValue;
    }

    /** A number of this series as it is handed out: the prefix, then the value padded to the width. */
    public String render(int value) {
        String padded = width < 1 ? String.valueOf(value) : String.format(Locale.ROOT, "%0" + width + "d", value);
        return prefix + padded;
    }

    @Override
    public String toString() {
        return "<CounterRow " + name + " at " + nextValue + ">";
    }
}

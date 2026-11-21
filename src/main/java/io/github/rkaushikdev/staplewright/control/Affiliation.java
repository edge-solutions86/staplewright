package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** What kind of person a keyholder is, which decides what they may hold. */
public enum Affiliation implements Valued {
    FACULTY("faculty"),
    STAFF("staff"),
    STUDENT("student"),
    CONTRACTOR("contractor"),
    EMERITUS("emeritus");

    private final String value;

    Affiliation(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether this affiliation carries an end date and lapses on its own. */
    public boolean isTemporary() {
        return this == CONTRACTOR || this == STUDENT;
    }

    /** The affiliation written as {@code text}, when there is one. */
    public static Optional<Affiliation> find(String text) {
        return Choices.find(Affiliation.class, text);
    }

    /** The affiliation written as {@code text}, refusing anything else. */
    public static Affiliation fromValue(String text) {
        return Choices.fromValue(Affiliation.class, text, "affiliation");
    }

    @Override
    public String toString() {
        return value;
    }
}

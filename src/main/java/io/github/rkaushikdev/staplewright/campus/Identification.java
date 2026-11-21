package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * How the technician at a lockout knew who they were letting in.
 *
 * <p>Somebody who cannot be identified does not get let in, which is why a lockout records how they
 * were identified rather than whether. {@link #NONE} is what a call carries until somebody is
 * identified, and a door is never opened on it.
 */
public enum Identification implements Valued {
    CAMPUS_CARD("campus card"),
    DEPARTMENT_STAFF("vouched for by department"),
    ROSTER("on the room roster"),
    NONE("not identified");

    private final String value;

    Identification(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The identification written as {@code text}, when there is one. */
    public static Optional<Identification> find(String text) {
        return Choices.find(Identification.class, text);
    }

    /** The identification written as {@code text}, refusing anything else. */
    public static Identification fromValue(String text) {
        return Choices.fromValue(Identification.class, text, "identification");
    }

    @Override
    public String toString() {
        return value;
    }
}

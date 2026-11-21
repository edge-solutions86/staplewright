package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Where on an opening a cylinder sits.
 *
 * <p>One opening can carry more than one cylinder. A storeroom door with a deadbolt above the
 * lockset is two, and an exit device with an outside trim cylinder is two more, so the position is
 * part of what makes an installation unique.
 */
public enum Position implements Valued {
    LOCKSET("lockset"),
    DEADBOLT("deadbolt"),
    TRIM("trim"),
    SHACKLE("shackle");

    private final String value;

    Position(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The position written as {@code text}, when there is one. */
    public static Optional<Position> find(String text) {
        return Choices.find(Position.class, text);
    }

    /** The position written as {@code text}, refusing anything else. */
    public static Position fromValue(String text) {
        return Choices.fromValue(Position.class, text, "cylinder position");
    }

    @Override
    public String toString() {
        return value;
    }
}

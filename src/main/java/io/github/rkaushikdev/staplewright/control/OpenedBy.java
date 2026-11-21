package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** Who broke a box's seal, which decides what happens next. */
public enum OpenedBy implements Valued {
    FIRE("fire service"),
    SHOP("the lock shop"),
    UNKNOWN("nobody signed for it");

    private final String value;

    OpenedBy(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The opener written as {@code text}, when there is one. */
    public static Optional<OpenedBy> find(String text) {
        return Choices.find(OpenedBy.class, text);
    }

    /** The opener written as {@code text}, refusing anything else. */
    public static OpenedBy fromValue(String text) {
        return Choices.fromValue(OpenedBy.class, text, "box opener");
    }

    @Override
    public String toString() {
        return value;
    }
}

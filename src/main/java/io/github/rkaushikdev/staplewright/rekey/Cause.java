package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** How a key left the shop's control. */
public enum Cause implements Valued {
    LOST("lost"),
    STOLEN("stolen"),
    UNRETURNED("unreturned"),
    DUPLICATED("duplicated"),
    UNACCOUNTED("unaccounted");

    private final String value;

    Cause(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The cause written as {@code text}, when there is one. */
    public static Optional<Cause> find(String text) {
        return Choices.find(Cause.class, text);
    }

    /** The cause written as {@code text}, refusing anything else. */
    public static Cause fromValue(String text) {
        return Choices.fromValue(Cause.class, text, "cause");
    }

    @Override
    public String toString() {
        return value;
    }
}

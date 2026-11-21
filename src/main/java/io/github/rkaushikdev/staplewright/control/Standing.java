package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** Where a keyholder sits with the shop today. */
public enum Standing implements Valued {
    ACTIVE("active"),
    SEPARATED("separated"),
    SUSPENDED("suspended");

    private final String value;

    Standing(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The standing written as {@code text}, when there is one. */
    public static Optional<Standing> find(String text) {
        return Choices.find(Standing.class, text);
    }

    /** The standing written as {@code text}, refusing anything else. */
    public static Standing fromValue(String text) {
        return Choices.fromValue(Standing.class, text, "standing");
    }

    @Override
    public String toString() {
        return value;
    }
}

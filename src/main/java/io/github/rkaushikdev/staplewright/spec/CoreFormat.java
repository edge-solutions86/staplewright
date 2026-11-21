package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Arrays;
import java.util.Optional;

/** Which family of housing an interchangeable core drops into. */
public enum CoreFormat implements Valued {
    SFIC_A2("small format, A2"),
    SFIC_A3("small format, A3"),
    SFIC_A4("small format, A4"),
    LARGE("large format");

    private final String value;

    CoreFormat(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The format written as {@code text}, when there is one. */
    public static Optional<CoreFormat> find(String text) {
        return Arrays.stream(values())
                .filter(format -> format.value.equals(text))
                .findFirst();
    }

    /** The format written as {@code text}, refusing anything else. */
    public static CoreFormat fromValue(String text) {
        return find(text).orElseThrow(() -> new InputError("no such core format", Facts.of("value", text)));
    }

    @Override
    public String toString() {
        return value;
    }
}

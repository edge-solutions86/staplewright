package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** What sort of gap in the calendar a work window is. */
public enum WindowKind implements Valued {
    VACATION("vacation"),
    TERM("term"),
    WEEKEND("weekend"),
    SHUTDOWN("shutdown");

    private final String value;

    WindowKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The window kind written as {@code text}, when there is one. */
    public static Optional<WindowKind> find(String text) {
        return Choices.find(WindowKind.class, text);
    }

    /** The window kind written as {@code text}, refusing anything else. */
    public static WindowKind fromValue(String text) {
        return Choices.fromValue(WindowKind.class, text, "window kind");
    }

    @Override
    public String toString() {
        return value;
    }
}

package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** What one line of the deposit book does. A charge takes money in; the rest give it up again. */
public enum EntryKind implements Valued {
    CHARGE("charge"),
    REFUND("refund"),
    FORFEIT("forfeit"),
    WAIVER("waiver");

    private final String value;

    EntryKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The kind written as {@code text}, when there is one. */
    public static Optional<EntryKind> find(String text) {
        return Choices.find(EntryKind.class, text);
    }

    /** The kind written as {@code text}, refusing anything else. */
    public static EntryKind fromValue(String text) {
        return Choices.fromValue(EntryKind.class, text, "deposit kind");
    }

    @Override
    public String toString() {
        return value;
    }
}

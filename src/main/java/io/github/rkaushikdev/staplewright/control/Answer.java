package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** What a department said about one key on its review list. */
public enum Answer implements Valued {
    UNANSWERED("unanswered"),
    CONFIRMED("confirmed"),
    RETURN("asked back"),
    UNKNOWN("department does not recognise it");

    private final String value;

    Answer(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether this answer asks for the key back: asked back, or not recognised at all. */
    public boolean wantsReturn() {
        return this == RETURN || this == UNKNOWN;
    }

    /** The answer written as {@code text}, when there is one. */
    public static Optional<Answer> find(String text) {
        return Choices.find(Answer.class, text);
    }

    /** The answer written as {@code text}, refusing anything else. */
    public static Answer fromValue(String text) {
        return Choices.fromValue(Answer.class, text, "review answer");
    }

    @Override
    public String toString() {
        return value;
    }
}

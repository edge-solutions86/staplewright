package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** Where an annual review cycle has got to. Only an open review takes answers. */
public enum ReviewState implements Valued {
    OPEN("open"),
    CLOSED("closed"),
    ABANDONED("abandoned");

    private final String value;

    ReviewState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<ReviewState> find(String text) {
        return Choices.find(ReviewState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static ReviewState fromValue(String text) {
        return Choices.fromValue(ReviewState.class, text, "review state");
    }

    @Override
    public String toString() {
        return value;
    }
}

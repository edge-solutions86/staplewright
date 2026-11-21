package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;
import java.util.Set;

/** Where one cylinder on a work order is. Installed and skipped both settle the line. */
public enum LineState implements Valued {
    PENDING("pending"),
    PINNED("pinned"),
    INSTALLED("installed"),
    SKIPPED("skipped");

    /** The states that settle a line: the cylinder went back in, or was skipped with a reason. */
    public static final Set<LineState> SETTLED = Set.of(INSTALLED, SKIPPED);

    private final String value;

    LineState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a line in this state has been dealt with. */
    public boolean isSettled() {
        return SETTLED.contains(this);
    }

    /** The line state written as {@code text}, when there is one. */
    public static Optional<LineState> find(String text) {
        return Choices.find(LineState.class, text);
    }

    /** The line state written as {@code text}, refusing anything else. */
    public static LineState fromValue(String text) {
        return Choices.fromValue(LineState.class, text, "line state");
    }

    @Override
    public String toString() {
        return value;
    }
}

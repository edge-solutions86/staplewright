package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Where a lockout call has got to.
 *
 * <p>A call is logged when the telephone rings and attended when a technician arrives. It is still
 * open in both of those states. It closes when the door is opened, when the technician refuses, or
 * when the caller withdraws it.
 */
public enum CallState implements Valued {
    LOGGED("logged"),
    ATTENDED("attended"),
    OPENED("opened"),
    REFUSED("refused"),
    WITHDRAWN("withdrawn");

    private final String value;

    CallState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a call here is still waiting for somebody. */
    public boolean isOpen() {
        return this == LOGGED || this == ATTENDED;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<CallState> find(String text) {
        return Choices.find(CallState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static CallState fromValue(String text) {
        return Choices.fromValue(CallState.class, text, "lockout state");
    }

    @Override
    public String toString() {
        return value;
    }
}

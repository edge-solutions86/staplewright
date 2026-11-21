package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/**
 * Where a key request has got to.
 *
 * <p>Raised, submitted, then approved or denied, and a request that has been decided cannot be
 * decided again. Withdrawn is not the same as denied: the department changed its mind, nobody
 * turned it down, and that difference matters to the department six months later.
 */
public enum RequestState implements Valued {
    RAISED("raised"),
    SUBMITTED("submitted"),
    APPROVED("approved"),
    DENIED("denied"),
    WITHDRAWN("withdrawn"),
    FILLED("filled");

    private final String value;

    RequestState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a request here is still waiting on somebody: raised or submitted. */
    public boolean isOpen() {
        return this == RAISED || this == SUBMITTED;
    }

    /** Whether a request here has had its answer: approved or denied. */
    public boolean isDecided() {
        return this == APPROVED || this == DENIED;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<RequestState> find(String text) {
        return Choices.find(RequestState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static RequestState fromValue(String text) {
        return Choices.fromValue(RequestState.class, text, "request state");
    }

    @Override
    public String toString() {
        return value;
    }
}

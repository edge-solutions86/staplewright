package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Where a sealed key box stands.
 *
 * <p>A box that has been opened is neither fine nor compromised: the key inside is out of anybody's
 * control from that moment, and the box stays opened, or empty once the key is found gone, until
 * somebody puts a new key in and a new seal on. A withdrawn box is off the wall for good.
 */
public enum BoxState implements Valued {
    SEALED("sealed"),
    OPENED("opened"),
    EMPTY("empty"),
    WITHDRAWN("withdrawn");

    private final String value;

    BoxState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a box in this state is still on a wall. */
    public boolean isLive() {
        return this != WITHDRAWN;
    }

    /** Whether a box in this state still has a key in it: sealed, or opened and not yet found empty. */
    public boolean holdsAKey() {
        return this == SEALED || this == OPENED;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<BoxState> find(String text) {
        return Choices.find(BoxState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static BoxState fromValue(String text) {
        return Choices.fromValue(BoxState.class, text, "box state");
    }

    @Override
    public String toString() {
        return value;
    }
}

package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Where one stamped key is.
 *
 * <p>Lost and stolen are different on purpose. A key left in a coat pocket at home comes back. A
 * key taken out of a desk drawer means somebody chose to take it, and the shop treats the two
 * differently when it decides how far to rekey.
 */
public enum KeyState implements Valued {
    OUT("out"),
    RETURNED("returned"),
    LOST("lost"),
    STOLEN("stolen");

    private final String value;

    KeyState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a key in this state was reported lost or stolen. */
    public boolean isGone() {
        return this == LOST || this == STOLEN;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<KeyState> find(String text) {
        return Choices.find(KeyState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static KeyState fromValue(String text) {
        return Choices.fromValue(KeyState.class, text, "key state");
    }

    @Override
    public String toString() {
        return value;
    }
}

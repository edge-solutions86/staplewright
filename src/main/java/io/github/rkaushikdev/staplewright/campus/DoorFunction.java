package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What the lockset on an opening does when nobody is holding a key.
 *
 * <p>The function matters more than it looks. A classroom lockset locks from outside with a key
 * and always opens from inside, so it is life safety neutral. A storeroom function is always
 * locked outside and needs a key every time. An office function can be left unlocked, which is the
 * one that produces the phone call about a stolen laptop. Privacy and exit only functions take no
 * cylinder at all.
 */
public enum DoorFunction implements Valued {
    CLASSROOM("classroom"),
    STOREROOM("storeroom"),
    OFFICE("office"),
    ENTRANCE("entrance"),
    PRIVACY("privacy"),
    EXIT_ONLY("exit_only"),
    PADLOCK("padlock"),
    CABINET("cabinet");

    /** Functions that take a cylinder, in declaration order. Privacy and exit only do not. */
    public static final Set<DoorFunction> KEYED_FUNCTIONS =
            Collections.unmodifiableSet(EnumSet.of(CLASSROOM, STOREROOM, OFFICE, ENTRANCE, PADLOCK, CABINET));

    private final String value;

    DoorFunction(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a lockset with this function takes a cylinder. */
    public boolean isKeyed() {
        return KEYED_FUNCTIONS.contains(this);
    }

    /** The function written as {@code text}, when there is one. */
    public static Optional<DoorFunction> find(String text) {
        return Choices.find(DoorFunction.class, text);
    }

    /** The function written as {@code text}, refusing anything else. */
    public static DoorFunction fromValue(String text) {
        return Choices.fromValue(DoorFunction.class, text, "door function");
    }

    @Override
    public String toString() {
        return value;
    }
}

package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** What a surveyor found on a door that somebody has to deal with. */
public enum Deficiency implements Valued {
    UNIDENTIFIED("cylinder cannot be identified"),
    WRONG_FUNCTION("function does not suit the room"),
    NO_CYLINDER("keyed opening with nothing in it"),
    WORN_OUT("hardware needs replacing before it is rekeyed"),
    NO_STRIKE("frame or strike needs work first"),
    UNKNOWN_KEY("opens to a key nobody has a record of");

    private final String value;

    Deficiency(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The deficiency written as {@code text}, when there is one. */
    public static Optional<Deficiency> find(String text) {
        return Choices.find(Deficiency.class, text);
    }

    /** The deficiency written as {@code text}, refusing anything else. */
    public static Deficiency fromValue(String text) {
        return Choices.fromValue(Deficiency.class, text, "deficiency");
    }

    @Override
    public String toString() {
        return value;
    }
}

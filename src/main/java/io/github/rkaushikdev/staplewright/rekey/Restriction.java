package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** Why a building cannot be touched outside its window, in the words the plan prints. */
public enum Restriction implements Valued {
    OCCUPIED("occupied by residents"),
    TEACHING("in teaching use"),
    EXAMS("exams running"),
    RESEARCH("research that cannot be interrupted"),
    NONE("no restriction");

    private final String value;

    Restriction(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The restriction written as {@code text}, when there is one. */
    public static Optional<Restriction> find(String text) {
        return Choices.find(Restriction.class, text);
    }

    /** The restriction written as {@code text}, refusing anything else. */
    public static Restriction fromValue(String text) {
        return Choices.fromValue(Restriction.class, text, "restriction");
    }

    @Override
    public String toString() {
        return value;
    }
}

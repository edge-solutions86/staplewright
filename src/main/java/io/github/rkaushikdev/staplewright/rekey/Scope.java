package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Text;

/** How far a rekey reaches, the way a work order names it. Ordered from cheapest. */
public enum Scope implements Valued {
    /** No cylinder comes apart. */
    NOTHING(0),
    /** One opening. */
    OPENING(1),
    /** Some of the cylinders in one building. */
    GROUP(2),
    /** Every installed cylinder in one building. */
    BRANCH(3),
    /** Cylinders in more than one building. */
    SYSTEM(4);

    private final int value;

    Scope(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    /** The reach in words, as a work order prints it: {@code group}. */
    public String label() {
        return Text.lower(name());
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

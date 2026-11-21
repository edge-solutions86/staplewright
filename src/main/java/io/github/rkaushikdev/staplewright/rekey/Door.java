package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;

/**
 * One opening in a phased rekey, with whatever stops it being done today.
 *
 * @param number the opening number
 * @param building the building code
 * @param restriction why it cannot be touched outside some windows
 * @param minutes the time it takes
 */
public record Door(String number, String building, Restriction restriction, int minutes) {

    /** How long a door takes unless told otherwise. */
    public static final int MINUTES = 25;

    /**
     * A door, refusing one that takes no time.
     *
     * @throws InputError for fewer than one minute
     */
    public Door {
        if (minutes < 1) {
            throw new InputError("a door takes some minutes", Facts.of("opening", number));
        }
    }

    /** A door with nothing stopping it, taking the standing time. */
    public Door(String number, String building) {
        this(number, building, Restriction.NONE);
    }

    /** A door under a restriction, taking the standing time. */
    public Door(String number, String building, Restriction restriction) {
        this(number, building, restriction, MINUTES);
    }

    /** Whether nothing stops the door being done in any window. */
    public boolean isFree() {
        return restriction == Restriction.NONE;
    }
}

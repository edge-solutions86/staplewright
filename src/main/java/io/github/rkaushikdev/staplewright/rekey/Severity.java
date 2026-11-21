package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * How the shop grades a missing key. Ordered on purpose, mildest first, and written as a number so
 * a higher grade is a worse incident.
 */
public enum Severity implements Valued {
    ROUTINE(1),
    ELEVATED(2),
    SERIOUS(3),
    CRITICAL(4);

    private final int value;

    Severity(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    /** The grade in words, as a summary prints it: {@code routine}. */
    public String label() {
        return Text.lower(name());
    }

    /**
     * The four numbers about an incident, turned into a grade the shop can argue with.
     *
     * <p>Written as a ladder rather than a score because a score invites people to tune the weights
     * until they get the answer they wanted. A master reaching more than one building, or a stolen
     * key to an exterior door, is critical. Any other master, or more than twenty openings, is
     * serious. An exterior door, more than five openings or a theft is elevated. Anything else is
     * routine.
     *
     * @param openings how many openings the key turns
     * @param buildings how many buildings those openings are in
     * @param exterior whether any of them faces outside
     * @param isMaster whether the key sits above a change key
     * @param cause how the key left
     */
    public static Severity grade(int openings, int buildings, boolean exterior, boolean isMaster, Cause cause) {
        if (isMaster && buildings > 1) {
            return CRITICAL;
        }
        if (exterior && cause == Cause.STOLEN) {
            return CRITICAL;
        }
        if (isMaster || openings > 20) {
            return SERIOUS;
        }
        if (exterior || openings > 5 || cause == Cause.STOLEN) {
            return ELEVATED;
        }
        return ROUTINE;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

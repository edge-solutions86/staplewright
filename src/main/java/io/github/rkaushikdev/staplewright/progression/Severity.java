package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Valued;

/**
 * How much the shop cares about a finding on a schedule. Declared worst first, which is the order a
 * review reports them in.
 */
public enum Severity implements Valued {
    /** Stops the job: two rooms sharing a lock, or a change key that is not one. */
    BLOCKING("blocking"),
    /** Needs looking at before the next cut. */
    SERIOUS("serious"),
    /** Worth knowing, usually just old. */
    NOTE("note");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}

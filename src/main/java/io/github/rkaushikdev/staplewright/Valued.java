package io.github.rkaushikdev.staplewright;

/**
 * A choice that is written down by a short value of its own, the way an enum of states or causes
 * is stored, sent and printed.
 *
 * <p>Every such enum also prints as its value, so a state reads the same in a message, a column
 * and a response body.
 */
public interface Valued {

    /** The value the choice is written as. */
    Object value();
}

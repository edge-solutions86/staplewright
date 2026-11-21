package io.github.rkaushikdev.staplewright.rekey;

/**
 * A door the plan could not give a day, and why.
 *
 * @param door the door
 * @param reason no window takes its restriction, or every window that would is full
 */
public record Unplaced(Door door, String reason) {}

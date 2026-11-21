package io.github.rkaushikdev.staplewright.spec;

/**
 * Two cores on different control keys that could be moved into each other's housings.
 *
 * @param first the serial that sorts first
 * @param second the other serial
 */
public record Crossing(String first, String second) {}

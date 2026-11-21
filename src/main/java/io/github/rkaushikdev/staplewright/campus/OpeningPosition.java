package io.github.rkaushikdev.staplewright.campus;

/**
 * One position on one opening, where a cylinder goes.
 *
 * @param opening the opening number
 * @param position where on it
 */
public record OpeningPosition(String opening, Position position) {}

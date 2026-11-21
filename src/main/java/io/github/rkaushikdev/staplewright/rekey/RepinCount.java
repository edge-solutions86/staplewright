package io.github.rkaushikdev.staplewright.rekey;

/**
 * How many times one cylinder has come apart.
 *
 * @param serial the cylinder
 * @param repins the events on record for it
 */
public record RepinCount(String serial, int repins) {}

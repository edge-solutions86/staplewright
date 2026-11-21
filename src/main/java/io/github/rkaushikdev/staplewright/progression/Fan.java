package io.github.rkaushikdev.staplewright.progression;

/**
 * One master and how many keys hang directly off it.
 *
 * @param master the master's symbol, as the shop writes it; empty text when there is no master
 * @param count how many keys sit directly under it
 */
public record Fan(String master, int count) {}

package io.github.rkaushikdev.staplewright.control;

/**
 * One tag somebody actually held up at an audit.
 *
 * @param tag the number read off the key
 * @param keyholder who was holding it
 */
public record Sighting(String tag, String keyholder) {}

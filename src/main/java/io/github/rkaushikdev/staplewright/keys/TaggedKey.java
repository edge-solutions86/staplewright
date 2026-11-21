package io.github.rkaushikdev.staplewright.keys;

/**
 * A key waiting at the stamping machine: its tag number and the symbol it is cut to.
 *
 * @param tag the tag number
 * @param symbol the symbol the key is cut to
 */
public record TaggedKey(String tag, KeySymbol symbol) {}

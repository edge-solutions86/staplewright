package io.github.rkaushikdev.staplewright.keys;

/**
 * One line of the blind code book: the number on the brass and the symbol it stands for.
 *
 * @param code the number stamped on the key
 * @param symbol the symbol it stands for, as the shop writes it
 */
public record BlindCode(String code, String symbol) {}

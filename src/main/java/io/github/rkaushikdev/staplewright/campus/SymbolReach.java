package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;

/**
 * How far one key reaches, counted in installed cylinders it turns.
 *
 * @param symbol the key
 * @param cylinders how many installed cylinders sit in groups that list it
 */
public record SymbolReach(KeySymbol symbol, int cylinders) {}

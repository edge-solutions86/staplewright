package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.InputError;

/**
 * What this shop stamps, and on what.
 *
 * <p>Masters going blind is the rule worth having. A change key with its symbol on it tells a
 * finder which office it opens, which is bad enough. A grand master with its symbol on it tells
 * them what they are holding.
 *
 * @param changeKeys what goes on a change key
 * @param masters what goes on anything that masters
 * @param addDoNotDuplicate whether every key also carries the do not duplicate mark
 * @param bowCharacters how many characters fit on a bow
 */
public record StampPolicy(Marking changeKeys, Marking masters, boolean addDoNotDuplicate, int bowCharacters) {

    /** About how many characters a key bow has room for. */
    public static final int BOW_CHARACTERS = 8;

    public StampPolicy {
        if (bowCharacters < 4) {
            throw new InputError("nothing useful fits on a bow that small");
        }
    }

    /** Symbols on change keys, blind codes on masters, do not duplicate on everything. */
    public StampPolicy() {
        this(Marking.SYMBOL, Marking.BLIND, true, BOW_CHARACTERS);
    }

    public StampPolicy withChangeKeys(Marking value) {
        return new StampPolicy(value, masters, addDoNotDuplicate, bowCharacters);
    }

    public StampPolicy withMasters(Marking value) {
        return new StampPolicy(changeKeys, value, addDoNotDuplicate, bowCharacters);
    }

    public StampPolicy withAddDoNotDuplicate(boolean value) {
        return new StampPolicy(changeKeys, masters, value, bowCharacters);
    }

    public StampPolicy withBowCharacters(int value) {
        return new StampPolicy(changeKeys, masters, addDoNotDuplicate, value);
    }

    /** What goes on the brass of a key with this symbol. */
    public Marking markingFor(KeySymbol symbol) {
        return symbol.isChangeKey() ? changeKeys : masters;
    }
}

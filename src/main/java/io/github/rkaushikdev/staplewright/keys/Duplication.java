package io.github.rkaushikdev.staplewright.keys;

import java.util.List;

/** Which keys have to be cut on a restricted blank, which is the duplicate control that works. */
public final class Duplication {

    private Duplication() {}

    /** Whether this key has to be cut on a restricted blank. */
    public static boolean restrictedNeeded(DuplicationPolicy policy, KeySymbol symbol) {
        if (!policy.restrictedOnlyForMasters()) {
            return true;
        }
        return !symbol.isChangeKey();
    }

    /** Symbols that ought to be on a restricted blank, in the order given, for a stock check. */
    public static List<String> unstamped(List<KeySymbol> symbols, DuplicationPolicy policy) {
        return symbols.stream()
                .filter(symbol -> restrictedNeeded(policy, symbol))
                .map(KeySymbol::render)
                .toList();
    }
}

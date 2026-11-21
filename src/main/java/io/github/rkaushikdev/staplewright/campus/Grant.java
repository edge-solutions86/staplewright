package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.List;

/**
 * One symbol in a draft hierarchy and every opening a key cut to it would turn.
 *
 * @param symbol the key
 * @param openings the doors it would open, in number order
 * @param reason why the draft put it there: a grand master for a building, a master for a group, a
 *     change key
 */
public record Grant(KeySymbol symbol, List<String> openings, String reason) {

    public Grant {
        openings = List.copyOf(openings);
    }

    /** A grant with no reason written on it. */
    public Grant(KeySymbol symbol, List<String> openings) {
        this(symbol, openings, "");
    }

    /** How many doors the key would open. */
    public int size() {
        return openings.size();
    }
}

package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Literal;
import java.util.List;
import java.util.Optional;

/**
 * One master and the change keys it was given.
 *
 * @param symbol the master
 * @param keys the change keys handed to it, in array order
 * @param constantChamber the chamber held still for this master, under the rotating constant method
 * @param constantDepth the depth that chamber is held at, under the rotating constant method
 */
public record Allocation(
        KeySymbol symbol, List<Bitting> keys, Optional<Integer> constantChamber, Optional<Integer> constantDepth) {

    public Allocation {
        keys = List.copyOf(keys);
    }

    /** A run of keys handed out by the block method, with no chamber held still. */
    public Allocation(KeySymbol symbol, List<Bitting> keys) {
        this(symbol, keys, Optional.empty(), Optional.empty());
    }

    /** The keys a master gets when one chamber is held at one depth for it. */
    public Allocation(KeySymbol symbol, List<Bitting> keys, int constantChamber, int constantDepth) {
        this(symbol, keys, Optional.of(constantChamber), Optional.of(constantDepth));
    }

    /** How many keys the master was given. */
    public int size() {
        return keys.size();
    }

    /** One line: the master, its key count, and the chamber held still when there is one. */
    public String describe() {
        if (constantChamber.isEmpty()) {
            return symbol.render() + ": " + size() + " keys";
        }
        return symbol.render() + ": " + size() + " keys, chamber " + constantChamber.get() + " held at "
                + Literal.plain(constantDepth.orElse(null));
    }
}

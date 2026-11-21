package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.HashSet;
import java.util.List;

/**
 * The symbols one cylinder answers to, and what to call it.
 *
 * <p>A door is not keyed to a symbol. It is keyed to a list of them: the change key for the room,
 * the master for the building, the grand master, and now and then a key from the far side of the
 * system because a lab is shared. That list is a keying group, and it is the unit the shop actually
 * pins to.
 *
 * @param name what the shop calls the group
 * @param symbols the keys the cylinder takes, in the order they were given
 */
public record KeyingGroup(String name, List<KeySymbol> symbols) {

    /**
     * A group, refusing one with no keys or with a key listed twice.
     *
     * @throws ConflictError for an empty group or a repeated symbol
     */
    public KeyingGroup {
        symbols = List.copyOf(symbols);
        if (symbols.isEmpty()) {
            throw new ConflictError("a keying group holds at least one key", Facts.of("group", name));
        }
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new ConflictError("a symbol is in the group twice", Facts.of("group", name));
        }
    }

    /** How many keys the cylinder takes. */
    public int size() {
        return symbols.size();
    }

    /** The symbols in schedule order, for printing. */
    public List<KeySymbol> ordered() {
        return KeySymbol.sortSymbols(symbols);
    }

    public boolean holds(KeySymbol symbol) {
        return symbols.contains(symbol);
    }

    /** The lowest key that sits above everything in the group. */
    public KeySymbol topSymbol() {
        KeySymbol walk = symbols.get(0);
        for (KeySymbol symbol : symbols.subList(1, symbols.size())) {
            walk = KeySymbol.commonAncestor(walk, symbol);
        }
        return walk;
    }

    /**
     * Whether the group holds keys the hierarchy does not relate.
     *
     * <p>A group is not cross keyed when every symbol in it sits on one line of descent, master above
     * change key above nothing. The moment two symbols sit on different branches the cylinder is
     * doing something the schedule alone does not explain.
     */
    public boolean isCrossKeyed() {
        List<KeySymbol> line = ordered();
        for (int index = 1; index < line.size(); index++) {
            if (!line.get(index).descendsFrom(line.get(index - 1))) {
                return true;
            }
        }
        return false;
    }
}

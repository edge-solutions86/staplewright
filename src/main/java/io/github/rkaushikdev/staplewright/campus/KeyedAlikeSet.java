package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.HashSet;
import java.util.List;

/**
 * A run of openings that carry one key between them.
 *
 * <p>A department with four store cupboards does not want four keys, so the four cylinders get the
 * same cuts and the set becomes one thing. That is cheap and convenient and it has one property
 * people forget: the set is now atomic. Lose the key to one cupboard and all four are compromised,
 * and rekeying one means rekeying all four on the same afternoon. So a set is a first class object
 * rather than a coincidence of bittings.
 *
 * @param name what the department calls the set
 * @param openings the doors in it, in the order they were given, at least two and each once
 * @param note anything else worth knowing
 */
public record KeyedAlikeSet(String name, List<String> openings, String note) {

    /**
     * A set, refusing one with no name, fewer than two doors, or a door listed twice.
     *
     * @throws InputError for a blank name
     * @throws ConflictError for a set of one door or a repeated door
     */
    public KeyedAlikeSet {
        openings = List.copyOf(openings);
        if (Text.isBlank(name)) {
            throw new InputError("a keyed alike set needs a name");
        }
        if (openings.size() < 2) {
            throw new ConflictError("a set of one door is just a door", Facts.of("name", name));
        }
        if (new HashSet<>(openings).size() != openings.size()) {
            throw new ConflictError("a door is in the set twice", Facts.of("name", name));
        }
    }

    /** A set with nothing else written about it. */
    public KeyedAlikeSet(String name, List<String> openings) {
        this(name, openings, "");
    }

    /** How many doors share the key. */
    public int size() {
        return openings.size();
    }

    /** Whether one door is in the set. */
    public boolean holds(String opening) {
        return openings.contains(opening);
    }

    /** One line for the register: {@code moore cupboards: MOO-214, MOO-216}. */
    public String describe() {
        return name + ": " + String.join(", ", openings.stream().sorted().toList());
    }
}

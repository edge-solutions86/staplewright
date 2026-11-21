package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Registry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every keyed alike set the shop maintains, and which door is in which.
 *
 * <p>A door belongs to one set at most, because a door in two sets would have to take two keys and
 * then neither set means what it says. The register walks its sets in name order.
 */
public final class KeyedAlikeRegister implements Iterable<KeyedAlikeSet> {

    private final Registry<KeyedAlikeSet> sets = new Registry<>("keyed alike set", "name");

    /**
     * Put a set on the register.
     *
     * @throws ConflictError when one of its doors is already in another set, naming that set, or
     *     the name is taken
     */
    public KeyedAlikeSet add(KeyedAlikeSet alike) {
        for (String number : alike.openings()) {
            Optional<KeyedAlikeSet> held = setHolding(number);
            if (held.isPresent()) {
                throw new ConflictError(
                        "that door is already in a keyed alike set",
                        Facts.of("opening", number, "held_by", held.get().name()));
            }
        }
        return sets.add(alike.name(), alike);
    }

    /**
     * The set by this name.
     *
     * @throws NotFoundError when there is none
     */
    public KeyedAlikeSet get(String name) {
        return sets.get(name);
    }

    /**
     * Take a set off the register. Its doors go back to being doors.
     *
     * @throws NotFoundError when there is none
     */
    public KeyedAlikeSet remove(String name) {
        return sets.remove(name);
    }

    /** The set one door belongs to, when it belongs to one. */
    public Optional<KeyedAlikeSet> setHolding(String opening) {
        return sets.first(alike -> alike.holds(opening));
    }

    /** Two sets become one under a new name, which is what happens when a wall comes out. */
    public KeyedAlikeSet merge(String first, String second, String name) {
        KeyedAlikeSet left = sets.get(first);
        KeyedAlikeSet right = sets.get(second);
        List<String> doors = new ArrayList<>(left.openings());
        doors.addAll(right.openings());
        KeyedAlikeSet joined = new KeyedAlikeSet(name, doors);
        sets.remove(first);
        sets.remove(second);
        return sets.add(name, joined);
    }

    /**
     * Pull some doors out of a set into their own.
     *
     * <p>Both halves have to survive as sets, so a split that would leave one door on its own is
     * refused rather than quietly deleting a set. The doors taken form the new set in number order;
     * the doors left keep their order and the old set's note.
     *
     * @throws NotFoundError when a door named is not in the set, listing those doors
     * @throws ConflictError when either half would hold fewer than two doors
     */
    public KeyedAlikeSet split(String name, Collection<String> taken, String into) {
        KeyedAlikeSet alike = sets.get(name);
        Set<String> wanted = new LinkedHashSet<>(taken);
        List<String> missing =
                wanted.stream().filter(number -> !alike.holds(number)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new NotFoundError("those doors are not in that set", Facts.of("name", name, "missing", missing));
        }
        List<String> left = alike.openings().stream()
                .filter(number -> !wanted.contains(number))
                .toList();
        if (left.size() < 2 || wanted.size() < 2) {
            throw new ConflictError("a split leaves two sets or it is not a split", Facts.of("name", name));
        }
        sets.put(name, new KeyedAlikeSet(name, left, alike.note()));
        return sets.add(into, new KeyedAlikeSet(into, wanted.stream().sorted().toList()));
    }

    /** Every set, in name order. */
    public List<KeyedAlikeSet> sets() {
        return sets.byKey();
    }

    /** The other doors that go up with this one when it is rekeyed, in number order. */
    public List<String> openingsAlikeWith(String opening) {
        return setHolding(opening)
                .map(held -> held.openings().stream()
                        .filter(number -> !number.equals(opening))
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    /** Whether a set by this name is on the register. */
    public boolean contains(Object name) {
        return sets.contains(name);
    }

    /** Every set, in name order. */
    @Override
    public Iterator<KeyedAlikeSet> iterator() {
        return sets().iterator();
    }

    /** How many sets the shop maintains. */
    public int size() {
        return sets.size();
    }
}

package io.github.rkaushikdev.staplewright.progression;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a key system looks like, measured rather than judged.
 *
 * @param symbols how many symbols were measured
 * @param depth the deepest rank anything sits on
 * @param levels every rank that holds something, top first
 * @param fanOut how many keys hang directly off each master, by symbol, in the order the masters
 *     were first met; a master with nothing under it is not listed
 */
public record Shape(int symbols, int depth, List<Level> levels, Map<String, Integer> fanOut) {

    public Shape {
        levels = List.copyOf(levels);
        fanOut = Collections.unmodifiableMap(new LinkedHashMap<>(fanOut));
    }

    /**
     * The master with the most hanging off it, which is the one to watch.
     *
     * <p>On a tie the master whose symbol sorts last is named. A system with nothing under anything
     * has no widest master, which comes back as empty text and nought.
     */
    public Fan widest() {
        return fanOut.entrySet().stream()
                .map(entry -> new Fan(entry.getKey(), entry.getValue()))
                .max(Comparator.comparingInt(Fan::count).thenComparing(Fan::master))
                .orElse(new Fan("", 0));
    }

    /** The mean number of keys under a master that has any. */
    public double averageFanOut() {
        if (fanOut.isEmpty()) {
            return 0.0;
        }
        return fanOut.values().stream().mapToInt(Integer::intValue).sum() / (double) fanOut.size();
    }

    /** The middle number of keys under a master, the mean of the two middle ones for an even count. */
    public double medianFanOut() {
        if (fanOut.isEmpty()) {
            return 0.0;
        }
        List<Integer> counts = fanOut.values().stream().sorted().toList();
        int middle = counts.size() / 2;
        if (counts.size() % 2 == 1) {
            return counts.get(middle);
        }
        return (counts.get(middle - 1) + counts.get(middle)) / 2.0;
    }

    /**
     * One branch carrying most of the system, which is how they grow.
     *
     * <p>A tree is lopsided when its widest master carries more than three times the middle one.
     * That is not a rule from anywhere, it is the point at which the shop stops being able to reason
     * about the system by looking at a chart of it. A tree with fewer than three masters is never
     * lopsided.
     */
    public boolean isLopsided() {
        if (fanOut.size() < 3) {
            return false;
        }
        double middle = medianFanOut();
        return middle > 0 && widest().count() > middle * 3;
    }

    /** One line: how many symbols, how deep, and the widest fan out. */
    public String describe() {
        return symbols + " symbols, " + depth + " deep, widest master " + widest().count();
    }
}

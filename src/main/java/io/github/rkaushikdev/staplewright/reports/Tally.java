package io.github.rkaushikdev.staplewright.reports;

import java.util.Comparator;
import java.util.List;

/**
 * One name and how many of something it has, the row of every "worst first" list a report prints:
 * who holds the most keys, which door generates the most calls, which building has the most doors
 * blocking a rekey.
 *
 * @param name the keyholder, symbol, room or building being counted
 * @param count how many it comes to
 */
public record Tally(String name, int count) {

    /** The biggest count first, a tie going to the smaller name. */
    static final Comparator<Tally> MOST_FIRST =
            Comparator.comparingInt(Tally::count).reversed().thenComparing(Tally::name);

    /**
     * The first {@code limit} of {@code found}. A negative limit leaves that many off the end, so
     * minus one is everything but the last.
     */
    static List<Tally> firstOf(List<Tally> found, int limit) {
        int end = limit < 0 ? Math.max(found.size() + limit, 0) : Math.min(limit, found.size());
        return List.copyOf(found.subList(0, end));
    }
}

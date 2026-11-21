package io.github.rkaushikdev.staplewright.campus;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Counts the way a crew sheet prints them: the biggest first, a tie going to the smaller name. */
final class Tallies {

    private Tallies() {}

    /** How many of {@code items} fall under each name, the biggest count first. */
    static <T> Map<String, Integer> count(Collection<T> items, Function<? super T, String> name) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (T item : items) {
            counts.merge(name.apply(item), 1, Integer::sum);
        }
        return biggestFirst(counts);
    }

    /** The same counts, read only, the biggest first and a tie in name order. */
    static Map<String, Integer> biggestFirst(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }
}

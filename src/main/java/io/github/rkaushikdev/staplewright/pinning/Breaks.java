package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/** The steps every column takes before it knows how many shear lines it has. */
final class Breaks {

    private Breaks() {}

    /** The depths a chamber breaks at, each once, shallow to deep. */
    static List<Integer> ordered(Collection<Integer> depths) {
        return List.copyOf(new TreeSet<>(depths));
    }

    /** The same, refusing a chamber that breaks nowhere. */
    static List<Integer> ordered(Collection<Integer> depths, int chamber) {
        List<Integer> ordered = ordered(depths);
        if (ordered.isEmpty()) {
            throw new PinningError("a chamber breaks somewhere", Facts.of("chamber", chamber));
        }
        return ordered;
    }

    /** The wafers between these breaks, a refusal naming the chamber it happened in. */
    static List<Integer> wafers(PinningSpec spec, int chamber, List<Integer> ordered) {
        try {
            return spec.wafersBetween(ordered);
        } catch (PinningError thin) {
            throw new PinningError(thin.message(), Facts.merged(Facts.of("chamber", chamber), thin.facts()));
        }
    }

    /** The total thickness of a run of wafers. */
    static int sum(List<Integer> wafers) {
        return wafers.stream().mapToInt(Integer::intValue).sum();
    }
}

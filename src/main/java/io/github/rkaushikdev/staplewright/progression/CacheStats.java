package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.util.Numbers;

/**
 * How the array cache is doing, for the health page.
 *
 * @param hits looks that found an array already built
 * @param misses looks that had to build one
 * @param evictions arrays pushed out to make room
 */
public record CacheStats(int hits, int misses, int evictions) {

    /** A cache nobody has used yet. */
    public CacheStats() {
        this(0, 0, 0);
    }

    /** Every look, found or built. */
    public int lookups() {
        return hits + misses;
    }

    /** The share of looks that found something, to three places; nought before the first look. */
    public double hitRate() {
        if (lookups() == 0) {
            return 0.0;
        }
        return Numbers.round(hits / (double) lookups(), 3);
    }

    /** One line for the health page. */
    public String describe() {
        return hits + " hits, " + misses + " misses, " + evictions + " evicted, rate " + Numbers.repr(hitRate());
    }
}

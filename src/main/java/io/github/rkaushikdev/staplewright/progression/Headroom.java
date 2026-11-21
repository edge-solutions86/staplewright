package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.util.Numbers;

/**
 * What is left in an array, and how long that lasts.
 *
 * @param capacity what the plan promised before the rules
 * @param cuttable what the rules left
 * @param used how many of those are already cut
 * @param reserveWanted how many the shop keeps back for surprises
 */
public record Headroom(int capacity, int cuttable, int used, int reserveWanted) {

    /** Cuttable keys nobody has yet, never below nought. */
    public int free() {
        return Math.max(cuttable - used, 0);
    }

    /** What is free beyond the reserve; below nought once the reserve is being eaten. */
    public int reserveLeft() {
        return free() - reserveWanted;
    }

    public boolean underReserve() {
        return reserveLeft() < 0;
    }

    /** The share of the cuttable keys already cut, to three places; all of it when nothing is cuttable. */
    public double usedFraction() {
        if (cuttable == 0) {
            return 1.0;
        }
        return Numbers.round(used / (double) cuttable, 3);
    }

    /** One line for the capacity page. */
    public String describe() {
        return used + " of " + cuttable + " cut, " + free() + " free, reserve " + reserveWanted;
    }
}

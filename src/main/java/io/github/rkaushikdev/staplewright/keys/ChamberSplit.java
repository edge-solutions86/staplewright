package io.github.rkaushikdev.staplewright.keys;

/**
 * One chamber where two keys disagree, and by how much.
 *
 * @param chamber the chamber, counted from one at the bow
 * @param above the depth the key higher in the hierarchy cuts there
 * @param below the depth the key under it cuts there
 */
public record ChamberSplit(int chamber, int above, int below) {

    /** How many depth steps apart the two cuts are, which is the wafer the chamber needs. */
    public int step() {
        return Math.abs(above - below);
    }

    /** Whether the key underneath is cut deeper than the one above it. */
    public boolean belowIsDeeper() {
        return below > above;
    }
}

package io.github.rkaushikdev.staplewright.keys;

/**
 * One line the code machine operator works through, in order from the shoulder.
 *
 * @param chamber the chamber, counted from one
 * @param depth the depth number off the card
 * @param spaceMils where the carriage goes, in mils from the gauge
 * @param depthMils where the cutter comes down to, in mils
 */
public record CutInstruction(int chamber, int depth, int spaceMils, int depthMils) {

    /** The line as the card prints it. */
    public String render() {
        return chamber + "  depth " + depth + "  at " + spaceMils + "  to " + depthMils;
    }
}

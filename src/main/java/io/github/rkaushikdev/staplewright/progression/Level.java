package io.github.rkaushikdev.staplewright.progression;

/**
 * One rank of a key system's tree, and how much is sitting on it.
 *
 * @param depth how far below the top key the rank sits; the top key is on rank nought
 * @param keys how many symbols sit on it
 * @param masters how many of those are masters rather than change keys
 */
public record Level(int depth, int keys, int masters) {

    public int changeKeys() {
        return keys - masters;
    }

    /** The rank as one line of the level table. */
    public String describe() {
        return "level " + depth + ": " + keys + " keys, " + masters + " of them masters";
    }
}

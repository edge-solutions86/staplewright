package io.github.rkaushikdev.staplewright.progression;

/**
 * What one keying group costs to build and what it gives away.
 *
 * @param group the group's name
 * @param keys how many keys the cylinder is pinned for
 * @param wafers how many master wafers the cylinder carries, across every chamber
 * @param operating how many keys turn the plug, drawn or not
 * @param incidental how many of those nobody drew
 */
public record GroupCost(String group, int keys, int wafers, int operating, int incidental) {

    /** Whether the cylinder takes its keys and nothing else. */
    public boolean isClean() {
        return incidental == 0;
    }
}

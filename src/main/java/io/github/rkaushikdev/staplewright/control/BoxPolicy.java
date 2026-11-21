package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;

/**
 * What the shop will put in a box on a wall.
 *
 * <p>{@code lowestLevel} is counted the way a symbol reads: the top key is nought and every master
 * below it is one more. A box may hold a symbol no higher up the tree than this, so a policy of
 * three refuses a building master and allows the change key for the entrance the box is bolted
 * beside.
 *
 * @param lowestLevel the highest a boxed key may sit, as a level
 * @param checkDays how often a box is looked at
 * @param resealDays how long a broken seal may stay broken
 */
public record BoxPolicy(int lowestLevel, int checkDays, int resealDays) {

    /** How often a box gets looked at, in days, unless the shop says otherwise. */
    public static final int CHECK_DAYS = 180;

    public BoxPolicy {
        if (lowestLevel < 1) {
            throw new InputError("a box holds a key at some level");
        }
        if (checkDays < 1) {
            throw new InputError("a box gets checked some day");
        }
        if (resealDays < 0) {
            throw new InputError("a reseal window does not run backwards");
        }
    }

    /** Level three and below, checked twice a year, resealed inside two days. */
    public BoxPolicy() {
        this(3, CHECK_DAYS, 2);
    }

    public boolean allows(KeySymbol symbol) {
        return symbol.level() >= lowestLevel;
    }

    public BoxPolicy withLowestLevel(int value) {
        return new BoxPolicy(value, checkDays, resealDays);
    }

    public BoxPolicy withCheckDays(int value) {
        return new BoxPolicy(lowestLevel, value, resealDays);
    }

    public BoxPolicy withResealDays(int value) {
        return new BoxPolicy(lowestLevel, checkDays, value);
    }
}

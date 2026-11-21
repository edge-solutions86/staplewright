package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.InputError;

/**
 * How many of each kind of key the shop will have out at once.
 *
 * <p>Stamping a key do not duplicate achieves nothing on its own. Any hardware store will cut it,
 * the stamp is not a legal instruction, and the shop has a drawer full of copies to prove it. What
 * works is the restricted keyway, where the blank cannot be bought, and the count, where the shop
 * knows five keys were cut to AA1 and finds six.
 *
 * @param changeKeyLimit how many of one change key may be out
 * @param masterLimit how many of one master may be out
 * @param topKeyLimit how many of the top key may be out
 * @param restrictedOnlyForMasters whether only masters have to be cut on a restricted blank
 */
public record DuplicationPolicy(
        int changeKeyLimit, int masterLimit, int topKeyLimit, boolean restrictedOnlyForMasters) {

    /** Four of a change key, two of a master, one top key, restricted blanks for masters only. */
    public static final DuplicationPolicy DEFAULT = new DuplicationPolicy();

    public DuplicationPolicy {
        if (changeKeyLimit < 1 || masterLimit < 1 || topKeyLimit < 1) {
            throw new InputError("a limit of nothing means no keys at all");
        }
    }

    /** The shop's standing ceilings. */
    public DuplicationPolicy() {
        this(4, 2, 1, true);
    }

    public DuplicationPolicy withChangeKeyLimit(int value) {
        return new DuplicationPolicy(value, masterLimit, topKeyLimit, restrictedOnlyForMasters);
    }

    public DuplicationPolicy withMasterLimit(int value) {
        return new DuplicationPolicy(changeKeyLimit, value, topKeyLimit, restrictedOnlyForMasters);
    }

    public DuplicationPolicy withTopKeyLimit(int value) {
        return new DuplicationPolicy(changeKeyLimit, masterLimit, value, restrictedOnlyForMasters);
    }

    public DuplicationPolicy withRestrictedOnlyForMasters(boolean value) {
        return new DuplicationPolicy(changeKeyLimit, masterLimit, topKeyLimit, value);
    }

    /** How many keys cut to this symbol policy allows out at once. */
    public int ceilingFor(KeySymbol symbol) {
        if (symbol.isTop()) {
            return topKeyLimit;
        }
        if (symbol.isChangeKey()) {
            return changeKeyLimit;
        }
        return masterLimit;
    }
}

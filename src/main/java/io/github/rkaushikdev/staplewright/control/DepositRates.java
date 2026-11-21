package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.InputError;

/**
 * What a deposit costs, by what the key opens, in whole cents.
 *
 * <p>Policy rather than arithmetic, and it lives here so the counter and the settings can both
 * reach it without either of them depending on the other.
 *
 * @param changeKeyCents the deposit on a change key
 * @param masterCents the deposit on anything that masters
 */
public record DepositRates(int changeKeyCents, int masterCents) {

    public DepositRates {
        if (changeKeyCents < 0 || masterCents < 0) {
            throw new InputError("a deposit is not negative");
        }
    }

    /** Twenty five dollars a change key, fifty for anything that masters. */
    public DepositRates() {
        this(2500, 5000);
    }

    /** The deposit on one key. */
    public int forKey(boolean isMaster) {
        return isMaster ? masterCents : changeKeyCents;
    }
}

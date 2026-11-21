package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.InputError;

/**
 * How long a claimed handover stands before the shop stops believing it.
 *
 * @param confirmDays how long the receiving keyholder has to confirm
 * @param allowMasterTransfer whether a master may change hands without coming back to the counter
 */
public record TransferPolicy(int confirmDays, boolean allowMasterTransfer) {

    /** How long a claim stands, in days, unless the shop says otherwise. */
    public static final int CONFIRM_DAYS = 14;

    public TransferPolicy {
        if (confirmDays < 1) {
            throw new InputError("a claim stands for at least a day");
        }
    }

    /** Two weeks, and masters come back to the counter. */
    public TransferPolicy() {
        this(CONFIRM_DAYS, false);
    }

    public TransferPolicy withConfirmDays(int value) {
        return new TransferPolicy(value, allowMasterTransfer);
    }

    public TransferPolicy withAllowMasterTransfer(boolean value) {
        return new TransferPolicy(confirmDays, value);
    }
}

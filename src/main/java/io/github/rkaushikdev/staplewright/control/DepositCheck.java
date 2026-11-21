package io.github.rkaushikdev.staplewright.control;

import java.util.List;

/**
 * Whether the money and the metal agree.
 *
 * @param ledgerCents the deposits on every key the ledger says is out
 * @param bookCents what the deposit book says the shop is holding
 * @param tagsWithoutDeposits keys out with a deposit on them and no open charge, in tag order
 * @param depositsWithoutKeys open charges with no key out to answer for them, in tag order
 */
public record DepositCheck(
        long ledgerCents, long bookCents, List<String> tagsWithoutDeposits, List<String> depositsWithoutKeys) {

    public DepositCheck {
        tagsWithoutDeposits = List.copyOf(tagsWithoutDeposits);
        depositsWithoutKeys = List.copyOf(depositsWithoutKeys);
    }

    /** How far the ledger runs ahead of the book, in cents. */
    public long difference() {
        return ledgerCents - bookCents;
    }

    /** Whether the totals agree and neither side has a tag the other is missing. */
    public boolean balances() {
        return difference() == 0 && tagsWithoutDeposits.isEmpty() && depositsWithoutKeys.isEmpty();
    }
}

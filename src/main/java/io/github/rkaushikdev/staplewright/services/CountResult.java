package io.github.rkaushikdev.staplewright.services;

/**
 * What a physical count found, against what the ledger thought.
 *
 * @param partNumber the part that was counted
 * @param expected what the drawer should have held
 * @param counted what was actually in it
 */
public record CountResult(String partNumber, int expected, int counted) {

    /** How far the drawer was from the ledger: positive over, negative short. */
    public int difference() {
        return counted - expected;
    }

    /** Whether the drawer held exactly what the ledger said. */
    public boolean agrees() {
        return difference() == 0;
    }

    /** One line for the count: {@code 1145A counted 188, 12 short}, or that it agrees. */
    public String describe() {
        if (agrees()) {
            return partNumber + " counted " + counted + ", agrees";
        }
        String way = difference() > 0 ? "over" : "short";
        return partNumber + " counted " + counted + ", " + Math.abs(difference()) + " " + way;
    }
}

package io.github.rkaushikdev.staplewright.reports;

/**
 * One part number over a period, opening to closing, the way a restricted keyway undertaking asks
 * for it.
 *
 * @param partNumber the blank's part number
 * @param opening what was in the drawer when the period began
 * @param received what came in on orders
 * @param returned what came back unused
 * @param cut what was cut into keys
 * @param scrapped what was spoiled and thrown away
 * @param counted what the stocktakes in the period found in the drawer, added up
 * @param closing what was in the drawer when the period ended
 */
public record Reconciliation(
        String partNumber, int opening, int received, int returned, int cut, int scrapped, int counted, int closing) {

    /** What the movements say should be there, before any count. */
    public int expected() {
        return opening + received + returned - cut - scrapped;
    }

    /**
     * What went missing, which is the number the factory asks about.
     *
     * <p>This is the sum of what the stocktakes moved. Every other movement is something somebody
     * chose to do and can point at a reason for, so the only brass that is genuinely unaccounted for
     * is the brass a count had to correct.
     */
    public int unaccounted() {
        return closing - expected();
    }

    /** Whether the part accounts for every blank. */
    public boolean agrees() {
        return unaccounted() == 0;
    }

    /** One line: {@code 1145A accounts for every blank}, {@code 1145A 12 unaccounted}, {@code 1145A 3 over}. */
    public String describe() {
        if (agrees()) {
            return partNumber + " accounts for every blank";
        }
        String way = unaccounted() > 0 ? "over" : "unaccounted";
        return partNumber + " " + Math.abs(unaccounted()) + " " + way;
    }
}

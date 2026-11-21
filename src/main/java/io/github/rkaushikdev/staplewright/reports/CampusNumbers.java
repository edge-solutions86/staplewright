package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.control.DepositBook;
import java.util.List;

/**
 * One year of the shop, in the figures that get asked for at a budget meeting.
 *
 * @param buildings the buildings on the campus
 * @param openings every opening on the schedule
 * @param keyedOpenings the openings whose lockset takes a cylinder
 * @param cylindersInstalled the cylinders sitting in a door
 * @param cylindersShelved the cylinders the shop owns that are not in a door
 * @param keysOut the keys out today
 * @param keysGone the keys written off as lost or stolen
 * @param depositsHeldCents the deposits the shop is holding, in cents
 */
public record CampusNumbers(
        int buildings,
        int openings,
        int keyedOpenings,
        int cylindersInstalled,
        int cylindersShelved,
        int keysOut,
        int keysGone,
        long depositsHeldCents) {

    /** Keyed openings with no cylinder to account for them. */
    public int uncovered() {
        return keyedOpenings - cylindersInstalled;
    }

    /** The figures as four printed lines. */
    public List<String> lines() {
        return List.of(
                buildings + " buildings, " + openings + " openings, " + keyedOpenings + " of them keyed",
                cylindersInstalled + " cylinders in doors, " + cylindersShelved + " on the shelf",
                keysOut + " keys out, " + keysGone + " written off",
                "deposits held " + DepositBook.dollars(depositsHeldCents));
    }
}

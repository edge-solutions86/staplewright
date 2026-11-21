package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;

/**
 * The four numbers of the annual key control return, for one year.
 *
 * <p>How many keys are out, how many of those are masters, how many the shop cannot account for,
 * and how many people are holding them.
 *
 * @param year the year the return is for
 * @param keysOut every key out on the day it was taken
 * @param mastersOut the keys out above change key level
 * @param unaccounted the keys nobody can produce: lost, or out past their due day
 * @param keyholders the people holding at least one key
 */
public record AnnualReturn(int year, int keysOut, int mastersOut, int unaccounted, int keyholders) {

    /**
     * A return, refusing a year before anybody living could have filed one.
     *
     * @throws InputError for a year before 1900
     */
    public AnnualReturn {
        if (year < 1900) {
            throw new InputError("a return is for a year somebody was alive", Facts.of("year", year));
        }
    }

    /** The average number of keys each holder has, or nought when nobody holds anything. */
    public double keysPerHolder() {
        if (keyholders == 0) {
            return 0.0;
        }
        return keysOut / (double) keyholders;
    }

    /** One line for the file: {@code 2025: 5 keys out, 3 masters, 0 unaccounted}. */
    public String describe() {
        return year + ": " + keysOut + " keys out, " + mastersOut + " masters, " + unaccounted + " unaccounted";
    }
}

package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * One section, and where it stands against a date.
 *
 * <p>A restricted section stays restricted until its patent runs out, and a section restricted by
 * contract alone carries no date and never goes open on its own. An unrestricted section is open
 * whatever the date says.
 *
 * @param section the section's code
 * @param restricted whether blanks for it are sold to the shop only
 * @param expiresOn the day its patent runs out, for a section that carries one
 */
public record Exposure(String section, boolean restricted, Optional<LocalDate> expiresOn) {

    /** How many days until the patent runs out, negative once it has. Empty with no date. */
    public Optional<Integer> daysLeft(LocalDate today) {
        return expiresOn.map(day -> Math.toIntExact(ChronoUnit.DAYS.between(today, day)));
    }

    /** The patent has run, or there never was one, so anybody can buy the blank. */
    public boolean isOpen(LocalDate today) {
        if (!restricted) {
            return true;
        }
        return expiresOn.isPresent() && !expiresOn.get().isAfter(today);
    }

    /** It will be open before a project started today finishes, over the usual planning horizon. */
    public boolean isClosing(LocalDate today) {
        return isClosing(today, Patents.PLANNING_YEARS);
    }

    /**
     * It will be open before a project started today and running this many years finishes.
     *
     * @throws InputError for a horizon that runs backwards
     */
    public boolean isClosing(LocalDate today, int withinYears) {
        if (isOpen(today) || expiresOn.isEmpty()) {
            return false;
        }
        return !expiresOn.get().isAfter(addYears(today, withinYears));
    }

    /** One line saying what kind of restriction the section has, as of today. */
    public String describe(LocalDate today) {
        if (!restricted) {
            return section + " open keyway";
        }
        if (expiresOn.isEmpty()) {
            return section + " restricted by contract only";
        }
        if (isOpen(today)) {
            return section + " patent ran out " + expiresOn.get();
        }
        return section + " restricted until " + expiresOn.get();
    }

    /** The same day some years on; a leap day lands on the last day of February. */
    private static LocalDate addYears(LocalDate day, int years) {
        if (years < 0) {
            throw new InputError("a planning horizon does not run backwards", Facts.of("years", years));
        }
        return day.plusYears(years);
    }
}

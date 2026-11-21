package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A stretch of days when rekey work can happen, and how much fits in it.
 *
 * @param name what the calendar calls it
 * @param kind what sort of gap it is
 * @param opensOn the first working day
 * @param closesOn the last working day, counted in
 * @param doorsPerDay how many doors a crew gets through in a day
 */
public record Window(String name, WindowKind kind, LocalDate opensOn, LocalDate closesOn, int doorsPerDay) {

    /** How many doors a day a window does unless told otherwise. */
    public static final int DOORS_PER_DAY = 12;

    /**
     * A window, refusing one with no name, one that closes before it opens, or one that does no work.
     *
     * @throws InputError for any of those
     */
    public Window {
        if (Text.isBlank(name)) {
            throw new InputError("a window needs a name");
        }
        if (closesOn.isBefore(opensOn)) {
            throw new InputError("a window closes after it opens", Facts.of("window", name));
        }
        if (doorsPerDay < 1) {
            throw new InputError("a window does at least a door a day", Facts.of("window", name));
        }
    }

    /** A window doing the standing twelve doors a day. */
    public Window(String name, WindowKind kind, LocalDate opensOn, LocalDate closesOn) {
        this(name, kind, opensOn, closesOn, DOORS_PER_DAY);
    }

    /** How many days it runs, both ends counted. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(opensOn, closesOn) + 1;
    }

    /** How many doors fit, which is the number everybody argues about. */
    public int capacity() {
        return days() * doorsPerDay;
    }

    /** Whether a day falls inside the window, both ends counted. */
    public boolean covers(LocalDate day) {
        return !day.isBefore(opensOn) && !day.isAfter(closesOn);
    }

    /** Whether two windows share a day. */
    public boolean overlaps(Window other) {
        return !opensOn.isAfter(other.closesOn) && !other.opensOn.isAfter(closesOn);
    }

    /**
     * Which day the door at this position in the window lands on, counting from nought.
     *
     * @throws InputError for a negative position
     */
    public LocalDate dayFor(int position) {
        if (position < 0) {
            throw new InputError("a position counts from nothing", Facts.of("window", name));
        }
        return opensOn.plusDays(position / doorsPerDay);
    }

    /** One line: {@code summer 2025-06-02 to 2025-06-06, 20 doors}. */
    public String describe() {
        return name + " " + opensOn + " to " + closesOn + ", " + capacity() + " doors";
    }
}

package io.github.rkaushikdev.staplewright.rekey;

import java.time.LocalDate;

/**
 * One door, in one window, on one day.
 *
 * @param door the door
 * @param window the name of the window it landed in
 * @param on the day it is done
 */
public record Placement(Door door, String window, LocalDate on) {

    /** One line: {@code MOO-101 summer 2025-06-02}. */
    public String describe() {
        return door.number() + " " + window + " " + on;
    }
}
